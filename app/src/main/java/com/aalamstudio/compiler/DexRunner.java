package com.aalamstudio.compiler;

import android.content.Context;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DexRunner {

    /** Returns a string starting with "OK" on success, "ERROR" on failure. */
    public static String run(Context ctx, String projectDir) {
        try {
            File androidJar = new File(ctx.getFilesDir(), "android.jar");
            if (!androidJar.exists()) {
                return "ERROR: android.jar missing (resources step must run first)";
            }

            final List<Path> programFiles = new ArrayList<>();
            collectClasses(new File(projectDir, "build/classes"), programFiles);
            if (programFiles.isEmpty()) return "ERROR: no .class files in build/classes";

            File[] jars = new File(projectDir, "libs").listFiles();
            if (jars != null) {
                for (File j : jars) {
                    if (j.getName().endsWith(".jar")) programFiles.add(j.toPath());
                }
            }

            final File outDir = new File(projectDir, "build/dex");
            outDir.mkdirs();

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

            final Path jarPath = androidJar.toPath();
            final Throwable[] fail = new Throwable[1];

            // D8 needs a big stack
            Thread t = new Thread(null, () -> {
                try {
                    D8Command cmd = D8Command.builder(handler)
                            .addProgramFiles(programFiles)
                            .addLibraryFiles(jarPath)
                            .setMinApiLevel(24)
                            .setMode(CompilationMode.DEBUG)
                            .setOutput(outDir.toPath(), OutputMode.DexIndexed)
                            .build();
                    D8.run(cmd);
                } catch (Throwable e) {
                    fail[0] = e;
                }
            }, "d8", 64L * 1024 * 1024);
            t.start();
            t.join();

            if (fail[0] != null) {
                return "ERROR: D8 failed: " + fail[0] + "\n" + log;
            }

            File dex = new File(outDir, "classes.dex");
            if (!dex.exists()) return "ERROR: classes.dex was not created\n" + log;

            return "OK: classes.dex created (" + dex.length() + " bytes)";
        } catch (Throwable e) {
            return "ERROR: " + e;
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
}
