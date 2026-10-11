package com.aalamstudio.compiler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;

public class LoginActivity extends Activity {
    private static final int RC_SIGN_IN = 9001;
    private static final int GOLD = Color.parseColor("#D4AF37");

    private FirebaseAuth auth;
    private GoogleSignInClient gsc;
    private View signInBtn;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            goMain();
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        gsc = GoogleSignIn.getClient(this, gso);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // ---- beech me: title, subtitle, status ----
        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        center.setPadding(dp(24), dp(12), dp(24), dp(12));
        root.addView(center, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView title = new TextView(this);
        title.setText("AALAM COMPILER");
        title.setTextColor(GOLD);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        center.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Sign in to save and sync your projects");
        sub.setTextColor(Color.LTGRAY);
        sub.setGravity(Gravity.CENTER);
        center.addView(sub);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#FF6B6B"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(16), 0, 0);
        center.addView(status);

        // ---- top-right corner: label + Google icon ----
        LinearLayout corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.VERTICAL);
        corner.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView label = new TextView(this);
        label.setText("Sign in with Google");
        label.setTextColor(Color.LTGRAY);
        label.setTextSize(12);
        label.setGravity(Gravity.CENTER);
        corner.addView(label);

        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.WHITE);

        ImageView g = new ImageView(this);
        g.setImageResource(R.drawable.ic_google);
        g.setBackground(circle);
        g.setPadding(dp(14), dp(14), dp(14), dp(14));
        g.setScaleType(ImageView.ScaleType.FIT_CENTER);
        g.setContentDescription("Sign in with Google");
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(dp(56), dp(56));
        gp.topMargin = dp(6);
        corner.addView(g, gp);
        signInBtn = g;

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        cp.topMargin = dp(20);
        cp.setMarginEnd(dp(24));
        root.addView(corner, cp);

        signInBtn.setOnClickListener(v -> {
            status.setText("");
            gsc.signOut().addOnCompleteListener(t ->
                    startActivityForResult(gsc.getSignInIntent(), RC_SIGN_IN));
        });

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != RC_SIGN_IN) return;

        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            firebaseLogin(acct.getIdToken());
        } catch (ApiException e) {
            status.setTextColor(Color.parseColor("#FF6B6B"));
            status.setText(explain(e.getStatusCode()));
        }
    }

    private void firebaseLogin(String idToken) {
        signInBtn.setEnabled(false);
        signInBtn.setAlpha(0.4f);
        status.setTextColor(Color.LTGRAY);
        status.setText("Signing in...");
        AuthCredential cred = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(cred).addOnCompleteListener(this, t -> {
            if (t.isSuccessful()) {
                goMain();
            } else {
                signInBtn.setEnabled(true);
                signInBtn.setAlpha(1f);
                status.setTextColor(Color.parseColor("#FF6B6B"));
                status.setText("Login failed: " + (t.getException() == null
                        ? "unknown" : t.getException().getMessage()));
            }
        });
    }

    private String explain(int code) {
        switch (code) {
            case 10:    return "Error 10: SHA-1 ya google-services.json galat hai";
            case 12500: return "Error 12500: Firebase me Google sign-in enable aur support email check karo";
            case 12501: return "Sign-in cancel hua (12501)";
            case 7:     return "Internet nahi hai";
            default:    return "Sign-in failed (code " + code + ")";
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
