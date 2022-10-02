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

    auto *reader = new FFVideoReader(s_path);
    // todo w/h scale not impl
    if (width <= 0 || height <= 0) {
        width = reader->getMediaInfo().width;
        height = reader->getMediaInfo().height;
    }

    auto timestamps = (jdoubleArray)env->CallObjectMethod(cb, onFetchStart, reader->getDuration());
    jdouble *tsArr = env->GetDoubleArrayElements(timestamps, nullptr);
    int size = env->GetArrayLength(timestamps);
    LOGE("timestamps size: %d", size)

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