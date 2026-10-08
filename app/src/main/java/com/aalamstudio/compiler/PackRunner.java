package com.aalamstudio.compiler;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Step 5: base.apk + classes.dex -> unsigned.apk (resources.arsc 4-byte aligned). */
public class PackRunner {

    public static String run(String projectDir) {
        try {
            File build = new File(projectDir, "build");
            File base = new File(build, "base.apk");
            File out = new File(build, "unsigned.apk");
            if (!base.exists()) return "ERROR: base.apk missing";

            File[] dexes = new File(build, "dex").listFiles((d, n) -> n.endsWith(".dex"));
            if (dexes == null || dexes.length == 0) return "ERROR: no .dex files in build/dex";
            Arrays.sort(dexes);

            int count = 0;
            CountingOut counter = new CountingOut(new BufferedOutputStream(new FileOutputStream(out)));
            try (ZipFile zf = new ZipFile(base);
                 ZipOutputStream zos = new ZipOutputStream(counter)) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    String name = e.getName();
                    if (name.endsWith(".dex")) continue;
                    byte[] data;
                    try (InputStream in = zf.getInputStream(e)) {
                        data = readAll(in);
                    }
                    if (e.getMethod() == ZipEntry.STORED || name.equals("resources.arsc")) {
                        writeStored(zos, counter, name, data);
                    } else {
                        writeDeflated(zos, name, data);
                    }
                    count++;
                }
                for (File d : dexes) {
                    byte[] data;
                    try (InputStream in = new FileInputStream(d)) {
                        data = readAll(in);
                    }
                    writeDeflated(zos, d.getName(), data);
                    count++;
                }
            }
            return "OK: unsigned.apk created (" + count + " entries, " + out.length() + " bytes)";
        } catch (Throwable e) {
            return "ERROR: " + e;
        }
    }

    private static void writeStored(ZipOutputStream zos, CountingOut c, String name, byte[] data)
            throws IOException {
        zos.closeEntry(); // so the byte counter is up to date
        ZipEntry z = new ZipEntry(name);
        z.setMethod(ZipEntry.STORED);
        z.setSize(data.length);
        z.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        z.setCrc(crc.getValue());

        // Local header = 30 bytes + name + extra. Pad extra so data starts on a 4-byte boundary.
        long dataStart = c.count + 30 + name.getBytes(StandardCharsets.UTF_8).length;
        int pad = (int) ((4 - (dataStart % 4)) % 4);
        if (pad != 0) {
            int len = pad + 4;
            byte[] extra = new byte[len];
            extra[0] = 0x35;
            extra[1] = (byte) 0xD9;
            extra[2] = (byte) (len - 4);
            z.setExtra(extra);
        }
        zos.putNextEntry(z);
        zos.write(data);
        zos.closeEntry();
    }

    private static void writeDeflated(ZipOutputStream zos, String name, byte[] data)
            throws IOException {
        zos.closeEntry();
        ZipEntry z = new ZipEntry(name);
        z.setMethod(ZipEntry.DEFLATED);
        zos.putNextEntry(z);
        zos.write(data);
        zos.closeEntry();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int r;
        while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
        return bo.toByteArray();
    }

    private static class CountingOut extends FilterOutputStream {
        long count;

        CountingOut(OutputStream o) {
            super(o);
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            count++;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            count += len;
        }
    }
}
