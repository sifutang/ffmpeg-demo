#include <jni.h>

#include "../reader/FFVideoReader.h"
#include "Logger.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_ffmpegdemo_utils_FFMpegUtils_getVideoFramesCore(JNIEnv *env, jobject thiz,
                                                             jstring path, jint width, jint height,
                                                             jobject cb) {
    // path
    const char *c_path = env->GetStringUTFChars(path, nullptr);
    std::string s_path = c_path;

    // frameAllocator
    jclass jclazz = env->GetObjectClass(thiz);
    assert(jclazz != nullptr);

    jmethodID allocateFrame = env->GetMethodID(jclazz, "allocateFrame",
                                               "(II)Ljava/nio/ByteBuffer;");
    assert(allocateFrame != nullptr);

    // callback
    jclazz = env->GetObjectClass(cb);
    assert(jclazz != nullptr);

    jmethodID onFetchStart = env->GetMethodID(jclazz, "onFetchStart", "(D)[D");
    jmethodID onProgress = env->GetMethodID(jclazz, "onProgress", "(Ljava/nio/ByteBuffer;DIII)Z");
    jmethodID onFetchEnd = env->GetMethodID(jclazz, "onFetchEnd", "()V");

    auto *reader = new FFVideoReader();
    reader->enableSkipNonRefFrame(true);
    reader->init(s_path);

    int videoWidth = reader->getMediaInfo().width;
    int videoHeight = reader->getMediaInfo().height;
    if (width <= 0 && height <= 0) {
        width = videoWidth;
        height = videoHeight;
    } else if (width > 0 && height <= 0) { // scale base width
        width += width % 2;
        if (width > videoWidth) {
            width = videoWidth;
        }
        height = (jint)(1.0 * width * videoHeight / videoWidth);
        height += height % 2;
    } else if (height > 0 && width <= 0) { // scale base height
        height += height % 2;
        if (height > videoHeight) {
            height = videoHeight;
        }
        width = (jint)(1.0 * height * videoWidth / videoHeight);
    }
    LOGI("video size: %dx%d, get frame size: %dx%d", videoWidth, videoHeight, width, height)

    auto timestamps = (jdoubleArray)env->CallObjectMethod(cb, onFetchStart, reader->getDuration());
    jdouble *tsArr = env->GetDoubleArrayElements(timestamps, nullptr);
    int size = env->GetArrayLength(timestamps);
    LOGI("timestamps size: %d", size)

    for (int i = 0; i < size; i++) {
        jobject jByteBuffer = env->CallObjectMethod(thiz, allocateFrame, width, height);
        auto *buffer = (uint8_t *)env->GetDirectBufferAddress(jByteBuffer);
        memset(buffer, 0, width * height * 4);

        auto pts = (int64_t)tsArr[i];
        reader->getFrame(pts, width, height, buffer);
        jboolean abort = !env->CallBooleanMethod(cb, onProgress, jByteBuffer, tsArr[i], width, height, i);
        if (abort) {
            LOGE("onProgress abort")
            break;
        }
    }

    reader->release();
    delete reader;

    env->CallVoidMethod(cb, onFetchEnd);

    if (c_path != nullptr) {
        env->ReleaseStringUTFChars(path, c_path);
    }
    if (tsArr != nullptr) {
        env->ReleaseDoubleArrayElements(timestamps, tsArr, 0);
    }
}