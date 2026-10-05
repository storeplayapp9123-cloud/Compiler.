package com.aalamstudio.compiler;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AaptRunner {

    /** Returns a string starting with "OK" on success, "ERROR" on failure. */
    public static String run(Context ctx, String projectDir) {
        try {
            File aapt2 = new File(ctx.getApplicationInfo().nativeLibraryDir, "libaapt2.so");
            if (!aapt2.exists()) {
                return "ERROR: libaapt2.so not found for this phone "
                        + Arrays.toString(Build.SUPPORTED_ABIS);
            }
            aapt2.setExecutable(true);

            File androidJar = new File(ctx.getFilesDir(), "android.jar");
            if (!androidJar.exists() || androidJar.length() == 0) {
                copyAsset(ctx, "android.jar", androidJar);
            }

            File manifest = new File(projectDir, "AndroidManifest.xml");
            if (!manifest.exists()) return "ERROR: AndroidManifest.xml missing in .asc";

            File build = new File(projectDir, "build");
            File gen = new File(build, "gen");
            gen.mkdirs();
            File resZip = new File(build, "res.zip");
            File baseApk = new File(build, "base.apk");
            File res = new File(projectDir, "res");
            File assets = new File(projectDir, "assets");

            boolean hasRes = hasSubDirs(res);
            if (hasRes) {
                String err = exec(Arrays.asList(aapt2.getAbsolutePath(), "compile",
                        "--dir", res.getAbsolutePath(),
                        "-o", resZip.getAbsolutePath()), build);
                if (err != null) return "ERROR: aapt2 compile failed\n" + err;
            }

            List<String> link = new ArrayList<>(Arrays.asList(
                    aapt2.getAbsolutePath(), "link",
                    "-o", baseApk.getAbsolutePath(),
                    "-I", androidJar.getAbsolutePath(),
                    "--manifest", manifest.getAbsolutePath(),
                    "--java", gen.getAbsolutePath(),
                    "--min-sdk-version", "24",
                    "--target-sdk-version", "34",
                    "--auto-add-overlay"));
            if (assets.isDirectory()) {
                link.add("-A");
                link.add(assets.getAbsolutePath());
            }
            if (hasRes) link.add(resZip.getAbsolutePath());

            String err = exec(link, build);
            if (err != null) return "ERROR: aapt2 link failed\n" + err;

            return "OK: base.apk created (" + baseApk.length() + " bytes)";
        } catch (Throwable e) {
            return "ERROR: " + e;
        }
    }

    private static boolean hasSubDirs(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return false;
        for (File k : kids) if (k.isDirectory()) return true;
        return false;
    }

    /** Runs a command. Returns null on success, or its output on failure. */
    private static String exec(List<String> cmd, File workDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) out.append(line).append('\n');
        }
        int code = p.waitFor();
        if (code == 0) return null;
        return out.length() > 0 ? out.toString() : "exit code " + code;
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
