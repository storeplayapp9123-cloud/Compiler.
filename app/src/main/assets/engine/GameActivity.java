package com.aalam.engine;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

public class GameActivity extends Activity {

    private GameEngine game;
    private boolean paused;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        try {
            ActivityInfo ai = getPackageManager()
                    .getActivityInfo(getComponentName(), PackageManager.GET_META_DATA);
            String cls = (ai.metaData == null) ? null : ai.metaData.getString("aalam.game_class");
            if (cls == null) {
                throw new IllegalStateException("aalam.game_class meta-data missing");
            }
            game = (GameEngine) Class.forName(cls).newInstance();
        } catch (Throwable e) {
            Toast.makeText(this, "Game start nahi hui: " + e, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        setContentView(new GameView(this, game));
        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    private void hideSystemBars() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        if (game != null) game.onGamePause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (paused && game != null) {
            paused = false;
            game.onGameResume();
        }
    }
}
