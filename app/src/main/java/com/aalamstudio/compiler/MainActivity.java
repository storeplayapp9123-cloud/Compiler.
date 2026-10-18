package com.aalamstudio.compiler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_ASC = 1;
    private static final int SAVE_APK = 2;
    private static final int GOLD = Color.parseColor("#D4AF37");
    private static final String PENDING_NAME = "incoming.asc";

    private TextView logView;
    private Button saveBtn;
    private Button cloudBtn;

    private byte[] lastAsc;
    private String lastAscName = "project";

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
        root.setPadding(40, 24, 40, 24);

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
        who.setPadding(0, 8, 0, 8);
        who.setOnClickListener(v -> signOut());
        root.addView(who);

        Button open = new Button(this);
        open.setText("Open .asc file");
        open.setTextColor(Color.BLACK);
        open.setBackgroundColor(GOLD);
        open.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, PICK_ASC);
        });
        root.addView(open);

        Button mine = new Button(this);
        mine.setText("My cloud projects");
        mine.setTextColor(GOLD);
        mine.setBackgroundColor(Color.parseColor("#222222"));
        mine.setOnClickListener(v -> showCloudProjects());
        root.addView(mine);

        cloudBtn = new Button(this);
        cloudBtn.setText("Save this project to cloud");
        cloudBtn.setTextColor(Color.BLACK);
        cloudBtn.setBackgroundColor(Color.LTGRAY);
        cloudBtn.setVisibility(View.GONE);
        cloudBtn.setOnClickListener(v -> {
            if (lastAsc == null) return;
            append("Uploading to cloud...");
            CloudSync.upload(lastAscName, lastAsc, logResult());
        });
        root.addView(cloudBtn);

        saveBtn = new Button(this);
        saveBtn.setText("Save APK");
        saveBtn.setTextColor(Color.BLACK);
        saveBtn.setBackgroundColor(Color.WHITE);
        saveBtn.setVisibility(View.GONE);
        saveBtn.setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/vnd.android.package-archive");
            i.putExtra(Intent.EXTRA_TITLE, FinishRunner.lastName);
            startActivityForResult(i, SAVE_APK);
        });
        root.addView(saveBtn);

        ScrollView scroll = new ScrollView(this);
        logView = new TextView(this);
        logView.setTextColor(Color.WHITE);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setText("Ready. Open a .asc file to build.\n");
        scroll.addView(logView);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        // Studio se aayi .asc (ya login ke baad cache me rakhi .asc) build karo.
        // Rotate hone par dobara build na chale.
        if (savedInstanceState == null) {
            handleIncoming();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_ASC) {
            final Uri uri = data.getData();
            new Thread(() -> openAsc(uri)).start();
        } else if (requestCode == SAVE_APK) {
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
            append("Received .asc from Aalam Studio");
            new Thread(() -> openAsc(uri)).start();
            return;
        }

        final File pending = new File(getCacheDir(), PENDING_NAME);
        if (!pending.exists()) return;

        boolean fresh = System.currentTimeMillis() - pending.lastModified() < 10 * 60 * 1000L;
        if (!fresh) {
            pending.delete();
            return;
        }

        append("Received .asc from Aalam Studio");
        new Thread(() -> {
            try (InputStream is = new FileInputStream(pending)) {
                byte[] bytes = readAll(is);
                pending.delete();
                runBuild(bytes);
            } catch (Exception e) {
                append("ERROR: " + e);
            }
        }).start();
    }

    // ---------- build ----------

    private void openAsc(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            runBuild(readAll(in));
        } catch (Exception e) {
            append("ERROR: " + e);
        }
    }

    private void runBuild(byte[] asc) {
        try {
            lastAsc = asc;
            FinishRunner.lastApk = null;
            runOnUiThread(() -> {
                saveBtn.setVisibility(View.GONE);
                cloudBtn.setVisibility(View.GONE);
            });

            File work = new File(getFilesDir(), "work");
            deleteRecursive(work);
            work.mkdirs();

            append("Extracting .asc ...");
            int count = unzip(new ByteArrayInputStream(asc), work);
            append("Extracted " + count + " files");

            lastAscName = readProjectName(work);
            runOnUiThread(() -> cloudBtn.setVisibility(View.VISIBLE));

            String out = NativeBridge.build(work.getAbsolutePath(), "android");
            append(out);

            if (FinishRunner.lastApk != null) {
                runOnUiThread(() -> saveBtn.setVisibility(View.VISIBLE));
            }
        } catch (Exception e) {
            append("ERROR: " + e);
        }
    }

    private String readProjectName(File work) {
        try (InputStream in = new FileInputStream(new File(work, "manifest.json"))) {
            String n = new JSONObject(new String(readAll(in), "UTF-8")).optString("name", "");
            if (!n.trim().isEmpty()) return n.trim();
        } catch (Exception ignored) {
        }
        return "project";
    }

    private void saveApk(Uri dest) {
        try (InputStream in = new FileInputStream(FinishRunner.lastApk);
             OutputStream out = getContentResolver().openOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
            append("Saved!");
        } catch (Exception e) {
            append("Save ERROR: " + e);
        }
    }

    // ---------- cloud ----------

    private CloudSync.Result<String> logResult() {
        return new CloudSync.Result<String>() {
            @Override public void ok(String v) { append(v); }
            @Override public void fail(String m) { append("Cloud ERROR: " + m); }
        };
    }

    private void showCloudProjects() {
        append("Loading cloud projects...");
        CloudSync.list(new CloudSync.Result<List<CloudSync.Item>>() {
            @Override
            public void ok(final List<CloudSync.Item> items) {
                if (items.isEmpty()) {
                    append("Cloud me abhi koi project nahi hai");
                    return;
                }
                String[] names = new String[items.size()];
                for (int i = 0; i < names.length; i++) {
                    names[i] = items.get(i).name + "  (" + (items.get(i).size / 1024) + " KB)";
                }
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("My cloud projects")
                        .setItems(names, (d, i) -> projectActions(items.get(i)))
                        .show();
            }

            @Override
            public void fail(String m) {
                append("Cloud ERROR: " + m);
            }
        });
    }

    private void projectActions(final CloudSync.Item it) {
        new AlertDialog.Builder(this)
                .setTitle(it.name)
                .setItems(new String[]{"Build", "Delete"}, (d, which) -> {
                    if (which == 0) {
                        append("Downloading " + it.name + " ...");
                        CloudSync.download(it.id, new CloudSync.Result<byte[]>() {
                            @Override
                            public void ok(final byte[] bytes) {
                                new Thread(() -> runBuild(bytes)).start();
                            }

                            @Override
                            public void fail(String m) {
                                append("Cloud ERROR: " + m);
                            }
                        });
                    } else {
                        CloudSync.delete(it.id, logResult());
                    }
                })
                .show();
    }

    // ---------- misc ----------

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

    private void append(String s) {
        runOnUiThread(() -> logView.append(s + "\n"));
    }
}
