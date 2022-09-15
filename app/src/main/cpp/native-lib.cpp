#include <jni.h>
#include "main/FFMpegPlayer.h"

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
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeSeek(JNIEnv *env, jobject thiz, jlong handle,
                                                   jdouble position) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player == nullptr) {
        return false;
    }
    return player->seek(position);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeSetMute(JNIEnv *env, jobject thiz, jlong handle,
                                                      jboolean mute) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->setMute(mute);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativeResume(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->resume();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_ffmpegdemo_player_FFPlayer_nativePause(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->pause();
    }
}