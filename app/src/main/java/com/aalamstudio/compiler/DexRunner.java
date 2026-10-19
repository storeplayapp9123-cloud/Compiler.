package com.aalamstudio.compiler;

import android.content.Context;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DexRunner {

    /** Returns a string starting with "OK" on success, "ERROR" on failure. */
    public static String run(Context ctx, String projectDir) {
        try {
            File androidJar = new File(ctx.getFilesDir(), "android.jar");
            if (!androidJar.exists()) {
                return "ERROR: android.jar missing (resources step must run first)";
            }

            // ---- app ke classes ----
            final List<Path> programFiles = new ArrayList<>();
            collectClasses(new File(projectDir, "build/classes"), programFiles);
            if (programFiles.isEmpty()) return "ERROR: no .class files in build/classes";

            File[] jars = new File(projectDir, "libs").listFiles();
            if (jars != null) {
                for (File j : jars) {
                    if (j.getName().endsWith(".jar")) programFiles.add(j.toPath());
                }
            }

            // ---- AndroidX libraries: ek baar dex karke cache ----
            List<File> libJars = AndroidxLibs.classpathJars(ctx);
            File cache = new File(ctx.getFilesDir(), "androidx-dex");
            if (!libJars.isEmpty()) {
                String err = ensureLibDex(ctx, libJars, androidJar, cache);
                if (err != null) return err;
            }

            // ---- app ko dex karo ----
            final File outDir = new File(projectDir, "build/dex");
            deleteRecursive(outDir);
            outDir.mkdirs();

            List<Path> classpath = new ArrayList<>();
            for (File j : libJars) classpath.add(j.toPath());

            String err = runD8(programFiles, classpath, androidJar.toPath(),
                    outDir, CompilationMode.DEBUG);
            if (err != null) return err;

            File dex = new File(outDir, "classes.dex");
            if (!dex.exists()) return "ERROR: classes.dex was not created";

            // ---- library ki cached dex files saath me daalo ----
            int libDexCount = 0;
            if (!libJars.isEmpty()) {
                File[] appDex = outDir.listFiles((d, n) -> n.endsWith(".dex"));
                int next = (appDex == null ? 1 : appDex.length) + 1;

                File[] cached = cache.listFiles((d, n) -> n.endsWith(".dex"));
                if (cached != null) {
                    Arrays.sort(cached);
                    for (File c : cached) {
                        copy(c, new File(outDir, "classes" + next + ".dex"));
                        next++;
                        libDexCount++;
                    }
                }
            }

            return "OK: classes.dex created (" + dex.length() + " bytes)"
                    + (libDexCount > 0 ? " + " + libDexCount + " androidx dex" : "");
        } catch (Throwable e) {
            return "ERROR: " + e;
        }
    }

    /** Library jars ko ek baar dex karta hai. App update par cache khud naya banta hai. */
    private static String ensureLibDex(Context ctx, List<File> libJars, File androidJar,
                                       File cache) throws Exception {
        File stamp = new File(cache, ".stamp");
        String want = lastUpdate(ctx) + ":" + libJars.size();
        if (stamp.exists() && want.equals(readText(stamp)) && hasDex(cache)) return null;

        deleteRecursive(cache);
        cache.mkdirs();

        List<Path> program = new ArrayList<>();
        for (File j : libJars) program.add(j.toPath());

        String err = runD8(program, new ArrayList<Path>(), androidJar.toPath(),
                cache, CompilationMode.RELEASE);
        if (err != null) {
            deleteRecursive(cache);
            return "ERROR: androidx libraries ko dex nahi kar paye\n" + err;
        }
        if (!hasDex(cache)) {
            deleteRecursive(cache);
            return "ERROR: androidx dex files nahi bani";
        }
        writeText(stamp, want);
        return null;
    }

    /** D8 chalata hai. Success par null, fail par error text. */
    private static String runD8(final List<Path> program, final List<Path> classpath,
                                final Path library, final File outDir,
                                final CompilationMode mode) throws InterruptedException {
        final StringBuilder log = new StringBuilder();
        final DiagnosticsHandler handler = new DiagnosticsHandler() {
            @Override
            public void error(Diagnostic d) {
                log.append("error: ").append(d.getDiagnosticMessage()).append('\n');
            }

            @Override
            public void warning(Diagnostic d) { }

            @Override
            public void info(Diagnostic d) { }
        };

        final Throwable[] fail = new Throwable[1];

        // D8 needs a big stack
        Thread t = new Thread(null, () -> {
            try {
                D8Command.Builder b = D8Command.builder(handler)
                        .addProgramFiles(program)
                        .addLibraryFiles(library)
                        .setMinApiLevel(24)
                        .setMode(mode)
                        .setOutput(outDir.toPath(), OutputMode.DexIndexed);
                if (!classpath.isEmpty()) {
                    b.addClasspathFiles(classpath.toArray(new Path[0]));
                }
                D8.run(b.build());
            } catch (Throwable e) {
                fail[0] = e;
            }
        }, "d8", 64L * 1024 * 1024);
        t.start();
        t.join();

        if (fail[0] != null) {
            return "ERROR: D8 failed: " + fail[0] + "\n" + log;
        }
        return null;
    }

    private static boolean hasDex(File dir) {
        File[] f = dir.listFiles((d, n) -> n.endsWith(".dex"));
        return f != null && f.length > 0;
    }

    private static long lastUpdate(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).lastUpdateTime;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void collectClasses(File dir, List<Path> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collectClasses(k, out);
            else if (k.getName().endsWith(".class")) out.add(k.toPath());
        }
    }

    private static void copy(File from, File to) throws IOException {
        try (InputStream in = new FileInputStream(from);
             OutputStream out = new FileOutputStream(to)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    private static String readText(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            return bo.toString("UTF-8");
        }
    }

    private static void writeText(File f, String s) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(s.getBytes("UTF-8"));
        }
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
