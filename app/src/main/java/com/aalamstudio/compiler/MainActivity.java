package com.aalamstudio.compiler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int SAVE_APK = 2;
    private static final int GOLD = Color.parseColor("#D4AF37");
    private static final String PENDING_NAME = "incoming.asc";

    private TextView logView;
    private TextView verifyView;
    private TextView apkCard;
    private Button startBtn;

    private byte[] lastAsc;
    private boolean verified = false;
    private boolean building = false;
    private File readyApk;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Studio se .asc aayi ho to login se pehle cache me rakh lo
            stashIncoming();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        NativeBridge.init(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(20), dp(12), dp(20), dp(12));

        TextView title = new TextView(this);
        title.setText("AALAM COMPILER");
        title.setTextColor(GOLD);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Code to Compile");
        sub.setTextColor(Color.LTGRAY);
        root.addView(sub);

        TextView who = new TextView(this);
        who.setText((user.getEmail() == null ? "" : user.getEmail()) + "   |   Sign out");
        who.setTextColor(Color.GRAY);
        who.setPadding(0, dp(4), 0, dp(8));
        who.setOnClickListener(v -> signOut());
        root.addView(who);

        // Verify status
        verifyView = new TextView(this);
        verifyView.setText("Aalam Studio me Build APK dabao. Project yaha verify hoga.");
        verifyView.setTextColor(Color.LTGRAY);
        verifyView.setBackgroundColor(Color.parseColor("#151515"));
        verifyView.setPadding(dp(12), dp(10), dp(12), dp(10));
        root.addView(verifyView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Log
        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.WHITE);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setPadding(0, dp(8), 0, dp(8));
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // Bottom-right: APK card (upar) + START (niche)
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setGravity(Gravity.END);

        apkCard = new TextView(this);
        apkCard.setTextColor(Color.BLACK);
        apkCard.setBackgroundColor(Color.WHITE);
        apkCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        apkCard.setVisibility(View.GONE);
        apkCard.setOnClickListener(v -> installApk());
        apkCard.setOnLongClickListener(v -> {
            saveApkAs();
            return true;
        });
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                dp(300), LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = dp(8);
        bottom.addView(apkCard, cardLp);

        startBtn = new Button(this);
        startBtn.setText("START");
        startBtn.setTextColor(Color.BLACK);
        startBtn.setBackgroundColor(GOLD);
        startBtn.setOnClickListener(v -> startBuild());
        setStartEnabled(false);
        bottom.addView(startBtn, new LinearLayout.LayoutParams(
                dp(160), LinearLayout.LayoutParams.WRAP_CONTENT));

        root.addView(bottom, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        setContentView(root);

        // Rotate hone par dobara verify na chale
        if (savedInstanceState == null) {
            handleIncoming();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SAVE_APK && resultCode == RESULT_OK && data != null) {
            final Uri dest = data.getData();
            new Thread(() -> saveApk(dest)).start();
        }
    }

    // ---------- incoming .asc (from Aalam Studio) ----------

    /** Login baaki ho to aayi hui .asc ko cache me copy kar lo. */
    private void stashIncoming() {
        Intent intent = getIntent();
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())
                || intent.getData() == null) return;
        try (InputStream is = getContentResolver().openInputStream(intent.getData());
             OutputStream os = new FileOutputStream(new File(getCacheDir(), PENDING_NAME))) {
            if (is == null) return;
            byte[] buf = new byte[65536];
            int r;
            while ((r = is.read(buf)) > 0) os.write(buf, 0, r);
        } catch (Exception ignored) {
        }
    }

    private void handleIncoming() {
        Intent intent = getIntent();
        final Uri uri = (intent != null && Intent.ACTION_VIEW.equals(intent.getAction()))
                ? intent.getData() : null;

        if (uri != null) {
            new Thread(() -> {
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    verifyAsc(readAll(in));
                } catch (Exception e) {
                    verifyFail("File nahi padh payi: " + e.getMessage());
                }
            }).start();
            return;
        }

        final File pending = new File(getCacheDir(), PENDING_NAME);
        if (!pending.exists()) return;

        boolean fresh = System.currentTimeMillis() - pending.lastModified() < 10 * 60 * 1000L;
        if (!fresh) {
            pending.delete();
            return;
        }
        new Thread(() -> {
            try (InputStream is = new FileInputStream(pending)) {
                byte[] bytes = readAll(is);
                pending.delete();
                verifyAsc(bytes);
            } catch (Exception e) {
                verifyFail("File nahi padh payi: " + e.getMessage());
            }
        }).start();
    }

    // ---------- verify ----------

    private void verifyAsc(byte[] asc) {
        lastAsc = asc;
        verified = false;
        runOnUiThread(() -> {
            setStartEnabled(false);
            apkCard.setVisibility(View.GONE);
            verifyView.setTextColor(Color.LTGRAY);
            verifyView.setText("Verifying...");
        });
        try {
            File work = freshWork();
            unzip(new ByteArrayInputStream(asc), work);

            File mj = new File(work, "manifest.json");
            if (!mj.isFile()) {
                verifyFail("manifest.json nahi mila");
                return;
            }
            JSONObject j;
            try (InputStream in = new FileInputStream(mj)) {
                j = new JSONObject(new String(readAll(in), "UTF-8"));
            }
            final String name = j.optString("name", "").trim();
            final String pkg = j.optString("package", "").trim();
            if (name.isEmpty()) {
                verifyFail("Project ka naam nahi mila");
                return;
            }
            if (!pkg.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+")) {
                verifyFail("Package name galat hai: \"" + pkg + "\"");
                return;
            }
            if (findFile(work, "AndroidManifest.xml", 6) == null) {
                verifyFail("AndroidManifest.xml nahi mila (Android app project chahiye)");
                return;
            }
            if (!hasSource(work, 8)) {
                verifyFail("Java/Kotlin source file nahi mili");
                return;
            }

            verified = true;
            runOnUiThread(() -> {
                verifyView.setTextColor(Color.parseColor("#7CFC7C"));
                verifyView.setText("✓ Verified: " + name + "  (" + pkg + ")");
                setStartEnabled(true);
            });
        } catch (Exception e) {
            verifyFail("Verify error: " + e.getMessage());
        }
    }

    private void verifyFail(final String why) {
        verified = false;
        runOnUiThread(() -> {
            verifyView.setTextColor(Color.parseColor("#FF6B6B"));
            verifyView.setText("✗ Verify fail: " + why);
            setStartEnabled(false);
        });
    }

    // ---------- build ----------

    private void startBuild() {
        if (!verified || building || lastAsc == null) return;
        building = true;
        logView.setText("");
        apkCard.setVisibility(View.GONE);
        setStartEnabled(false);
        startBtn.setText("COMPILING...");

        new Thread(() -> {
            try {
                // har baar fresh extract, taaki build hamesha saaf folder me ho
                File work = freshWork();
                unzip(new ByteArrayInputStream(lastAsc), work);

                FinishRunner.lastApk = null;
                append("Compiling...");
                String out = NativeBridge.build(work.getAbsolutePath(), "android");
                append(out);

                if (FinishRunner.lastApk != null) {
                    prepareApkCard();
                }
            } catch (Exception e) {
                append("ERROR: " + e);
            } finally {
                building = false;
                runOnUiThread(() -> {
                    startBtn.setText("START");
                    setStartEnabled(verified);
                });
            }
        }).start();
    }

    /** Compile hui APK ko cache me copy karke START ke upar card dikhao. */
    private void prepareApkCard() {
        try {
            File src = new File(String.valueOf(FinishRunner.lastApk));
            String name = String.valueOf(FinishRunner.lastName);
            if (name.isEmpty() || name.equals("null")) name = "app.apk";
            if (!name.endsWith(".apk")) name += ".apk";

            File dir = new File(getCacheDir(), "apk");
            deleteRecursive(dir);
            dir.mkdirs();
            File dest = new File(dir, name);
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[65536];
                int r;
                while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            }
            readyApk = dest;

            final String label = "📦 " + name + "  (" + (dest.length() / 1024) + " KB)"
                    + "\nTap: Install   •   Long press: Save";
            runOnUiThread(() -> {
                apkCard.setText(label);
                apkCard.setVisibility(View.VISIBLE);
            });
        } catch (Exception e) {
            append("APK ready nahi hui: " + e);
        }
    }

    // ---------- install / save ----------

    private void installApk() {
        if (readyApk == null || !readyApk.exists()) return;

        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(this, "Install permission allow karo, phir dobara tap karo",
                    Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())));
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", readyApk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Install fail: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void saveApkAs() {
        if (readyApk == null) return;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/vnd.android.package-archive");
        i.putExtra(Intent.EXTRA_TITLE, readyApk.getName());
        startActivityForResult(i, SAVE_APK);
    }

    private void saveApk(Uri dest) {
        try (InputStream in = new FileInputStream(readyApk);
             OutputStream out = getContentResolver().openOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            append("Saved!");
        } catch (Exception e) {
            append("Save ERROR: " + e);
        }
    }

    // ---------- misc ----------

    private void setStartEnabled(boolean enabled) {
        startBtn.setEnabled(enabled);
        startBtn.setAlpha(enabled ? 1f : 0.4f);
    }

    private File freshWork() {
        File work = new File(getFilesDir(), "work");
        deleteRecursive(work);
        work.mkdirs();
        return work;
    }

    private File findFile(File dir, String name, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return null;
        for (File f : kids) {
            if (f.isFile() && f.getName().equals(name)) return f;
        }
        if (depth == 0) return null;
        for (File f : kids) {
            if (f.isDirectory()) {
                File r = findFile(f, name, depth - 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private boolean hasSource(File dir, int depth) {
        File[] kids = dir.listFiles();
        if (kids == null) return false;
        for (File f : kids) {
            if (f.isFile() && (f.getName().endsWith(".java") || f.getName().endsWith(".kt"))) return true;
        }
        if (depth == 0) return false;
        for (File f : kids) {
            if (f.isDirectory() && hasSource(f, depth - 1)) return true;
        }
        return false;
    }

    private void signOut() {
        FirebaseAuth.getInstance().signOut();
        GoogleSignIn.getClient(this,
                new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build())
                .signOut()
                .addOnCompleteListener(t -> {
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                });
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toByteArray();
    }

    private int unzip(InputStream in, File dest) throws IOException {
        int count = 0;
        String root = dest.getCanonicalPath() + File.separator;
        byte[] buf = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                File f = new File(dest, e.getName());
                if (!f.getCanonicalPath().startsWith(root)) {
                    throw new IOException("Bad path in .asc: " + e.getName());
                }
                if (e.isDirectory()) {
                    f.mkdirs();
                    continue;
                }
                f.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(f)) {
                    int r;
                    while ((r = zis.read(buf)) > 0) out.write(buf, 0, r);
                }
                count++;
            }
        }
        return count;
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void append(String s) {
        runOnUiThread(() -> logView.append(s + "\n"));
    }
}
