package com.aalamstudio.compiler;

import android.content.Context;
import android.os.Build;
import android.system.Os;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AaptRunner {

    /** Returns a string starting with "OK" on success, "ERROR" on failure. */
    public static String run(Context ctx, String projectDir) {
        try {
            File nativeDir = new File(ctx.getApplicationInfo().nativeLibraryDir);
            File aapt2 = new File(nativeDir, "libaapt2.so");
            if (!aapt2.exists()) {
                return "ERROR: libaapt2.so not found for this phone "
                        + Arrays.toString(Build.SUPPORTED_ABIS);
            }
            aapt2.setExecutable(true);

            // Termux libs ask for names like libz.so.1 / libexpat.so.1.
            // Make symlinks from those names to the libs we actually have.
            File compat = new File(ctx.getFilesDir(), "aapt2-compat");
            compat.mkdirs();
            String missing = prepareLibs(nativeDir, compat, aapt2);
            if (!missing.isEmpty()) {
                return "ERROR: missing libraries (upload these to jniLibs):\n" + missing;
            }
            String libPath = compat.getAbsolutePath() + ":" + nativeDir.getAbsolutePath();

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

            // Does aapt2 even start?
            String v = exec(Arrays.asList(aapt2.getAbsolutePath(), "version"), build, libPath);
            if (v != null) return "ERROR: aapt2 will not start\n" + v;

            boolean hasRes = hasSubDirs(res);
            if (hasRes) {
                String err = exec(Arrays.asList(aapt2.getAbsolutePath(), "compile",
                        "--dir", res.getAbsolutePath(),
                        "-o", resZip.getAbsolutePath()), build, libPath);
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

            String err = exec(link, build, libPath);
            if (err != null) return "ERROR: aapt2 link failed\n" + err;

            return "OK: base.apk created (" + baseApk.length() + " bytes)";
        } catch (Throwable e) {
            return "ERROR: " + e;
        }
    }

    /** Makes symlinks for versioned lib names. Returns list of libs not found anywhere. */
    private static String prepareLibs(File nativeDir, File compat, File start) {
        String sysDir = Build.SUPPORTED_64_BIT_ABIS.length > 0 ? "/system/lib64" : "/system/lib";
        StringBuilder missing = new StringBuilder();
        Set<String> seen = new HashSet<>();
        Deque<File> queue = new ArrayDeque<>();
        queue.add(start);

        while (!queue.isEmpty()) {
            File f = queue.poll();
            for (String n : needed(f)) {
                if (!seen.add(n)) continue;

                File direct = new File(nativeDir, n);
                if (direct.exists()) {
                    queue.add(direct);
                    continue;
                }

                int i = n.indexOf(".so");
                String base = i > 0 ? n.substring(0, i + 3) : n;

                File viaBase = new File(nativeDir, base);
                if (!base.equals(n) && viaBase.exists()) {
                    symlink(compat, n, viaBase.getAbsolutePath());
                    queue.add(viaBase);
                    continue;
                }

                File sys = new File(sysDir, base);
                if (sys.exists()) {
                    if (!base.equals(n)) symlink(compat, n, sys.getAbsolutePath());
                    continue;
                }

                missing.append(n).append('\n');
            }
        }
        return missing.toString();
    }

    /** Reads the DT_NEEDED names of a 64-bit ELF file. */
    private static List<String> needed(File f) {
        List<String> out = new ArrayList<>();
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] h = new byte[64];
            r.readFully(h);
            if (h[0] != 0x7f || h[1] != 'E' || h[2] != 'L' || h[3] != 'F' || h[4] != 2) return out;
            ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
            long phoff = b.getLong(32);
            int phentsize = b.getShort(54) & 0xffff;
            int phnum = b.getShort(56) & 0xffff;

            long dynOff = -1, dynSize = 0;
            List<long[]> loads = new ArrayList<>();
            for (int i = 0; i < phnum; i++) {
                r.seek(phoff + (long) i * phentsize);
                byte[] p = new byte[56];
                r.readFully(p);
                ByteBuffer pb = ByteBuffer.wrap(p).order(ByteOrder.LITTLE_ENDIAN);
                int type = pb.getInt(0);
                long off = pb.getLong(8);
                long vaddr = pb.getLong(16);
                long filesz = pb.getLong(32);
                if (type == 1) loads.add(new long[]{vaddr, off, filesz});
                else if (type == 2) { dynOff = off; dynSize = filesz; }
            }
            if (dynOff < 0) return out;

            byte[] d = new byte[(int) dynSize];
            r.seek(dynOff);
            r.readFully(d);
            ByteBuffer db = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
            long strtab = 0;
            List<Long> nameOffs = new ArrayList<>();
            for (int i = 0; i + 16 <= d.length; i += 16) {
                long tag = db.getLong(i);
                long val = db.getLong(i + 8);
                if (tag == 0) break;
                if (tag == 1) nameOffs.add(val);
                else if (tag == 5) strtab = val;
            }

            long strOff = -1;
            for (long[] l : loads) {
                if (strtab >= l[0] && strtab < l[0] + l[2]) {
                    strOff = strtab - l[0] + l[1];
                    break;
                }
            }
            if (strOff < 0) return out;

            for (long no : nameOffs) {
                r.seek(strOff + no);
                byte[] buf = new byte[256];
                int n = r.read(buf);
                int e = 0;
                while (e < n && buf[e] != 0) e++;
                out.add(new String(buf, 0, e, "UTF-8"));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void symlink(File dir, String name, String target) {
        File l = new File(dir, name);
        l.delete();
        try {
            Os.symlink(target, l.getAbsolutePath());
        } catch (Exception ignored) {
        }
    }

    private static boolean hasSubDirs(File dir) {
        File[] kids = dir.listFiles();
        if (kids == null) return false;
        for (File k : kids) if (k.isDirectory()) return true;
        return false;
    }

    /** Runs a command. Returns null on success, or its output on failure. */
    private static String exec(List<String> cmd, File workDir, String libPath) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir);
        pb.redirectErrorStream(true);
        pb.environment().put("LD_LIBRARY_PATH", libPath);
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
