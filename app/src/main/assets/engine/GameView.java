package com.aalam.engine;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    private final GameEngine game;
    private Thread thread;
    private volatile boolean running;
    private boolean started;

    GameView(Context ctx, GameEngine game) {
        super(ctx);
        this.game = game;
        getHolder().addCallback(this);
        setFocusable(true);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this, "aalam-game");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        game.resize(w, h);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        boolean retry = true;
        while (retry && thread != null) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Override
    public void run() {
        SurfaceHolder holder = getHolder();
        long last = System.nanoTime();

        while (running) {
            long now = System.nanoTime();
            float dt = (now - last) / 1000000000f;
            last = now;
            if (dt > 0.1f) dt = 0.1f;

            if (!started) {
                game.attach(getContext(), getWidth(), getHeight());
                game.onGameStart();
                started = true;
            }

            game.onUpdate(dt);

            Canvas c = null;
            try {
                c = holder.lockCanvas();
                if (c != null) {
                    c.drawColor(Color.BLACK);
                    game.onDraw(c);
                }
            } finally {
                if (c != null) holder.unlockCanvasAndPost(c);
            }

            long usedMs = (System.nanoTime() - now) / 1000000L;
            long sleepMs = 16 - usedMs;
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException ignored) {
                }
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return game.onTouch(e) || super.onTouchEvent(e);
    }
}
