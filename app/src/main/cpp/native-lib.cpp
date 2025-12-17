#include <jni.h>
#include <vector>
#include "yin.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_malesko_smt_audio_NativeYin_nativeCreate(JNIEnv*, jobject, jint sampleRate, jint bufferSize) {
    auto* yin = new Yin(sampleRate, bufferSize);
    return reinterpret_cast<jlong>(yin);
}

extern "C" JNIEXPORT void JNICALL
Java_com_malesko_smt_audio_NativeYin_nativeDestroy(JNIEnv*, jobject, jlong handle) {
auto* yin = reinterpret_cast<Yin*>(handle);
delete yin;
}

extern "C" JNIEXPORT jfloat JNICALL
        Java_com_malesko_smt_audio_NativeYin_nativeProcess(JNIEnv* env, jobject, jlong handle, jfloatArray frame_) {
auto* yin = reinterpret_cast<Yin*>(handle);
if (!yin) return -1.0f;

jsize n = env->GetArrayLength(frame_);
std::vector<float> frame(static_cast<size_t>(n));
env->GetFloatArrayRegion(frame_, 0, n, frame.data());

return yin->getPitch(frame);
}
