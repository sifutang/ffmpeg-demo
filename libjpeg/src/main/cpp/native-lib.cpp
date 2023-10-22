#include <jni.h>
#include <string>
#include <cstdio>
#include <setjmp.h>

#include "header/Logger.h"
#include "ScopedUtfChars.h"
#include "./source/jpeglib.h"

struct my_error_mgr {
    struct jpeg_error_mgr pub;
    jmp_buf setjmp_buffer;
};

typedef struct my_error_mgr *my_error_ptr;

void my_error_exit(j_common_ptr info) {
    /* info->err really points to a my_error_mgr struct, so coerce pointer */
    auto myErr = (my_error_ptr)info->err;

    /* Always display the message. */
    /* We could postpone this until after returning, if we chose. */
    (*info->err->output_message) (info);

    char error_message[JMSG_LENGTH_MAX];
    (*info->err->format_message)(info, error_message);

    LOGE("JPEG_Helper, my_error_exit: %s", error_message)

    /* Return control to the setjmp point */
    longjmp(myErr->setjmp_buffer, 1);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libjpeg_JpegReader_read(JNIEnv *env, jobject thiz, jstring path, jobject data) {
    jclass jclazz = env->GetObjectClass(data);
    jfieldID widthFiled = env->GetFieldID(jclazz, "width", "I");
    jfieldID heightFiled = env->GetFieldID(jclazz, "height", "I");
    jfieldID colorTypeFiled = env->GetFieldID(jclazz, "colorType", "I");
    jfieldID bitDepthFiled = env->GetFieldID(jclazz, "bitDepth", "I");

    ScopedUtfChars filePath(env, path);

    FILE* file;
    jpeg_decompress_struct info{};
    JSAMPROW *rowDataPtr = nullptr;

    do {
        file = fopen(filePath.c_str(), "rb");
        if (!file) {
            LOGI("JPEG_Helper, open file failed")
            break;
        }

        my_error_mgr jerr{};
        info.err = jpeg_std_error(&jerr.pub);
        jerr.pub.error_exit = my_error_exit;
        if (setjmp(jerr.setjmp_buffer)) {
            break;
        }

        jpeg_create_decompress(&info);

        jpeg_stdio_src(&info, file);

        jpeg_read_header(&info, true);

        if (info.image_width <= 0 || info.image_height <= 0) {
            LOGE("JPEG_Helper, size is invalid")
            break;
        }

        info.out_color_components = 4;
        info.out_color_space = JCS_EXT_RGBA;

        int width = (int)info.image_width;
        int height = (int)info.image_height;
        rowDataPtr = (JSAMPROW *) malloc(sizeof(JSAMPROW) * height);
        if (!rowDataPtr) {
            LOGE("JPEG_Helper, out of memory")
            break;
        }

        jmethodID allocateFrame = env->GetMethodID(jclazz, "allocateFrameFromNative","(II)Ljava/nio/ByteBuffer;");
        jobject jByteBuffer = env->CallObjectMethod(data, allocateFrame, (jint) width, (jint) height);
        auto *rawData = (uint8_t *) env->GetDirectBufferAddress(jByteBuffer);
        memset(rawData, 0, height * width * 4);

        for (int i = 0; i < height; ++i) {
            rowDataPtr[i] = &rawData[i * width * 4];
        }

        jpeg_start_decompress(&info);

        while (info.output_scanline < height) {
            jpeg_read_scanlines(&info, &rowDataPtr[info.output_scanline], 1);
        }

        jpeg_finish_decompress(&info);

        env->SetIntField(data, bitDepthFiled, (jint) info.data_precision);
        env->SetIntField(data, colorTypeFiled, (jint) 0);
        env->SetIntField(data, heightFiled, (jint) height);
        env->SetIntField(data, widthFiled, (jint) width);
    } while (false);

    if (file) {
        fclose(file);
    }
    if (rowDataPtr) {
        free(rowDataPtr);
    }
    jpeg_destroy_decompress(&info);
}