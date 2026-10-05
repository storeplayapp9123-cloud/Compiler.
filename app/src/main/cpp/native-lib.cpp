#include <jni.h>
#include <string>
#include "aalam_compiler.h"

static void onLog(const char* msg, void* user) {
    auto* out = static_cast<std::string*>(user);
    out->append(msg);
    out->append("\n");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aalamstudio_compiler_NativeBridge_build(JNIEnv* env, jclass,
                                                 jstring dir, jstring target) {
    const char* d = env->GetStringUTFChars(dir, nullptr);
    const char* t = env->GetStringUTFChars(target, nullptr);

    std::string out;
    int code = aalam_build(d, t, onLog, &out);
    out += "Result code: " + std::to_string(code);

    env->ReleaseStringUTFChars(dir, d);
    env->ReleaseStringUTFChars(target, t);
    return env->NewStringUTF(out.c_str());
}
