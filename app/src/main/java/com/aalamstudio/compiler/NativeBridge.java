package com.aalamstudio.compiler;

import android.content.Context;

public class NativeBridge {
    static {
        System.loadLibrary("aalam");
    }

    private static Context appContext;

    public static void init(Context c) {
        appContext = c.getApplicationContext();
    }

    public static native String build(String projectDir, String target);

    /** Called from C++ for steps that run in Java. */
    public static String runJavaStep(String step, String dir) {
        if ("aapt2".equals(step)) return AaptRunner.run(appContext, dir);
        if ("ecj".equals(step)) return EcjRunner.compile(appContext, dir);
        return "ERROR: unknown step " + step;
    }
}
