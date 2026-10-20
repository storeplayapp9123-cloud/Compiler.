package com.aalamstudio.compiler;

import android.content.Context;

public class NativeBridge {
    static {
        System.loadLibrary("aalam");
    }

    private static Context appContext;

    /** MainActivity yahan apna listener rakhti hai, taaki live progress dikhe. */
    public interface Progress {
        void onStep(String message);
    }

    public static volatile Progress progress;

    public static void init(Context c) {
        appContext = c.getApplicationContext();
    }

    public static native String build(String projectDir, String target);

    /** Called from C++ for steps that run in Java. */
    public static String runJavaStep(String step, String dir) {
        say(startMessage(step));
        long t0 = System.currentTimeMillis();
        String result = runStep(step, dir);
        long secs = (System.currentTimeMillis() - t0) / 1000;
        say("   " + step + " khatam (" + secs + " sec)");
        return result;
    }

    private static String runStep(String step, String dir) {
        if ("aapt2".equals(step)) return AaptRunner.run(appContext, dir);
        if ("ecj".equals(step)) return EcjRunner.compile(appContext, dir);
        if ("d8".equals(step)) return DexRunner.run(appContext, dir);
        if ("pack".equals(step)) return PackRunner.run(dir);
        if ("sign".equals(step)) return SignRunner.run(dir);
        if ("finish".equals(step)) return FinishRunner.run(appContext, dir);
        return "ERROR: unknown step " + step;
    }

    private static String startMessage(String step) {
        switch (step) {
            case "aapt2":
                return "▶ Resources (aapt2)... pehli baar AndroidX res compile hoti hai";
            case "ecj":
                return "▶ Java compile (ECJ)...";
            case "d8":
                return "▶ DEX (D8)... pehli baar AndroidX dex hota hai, kuch minute lag sakte hain";
            case "pack":
                return "▶ APK packaging...";
            case "sign":
                return "▶ APK signing...";
            case "finish":
                return "▶ Finishing...";
            default:
                return "▶ " + step + "...";
        }
    }

    private static void say(String msg) {
        Progress p = progress;
        if (p != null) p.onStep(msg);
    }
}
