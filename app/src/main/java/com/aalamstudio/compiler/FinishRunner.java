package com.aalamstudio.compiler;

import android.content.Context;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Step 7: copy signed.apk to a nicely named output file. */
public class FinishRunner {

    public static volatile File lastApk;
    public static volatile String lastName;

    public static String run(Context ctx, String projectDir) {
        try {
            File signed = new File(projectDir, "build/signed.apk");
            if (!signed.exists()) return "ERROR: signed.apk missing";

            String name = "app";
            try (InputStream in = new FileInputStream(new File(projectDir, "manifest.json"))) {
                byte[] b = new byte[in.available()];
                int n = in.read(b);
                String n2 = new JSONObject(new String(b, 0, Math.max(n, 0), "UTF-8")).optString("name", "app");
                if (!n2.trim().isEmpty()) name = n2.trim();
            } catch (Exception ignored) {
            }
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");

            File dir = ctx.getExternalFilesDir("output");
            if (dir == null) dir = new File(ctx.getFilesDir(), "output");
            dir.mkdirs();
            File dest = new File(dir, name + ".apk");

            try (InputStream in = new FileInputStream(signed);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }

            lastApk = dest;
            lastName = name + ".apk";
            return "OK: " + lastName + " ready (" + dest.length() + " bytes)";
        } catch (Throwable e) {
            return "ERROR: " + e;
        }
    }
}
