package com.aalamstudio.compiler;

import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;

import java.io.File;
import java.util.Collections;

/** Step 6: unsigned.apk -> signed.apk (v1 + v2 signature, built-in debug key). */
public class SignRunner {

    public static String run(String projectDir) {
        try {
            File build = new File(projectDir, "build");
            File unsigned = new File(build, "unsigned.apk");
            File signed = new File(build, "signed.apk");
            if (!unsigned.exists()) return "ERROR: unsigned.apk missing";
            signed.delete();

            ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder(
                    "aalam", DebugKey.privateKey(),
                    Collections.singletonList(DebugKey.certificate())).build();

            new ApkSigner.Builder(Collections.singletonList(cfg))
                    .setInputApk(unsigned)
                    .setOutputApk(signed)
                    .setMinSdkVersion(24)
                    .setV1SigningEnabled(true)
                    .setV2SigningEnabled(true)
                    .setV3SigningEnabled(false)
                    .build()
                    .sign();

            ApkVerifier.Result r = new ApkVerifier.Builder(signed)
                    .setMinCheckedPlatformVersion(24)
                    .build()
                    .verify();
            if (!r.isVerified()) return "ERROR: signature check failed\n" + r.getErrors();

            return "OK: signed.apk created (" + signed.length() + " bytes, v1+v2 verified)";
        } catch (Throwable e) {
            return "ERROR: " + e;
        }
    }
}
