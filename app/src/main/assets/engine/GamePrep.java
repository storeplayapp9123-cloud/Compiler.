package com.aalamstudio.compiler;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Android game project ko verify karta hai aur build ke liye taiyar karta hai. */
public class GamePrep {

    public static final String[] ENGINE_FILES = {"GameEngine.java", "GameView.java", "GameActivity.java"};
    private static final String PKG_REGEX = "[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+";

    public static class Info {
        public String appName;
        public String appPackage;
        public String gameClass;    // poora naam, jaise com.example.game.GameMain
        public String orientation;  // portrait ya sensorLandscape
    }

    public static class Fail extends Exception {
        public Fail(String message) {
            super(message);
        }
    }

    public static boolean isGame(JSONObject manifest) {
        return "game".equalsIgnoreCase(manifest.optString("type", "").trim());
    }

    /** Game project theek hai ya nahi. Galat ho to Fail fenkta hai. */
    public static Info inspect(File work, JSONObject manifest) throws Fail, IOException {
        boolean android = false;
        JSONArray pl = manifest.optJSONArray("platforms");
        if (pl != null) {
            for (int i = 0; i < pl.length(); i++) {
                if ("android".equalsIgnoreCase(pl.optString(i).trim())) android = true;
            }
        }
        if (!android && "android".equalsIgnoreCase(manifest.optString("platform", "").trim())) {
            android = true;
        }
        if (!android) {
            throw new Fail("Is game me Android platform nahi hai (abhi sirf Android games)");
        }

        String appName = manifest.optString("name", "").trim();
        String appPkg = manifest.optString("package", "").trim();
        if (appName.isEmpty()) throw new Fail("Project ka naam nahi mila");
        if (!appPkg.matches(PKG_REGEX)) throw new Fail("Package name galat hai: \"" + appPkg + "\"");

        File gameMain = findExact(work, "GameMain.java", 8);
        if (gameMain == null) {
            File other = findPrefix(work, "GameMain.", 8);
            if (other != null) {
                throw new Fail("Abhi sirf Java me likhe Android games compile hote hain (mila: "
                        + other.getName() + "). Studio me language Java chuno.");
            }
            throw new Fail("GameMain.java nahi mili");
        }

        String src = readText(gameMain);
        if (!Pattern.compile("extends\\s+GameEngine\\b").matcher(src).find()) {
            throw new Fail("GameMain, GameEngine se extend nahi karta");
        }

        String cls = "GameMain";
        Matcher pm = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(src);
        if (pm.find()) cls = pm.group(1) + ".GameMain";

        String orientation = "portrait";
        File cfg = findExact(work, "game_config.json", 8);
        if (cfg != null) {
            try {
                String o = new JSONObject(readText(cfg)).optString("orientation", "portrait");
                if (o.toLowerCase().contains("land")) orientation = "sensorLandscape";
            } catch (Exception ignored) {
            }
        }

        Info info = new Info();
        info.appName = appName;
        info.appPackage = appPkg;
        info.gameClass = cls;
        info.orientation = orientation;
        return info;
    }

    /** AndroidManifest.xml banata hai aur engine ki source files project me rakhta hai. */
    public static void prepare(Context ctx, File work, Info info) throws IOException {
        writeText(new File(work, "AndroidManifest.xml"), manifestXml(info));

        File dir = new File(work, "src/_engine/com/aalam/engine");
        dir.mkdirs();
        for (String f : ENGINE_FILES) {
            try (InputStream in = ctx.getAssets().open("engine/" + f);
                 OutputStream out = new FileOutputStream(new File(dir, f))) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            } catch (IOException e) {
                throw new IOException("Engine file assets me nahi mili: engine/" + f);
            }
        }
    }

    private static String manifestXml(Info info) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n"
                + "    package=\"" + esc(info.appPackage) + "\">\n\n"
                + "    <application\n"
                + "        android:label=\"" + esc(info.appName) + "\"\n"
                + "        android:theme=\"@android:style/Theme.DeviceDefault.NoActionBar.Fullscreen\">\n"
                + "        <activity\n"
                + "            android:name=\"com.aalam.engine.GameActivity\"\n"
                + "            android:exported=\"true\"\n"
                + "            android:screenOrientation=\"" + info.orientation + "\"\n"
                + "            android:configChanges=\"orientation|screenSize|keyboardHidden\">\n"
                + "            <meta-data\n"
                + "                android:name=\"aalam.game_class\"\n"
                + "                android:value=\"" + esc(info.gameClass) + "\" />\n"
                + "            <intent-filter>\n"
                + "                <action android:name=\"android.intent.action.MAIN\" />\n"
                + "                <category android:name=\"android.intent.category.LAUNCHER\" />\n"
                + "            </intent-filter>\n"
                + "        </activity>\n"
                + "    </application>\n"
                + "</manifest>\n";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static File findExact(File dir, String name, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) {
            if (f.isFile() && f.getName().equals(name)) return f;
        }
        if (depth == 0) return null;
        for (File f : kids) {
            if (f.isDirectory()) {
                File r = findExact(f, name, depth - 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private static File findPrefix(File dir, String prefix, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) {
            if (f.isFile() && f.getName().startsWith(prefix)) return f;
        }
        if (depth == 0) return null;
        for (File f : kids) {
            if (f.isDirectory()) {
                File r = findPrefix(f, prefix, depth - 1);
                if (r != null) return r;
            }
        }
        return null;
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
}
