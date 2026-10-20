package com.aalam.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;

public abstract class GameEngine {
    private Context context;
    private int width;
    private int height;
    private String scene = "";

    /** Runtime isko onGameStart() se pehle call karta hai. */
    final void attach(Context ctx, int w, int h) {
        context = ctx;
        width = w;
        height = h;
    }

    final void resize(int w, int h) {
        width = w;
        height = h;
    }

    /** Game shuru hone par ek baar. */
    public void onGameStart() { }

    /** Har frame, deltaSeconds = pichhle frame se kitna time (second me). */
    public void onUpdate(float deltaSeconds) { }

    /** Har frame ki drawing. */
    public void onDraw(Canvas canvas) { }

    /** Touch event. true return karo agar tumne handle kiya. */
    public boolean onTouch(MotionEvent event) { return false; }

    public void onGamePause() { }

    public void onGameResume() { }

    public final void loadScene(String name) {
        scene = (name == null) ? "" : name;
    }

    public final String getScene() { return scene; }

    public final int getWidth() { return width; }

    public final int getHeight() { return height; }

    public final Context getContext() { return context; }
}
