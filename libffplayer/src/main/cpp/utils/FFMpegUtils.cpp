#include <jni.h>
#include <memory.h>
#include "../reader/FFVideoReader.h"
#include "../writer/FFVideoWriter.h"
#include "../base/nativehelper/ScopedUtfChars.h"
#include "../base/nativehelper/ScopedPrimitiveArray.h"
#include "Logger.h"

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libffplayer_utils_FFMpegUtils_getVideoFramesCore(JNIEnv *env, jobject thiz,
                                                             jstring path, jint width, jint height,
                                                             jboolean precise, jobject cb) {
    // path
    ScopedUtfChars scopedPath(env, path);
    std::string s_path = scopedPath.c_str();

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
    jmethodID onProgress = env->GetMethodID(jclazz, "onProgress", "(Ljava/nio/ByteBuffer;DIIII)Z");
    jmethodID onFetchEnd = env->GetMethodID(jclazz, "onFetchEnd", "()V");

    auto *reader = new FFVideoReader();
    reader->setDiscardType(DISCARD_NONREF);
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
    } else if (width <= 0) { // scale base height
        height += height % 2;
        if (height > videoHeight) {
            height = videoHeight;
        }
        width = (jint)(1.0 * height * videoWidth / videoHeight);
    }
    LOGI("video size: %dx%d, get frame size: %dx%d", videoWidth, videoHeight, width, height)

    auto timestamps = (jdoubleArray)env->CallObjectMethod(cb, onFetchStart, reader->getDuration());
    ScopedDoubleArrayRW tsArr(env, timestamps);
    int size = env->GetArrayLength(timestamps);
    LOGI("timestamps size: %d", size)

    int rotate = reader->getRotate();

    for (int i = 0; i < size; i++) {
        jobject jByteBuffer = env->CallObjectMethod(thiz, allocateFrame, width, height);
        auto *buffer = (uint8_t *)env->GetDirectBufferAddress(jByteBuffer);
        memset(buffer, 0, width * height * 4);
        reader->getFrame((int64_t)tsArr[i], width, height, buffer, precise);
        jboolean abort = !env->CallBooleanMethod(cb, onProgress, jByteBuffer, (jdouble)tsArr[i], width, height, rotate, i);
        if (abort) {
            LOGE("onProgress abort")
            break;
        }
    }

    reader->release();
    delete reader;

    env->CallVoidMethod(cb, onFetchEnd);
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_xyq_libffplayer_utils_FFMpegUtils_nativeExportGif(JNIEnv *env, jobject thiz,
                                                          jstring video_path, jstring output) {
    ScopedUtfChars input_path(env, video_path);
    ScopedUtfChars output_path(env, output);
    std::string inputPath = input_path.c_str();
    std::string outputPath = output_path.c_str();

    // init reader
    auto reader = std::make_unique<FFVideoReader>();
    reader->setDiscardType(DISCARD_NONKEY);
    reader->init(inputPath);

    // init writer
    auto codecParam = reader->getCodecParameters();
    CompileSettings compileSettings;
    compileSettings.width = codecParam->width;
    compileSettings.height = codecParam->height;
    compileSettings.encodeType = ENCODE_TYPE_GIF;
    compileSettings.pixelFormat = PIX_FMT_RGB8;
    compileSettings.mediaType = MEDIA_TYPE_VIDEO;
    compileSettings.bitRate = codecParam->bit_rate;
    compileSettings.gopSize = 0;
    compileSettings.fps = 10;

    auto writer = std::make_unique<FFVideoWriter>();
    writer->init(outputPath, compileSettings);

    // loop encode
    int ret = 0;
    while (true) {
        reader->getNextFrame([&](AVFrame *avFrame) {
            if (avFrame != nullptr) {
                writer->encode(avFrame);
            } else {
                ret = -1;
            }
        });
        if (ret < 0) {
            break;
        }
    }
    writer->signalEof();

    return true;
}