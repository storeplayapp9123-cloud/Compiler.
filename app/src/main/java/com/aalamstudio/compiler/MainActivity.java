package com.aalamstudio.compiler;

import android.app.Activity;
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_ASC = 1;
    private static final int SAVE_APK = 2;
    private static final int GOLD = Color.parseColor("#D4AF37");
    private TextView logView;
    private Button saveBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        if (requestCode == PICK_ASC) {
            final Uri uri = data.getData();
            new Thread(() -> runBuild(uri)).start();
        } else if (requestCode == SAVE_APK) {
            final Uri dest = data.getData();
            new Thread(() -> saveApk(dest)).start();
        }
    }

    private void runBuild(Uri uri) {
        try {
            FinishRunner.lastApk = null;
            runOnUiThread(() -> saveBtn.setVisibility(View.GONE));

            File work = new File(getFilesDir(), "work");
            deleteRecursive(work);
            work.mkdirs();

            append("Extracting .asc ...");
            int count = unzip(getContentResolver().openInputStream(uri), work);
            append("Extracted " + count + " files");

            String out = NativeBridge.build(work.getAbsolutePath(), "android");
            append(out);

            if (FinishRunner.lastApk != null) {
                runOnUiThread(() -> saveBtn.setVisibility(View.VISIBLE));
            }
        } catch (Exception e) {
            append("ERROR: " + e);
        }
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
