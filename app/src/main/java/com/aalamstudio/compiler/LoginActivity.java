package com.aalamstudio.compiler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
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
    private Button signInBtn;
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(60, 24, 60, 24);

        TextView title = new TextView(this);
        title.setText("AALAM COMPILER");
        title.setTextColor(GOLD);
        title.setTextSize(28);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Sign in to save and sync your projects");
        sub.setTextColor(Color.LTGRAY);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        signInBtn = new Button(this);
        signInBtn.setText("Sign in with Google");
        signInBtn.setTextColor(Color.BLACK);
        signInBtn.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = 48;
        root.addView(signInBtn, bp);

        status = new TextView(this);
        status.setTextColor(Color.parseColor("#FF6B6B"));
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, 24, 0, 0);
        root.addView(status);

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
            status.setText(explain(e.getStatusCode()));
        }
    }

    private void firebaseLogin(String idToken) {
        signInBtn.setEnabled(false);
        status.setTextColor(Color.LTGRAY);
        status.setText("Signing in...");
        AuthCredential cred = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(cred).addOnCompleteListener(this, t -> {
            if (t.isSuccessful()) {
                goMain();
            } else {
                signInBtn.setEnabled(true);
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
            case 12501: return "Sign-in cancel hua";
            case 7:     return "Internet nahi hai";
            default:    return "Sign-in failed (code " + code + ")";
        }
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
