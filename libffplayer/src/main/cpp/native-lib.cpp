#include <jni.h>
#include "main/FFMpegPlayer.h"
#include "utils/TraceUtils.h"
#include "base/nativehelper/ScopedUtfChars.h"

extern "C"
JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGI("ffmpegdemo JNI_OnLoad")
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    TraceUtils::init();
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeInit(JNIEnv *env, jobject thiz) {
    ATRACE_CALL();
    auto *player = new FFMpegPlayer();
    player->init(env, thiz);
    return reinterpret_cast<long>(player);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativePrepare(JNIEnv *env, jobject thiz, jlong handle,
                                                      jstring path, jobject surface) {
    ATRACE_CALL();
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    ScopedUtfChars scopedPath(env, path);
    std::string s_path = scopedPath.c_str();
    bool result = false;
    if (player) {
        result = player->prepare(env,s_path, surface);
    }
    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeStart(JNIEnv *env, jobject thiz, jlong handle) {
    ATRACE_CALL();
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->start();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeStop(JNIEnv *env, jobject thiz, jlong handle) {
    ATRACE_CALL();
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->stop();
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeRelease(JNIEnv *env, jobject thiz, jlong handle) {
    ATRACE_CALL();
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    delete player;
}
extern "C"
JNIEXPORT jdouble JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeGetDuration(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player == nullptr) {
        return 0;
    }
    return player->getDuration();
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeSeek(JNIEnv *env, jobject thiz, jlong handle,
                                                   jdouble position) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player == nullptr) {
        return false;
    }
    return player->seek(position);
}
extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeSetMute(JNIEnv *env, jobject thiz, jlong handle,
                                                      jboolean mute) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->setMute(mute);
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeResume(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->resume();
    }
}
extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativePause(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        player->pause();
    }
}

extern "C"
JNIEXPORT jint JNICALL
Java_com_xyq_libffplayer_FFPlayer_nativeGetRotate(JNIEnv *env, jobject thiz, jlong handle) {
    auto *player = reinterpret_cast<FFMpegPlayer *>(handle);
    if (player) {
        return player->getRotate();
    }
    return 0;
}