#include <jni.h>
#include "FFMpegPlayer.h"

extern "C"
JNIEXPORT jlong JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeInit(JNIEnv *env, jobject thiz) {
    auto *player = new FFMpegPlayer();
    player->init(env, thiz);
    return reinterpret_cast<long>(player);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativePrepare(JNIEnv *env, jobject thiz, jlong handle,
                                                      jstring path, jobject surface) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    const char *c_path = env->GetStringUTFChars(path, nullptr);
    std::string s_path = c_path;
    bool result = false;
    if (player) {
        result = player->prepare(env,s_path, surface);
    }
    if (c_path != nullptr) {
        env->ReleaseStringUTFChars(path, c_path);
    }
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeStart(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->start();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeStop(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->stop();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeRelease(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    delete player;
}
extern "C"
JNIEXPORT jdouble JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeGetDuration(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player == nullptr) {
        return 0;
    }
    return player->getDuration();
}