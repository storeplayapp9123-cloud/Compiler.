package com.aalamstudio.compiler;

public class NativeBridge {
    static {
        System.loadLibrary("aalam");
    }

    public static native String build(String projectDir, String target);
}
