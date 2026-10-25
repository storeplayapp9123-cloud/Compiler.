package com.aalamstudio.compiler;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Aalam SDK app (com.aalamstudio.sdk) se files leta hai. SDK na mile to Compiler ke apne assets. */
public class SdkClient {

    public static final String SDK_PACKAGE = "com.aalamstudio.sdk";
    public static final String AUTHORITY = "com.aalamstudio.sdk.provider";

    private static List<String> indexCache;
    private static String indexStamp = "";

    /** SDK app install hai to uska lastUpdateTime, nahi to "". */
    public static String stamp(Context ctx) {
        try {
            return String.valueOf(ctx.getPackageManager().getPackageInfo(SDK_PACKAGE, 0).lastUpdateTime);
        } catch (Exception e) {
            return "";
        }
    }

    /** SDK ki saari files ke path (jaise androidx/foo.aar). SDK na ho to khali. */
    public static synchronized List<String> index(Context ctx) {
        String st = stamp(ctx);
        if (st.isEmpty()) return new ArrayList<>();
        if (indexCache != null && st.equals(indexStamp)) return indexCache;

        List<String> out = new ArrayList<>();
        Uri uri = Uri.parse("content://" + AUTHORITY + "/index");
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) out.add(c.getString(0));
            }
        } catch (Exception ignored) {
        }
        if (!out.isEmpty()) {
            indexCache = out;
            indexStamp = st;
        }
        return out;
    }

    /** dir ke andar ki files ke naam (sirf seedhe bachche). */
    public static List<String> listDir(Context ctx, String dir) {
        String prefix = dir + "/";
        List<String> out = new ArrayList<>();
        for (String p : index(ctx)) {
            if (p.startsWith(prefix)) {
                String rest = p.substring(prefix.length());
                if (!rest.isEmpty() && rest.indexOf('/') < 0) out.add(rest);
            }
        }
        return out;
    }

    public static boolean has(Context ctx, String path) {
        return index(ctx).contains(path);
    }

    /** SDK se file kholo. Na mile to assetFallback (agar diya ho). */
    public static InputStream open(Context ctx, String sdkPath, String assetFallback) throws IOException {
        if (!stamp(ctx).isEmpty()) {
            try {
                InputStream in = ctx.getContentResolver()
                        .openInputStream(Uri.parse("content://" + AUTHORITY + "/" + sdkPath));
                if (in != null) return in;
            } catch (Exception ignored) {
            }
        }
        if (assetFallback != null) return ctx.getAssets().open(assetFallback);
        throw new FileNotFoundException(sdkPath);
    }

    /**
     * android.jar ka File. SDK me ho to wahan se (SDK badalne par naya), nahi to
     * Compiler ke apne assets se.
     */
    public static File androidJar(Context ctx) throws IOException {
        File jar = new File(ctx.getFilesDir(), "android.jar");
        File stampFile = new File(ctx.getFilesDir(), "android.jar.stamp");
        String sdk = stamp(ctx);

        if (!sdk.isEmpty() && has(ctx, "platform/android.jar")) {
            boolean fresh = jar.isFile() && jar.length() > 0
                    && stampFile.isFile() && sdk.equals(readText(stampFile));
            if (!fresh) {
                File tmp = new File(ctx.getFilesDir(), "android.jar.tmp");
                try (InputStream in = open(ctx, "platform/android.jar", null)) {
                    copy(in, tmp);
                    if (tmp.length() > 0 && tmp.renameTo(jar)) {
                        writeText(stampFile, sdk);
                    }
                } catch (IOException e) {
                    tmp.delete();
                }
            }
        }

        if (!jar.isFile() || jar.length() == 0) {
            try (InputStream in = ctx.getAssets().open("android.jar")) {
                copy(in, jar);
                stampFile.delete();
            } catch (IOException e) {
                throw new IOException("android.jar nahi mili. Aalam SDK app install karo. ("
                        + e.getMessage() + ")");
            }
        }
        return jar;
    }

    // ---------- helpers ----------

    private static void copy(InputStream in, File dest) throws IOException {
        try (OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        }
    }

    private static String readText(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[256];
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
}
