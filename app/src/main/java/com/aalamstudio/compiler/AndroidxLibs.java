package com.aalamstudio.compiler;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** assets/androidx/*.aar|*.jar ko ek baar filesDir/androidx-ready me khol deta hai. */
public class AndroidxLibs {

    public static class Lib {
        public String name;
        public File dir;
        public List<File> jars = new ArrayList<>();
        public File resDir;     // null agar res/ nahi hai
        public File manifest;   // null agar nahi hai
        public File rTxt;       // null agar nahi hai
        public String pkg = ""; // manifest ka package
    }

    private static final String ASSET_DIR = "androidx";
    private static List<Lib> cache;
    private static String cacheStamp;

    public static synchronized List<Lib> prepare(Context ctx) throws IOException {
        String[] names = ctx.getAssets().list(ASSET_DIR);
        if (names == null || names.length == 0) return Collections.emptyList();
        Arrays.sort(names);

        String stamp = lastUpdate(ctx) + ":" + names.length;
        File root = new File(ctx.getFilesDir(), "androidx-ready");
        File stampFile = new File(root, ".stamp");

        if (cache != null && stamp.equals(cacheStamp) && stampFile.exists()) return cache;

        boolean fresh = stampFile.exists() && stamp.equals(readText(stampFile));
        if (!fresh) {
            deleteRecursive(root);
            root.mkdirs();
            for (String n : names) extractAsset(ctx, n, root);
            writeText(stampFile, stamp);
        }
        cache = scan(root);
        cacheStamp = stamp;
        return cache;
    }

    /** ECJ classpath ke liye saare library jars. */
    public static List<File> classpathJars(Context ctx) throws IOException {
        List<File> out = new ArrayList<>();
        for (Lib l : prepare(ctx)) out.addAll(l.jars);
        return out;
    }

    // ---------- extract ----------

    private static void extractAsset(Context ctx, String asset, File root) throws IOException {
        int dot = asset.lastIndexOf('.');
        if (dot <= 0) return;
        String base = asset.substring(0, dot);
        String lower = asset.toLowerCase(Locale.ROOT);

        File dir = new File(root, base);
        dir.mkdirs();
        try (InputStream in = ctx.getAssets().open(ASSET_DIR + "/" + asset)) {
            if (lower.endsWith(".jar")) {
                copy(in, new File(dir, "classes.jar"));
            } else if (lower.endsWith(".aar")) {
                unzipAar(in, dir);
            }
        }
    }

    private static void unzipAar(InputStream in, File dir) throws IOException {
        String rootPath = dir.getCanonicalPath() + File.separator;
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String n = e.getName();
                boolean want = n.equals("classes.jar")
                        || n.equals("AndroidManifest.xml")
                        || n.equals("R.txt")
                        || n.startsWith("res/")
                        || (n.startsWith("libs/") && n.endsWith(".jar"));
                if (!want) continue;

                File f = new File(dir, n);
                if (!f.getCanonicalPath().startsWith(rootPath)) {
                    throw new IOException("Bad path in aar: " + n);
                }
                f.getParentFile().mkdirs();
                try (OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = zis.read(buf)) > 0) out.write(buf, 0, r);
                }
            }
        }
    }

    // ---------- scan ----------

    private static List<Lib> scan(File root) throws IOException {
        File[] dirs = root.listFiles(File::isDirectory);
        List<Lib> libs = new ArrayList<>();
        if (dirs == null) return libs;
        Arrays.sort(dirs);

        Pattern pkgPat = Pattern.compile("package\\s*=\\s*\"([^\"]+)\"");
        for (File d : dirs) {
            Lib l = new Lib();
            l.name = d.getName();
            l.dir = d;

            addJar(l, new File(d, "classes.jar"));
            File[] extra = new File(d, "libs").listFiles((x, n) -> n.endsWith(".jar"));
            if (extra != null) {
                Arrays.sort(extra);
                for (File j : extra) addJar(l, j);
            }

            File res = new File(d, "res");
            String[] resKids = res.list();
            if (resKids != null && resKids.length > 0) l.resDir = res;

            File m = new File(d, "AndroidManifest.xml");
            if (m.isFile()) {
                l.manifest = m;
                Matcher mm = pkgPat.matcher(readText(m));
                if (mm.find()) l.pkg = mm.group(1);
            }
            File r = new File(d, "R.txt");
            if (r.isFile()) l.rTxt = r;

            libs.add(l);
        }
        return libs;
    }

    private static void addJar(Lib l, File j) {
        if (j.isFile() && j.length() > 22) l.jars.add(j); // 22 byte = khali zip
    }

    // ---------- helpers ----------

    private static long lastUpdate(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).lastUpdateTime;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void copy(InputStream in, File dest) throws IOException {
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    private static String readText(File f) throws IOException {
        try (InputStream in = new java.io.FileInputStream(f)) {
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
