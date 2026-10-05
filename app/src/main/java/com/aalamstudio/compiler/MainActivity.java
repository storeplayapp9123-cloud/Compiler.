package com.aalamstudio.compiler;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class MainActivity extends Activity {
    private static final int PICK_ASC = 1;
    private static final int GOLD = Color.parseColor("#D4AF37");
    private TextView logView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
        if (requestCode == PICK_ASC && resultCode == RESULT_OK && data != null) {
            final Uri uri = data.getData();
            new Thread(() -> runBuild(uri)).start();
        }
    }

    private void runBuild(Uri uri) {
        try {
            File work = new File(getFilesDir(), "work");
            deleteRecursive(work);
            work.mkdirs();

            append("Extracting .asc ...");
            int count = unzip(getContentResolver().openInputStream(uri), work);
            append("Extracted " + count + " files");

            String out = NativeBridge.build(work.getAbsolutePath(), "android");
            append(out);
        } catch (Exception e) {
            append("ERROR: " + e);
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
