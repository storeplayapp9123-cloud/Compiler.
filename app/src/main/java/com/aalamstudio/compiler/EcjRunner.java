package com.aalamstudio.compiler;

import android.content.Context;

import org.eclipse.jdt.internal.compiler.batch.Main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public class EcjRunner {

    /** Returns a string starting with "OK" on success, "ERROR" on failure. */
    public static String compile(Context ctx, String projectDir) {
        try {
            File androidJar = new File(ctx.getFilesDir(), "android.jar");
            if (!androidJar.exists() || androidJar.length() == 0) {
                copyAsset(ctx, "android.jar", androidJar);
            }

            File src = new File(projectDir, "src");
            File out = new File(projectDir, "build/classes");
            out.mkdirs();

            List<String> javaFiles = new ArrayList<>();
            collect(src, javaFiles);
            collect(new File(projectDir, "build/gen"), javaFiles);
            if (javaFiles.isEmpty()) return "ERROR: no .java files in src/";

            List<String> args = new ArrayList<>();
            args.add("-source"); args.add("1.8");
            args.add("-target"); args.add("1.8");
            args.add("-encoding"); args.add("UTF-8");
            args.add("-nowarn");
            args.add("-proc:none");
            args.add("-bootclasspath"); args.add(androidJar.getAbsolutePath());

            StringBuilder cp = new StringBuilder();
            File[] jars = new File(projectDir, "libs").listFiles();
            if (jars != null) {
                for (File j : jars) {
                    if (j.getName().endsWith(".jar")) {
                        if (cp.length() > 0) cp.append(File.pathSeparator);
                        cp.append(j.getAbsolutePath());
                    }
                }
            }
            if (cp.length() > 0) { args.add("-classpath"); args.add(cp.toString()); }

            args.add("-d"); args.add(out.getAbsolutePath());
            args.addAll(javaFiles);

            final StringWriter outW = new StringWriter();
            final StringWriter errW = new StringWriter();
            final Main main = new Main(new PrintWriter(outW), new PrintWriter(errW), false);
            final String[] argv = args.toArray(new String[0]);
            final boolean[] ok = new boolean[1];
            final Throwable[] fail = new Throwable[1];

            // ECJ needs a big stack
            Thread t = new Thread(null, () -> {
                try {
                    ok[0] = main.compile(argv);
                } catch (Throwable e) {
                    fail[0] = e;
                }
            }, "ecj", 64L * 1024 * 1024);
            t.start();
            t.join();

            if (fail[0] != null) return "ERROR: ECJ crashed: " + fail[0];
            if (!ok[0]) return "ERROR: Java compile failed\n" + errW + outW;

            return "OK: " + countClasses(out) + " class files in build/classes";
        } catch (Throwable e) {
            return "ERROR: " + e;
        }
    }

    private static void collect(File dir, List<String> out) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) collect(k, out);
            else if (k.getName().endsWith(".java")) out.add(k.getAbsolutePath());
        }
    }

    private static int countClasses(File dir) {
        int n = 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        for (File k : kids) {
            if (k.isDirectory()) n += countClasses(k);
            else if (k.getName().endsWith(".class")) n++;
        }
        return n;
    }

    private static void copyAsset(Context ctx, String name, File dest) throws IOException {
        try (InputStream in = ctx.getAssets().open(name);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }
}
