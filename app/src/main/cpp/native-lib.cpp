#include <jni.h>
#include <string>
#include "aalam_compiler.h"

struct BuildCtx {
    JNIEnv* env;
    std::string out;
};

static void onLog(const char* msg, void* user) {
    auto* c = static_cast<BuildCtx*>(user);
    c->out += msg;
    c->out += "\n";
}

static int onStep(const char* step, const char* dir, void* user) {
    auto* c = static_cast<BuildCtx*>(user);
    JNIEnv* env = c->env;

    jclass cls = env->FindClass("com/aalamstudio/compiler/NativeBridge");
    if (!cls) { env->ExceptionClear(); c->out += "ERROR: NativeBridge not found\n"; return 1; }
    jmethodID m = env->GetStaticMethodID(cls, "runJavaStep",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
    if (!m) { env->ExceptionClear(); c->out += "ERROR: runJavaStep not found\n"; return 1; }

    jstring js = env->NewStringUTF(step);
    jstring jd = env->NewStringUTF(dir);
    jstring res = (jstring) env->CallStaticObjectMethod(cls, m, js, jd);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        c->out += "ERROR: Java step crashed\n";
        return 1;
    }
    if (!res) { c->out += "ERROR: Java step returned nothing\n"; return 1; }

    const char* r = env->GetStringUTFChars(res, nullptr);
    std::string s(r);
    env->ReleaseStringUTFChars(res, r);

    c->out += s + "\n";
    return s.rfind("OK", 0) == 0 ? 0 : 1;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_aalamstudio_compiler_NativeBridge_build(JNIEnv* env, jclass,
                                                 jstring dir, jstring target) {
    const char* d = env->GetStringUTFChars(dir, nullptr);
    const char* t = env->GetStringUTFChars(target, nullptr);

    BuildCtx ctx;
    ctx.env = env;
    int code = aalam_build(d, t, onLog, onStep, &ctx);
    ctx.out += "Result code: " + std::to_string(code);

    env->ReleaseStringUTFChars(dir, d);
    env->ReleaseStringUTFChars(target, t);
    return env->NewStringUTF(ctx.out.c_str());
}
