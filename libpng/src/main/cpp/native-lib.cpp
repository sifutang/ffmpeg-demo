#include <jni.h>
#include <string>
#include <cstdio>
#include "./source/png.h"
#include "header/Logger.h"
#include "ScopedUtfChars.h"

/* Automatically clean up after throwing an exception */
struct PNGAutoClean {
    PNGAutoClean(png_structp p, png_infop i): png_ptr(p), info_ptr(i) {}
    ~PNGAutoClean() {
        png_destroy_read_struct(&png_ptr, &info_ptr, nullptr);
    }
private:
    png_structp png_ptr;
    png_infop info_ptr;
};

extern "C"
JNIEXPORT void JNICALL
Java_com_xyq_libpng_PngReader_read(JNIEnv *env, jobject thiz, jstring path, jobject data) {
    jclass jclazz = env->GetObjectClass(data);
    jfieldID widthFiled = env->GetFieldID(jclazz, "width", "I");
    jfieldID heightFiled = env->GetFieldID(jclazz, "height", "I");
    jfieldID colorTypeFiled = env->GetFieldID(jclazz, "colorType", "I");
    jfieldID bitDepthFiled = env->GetFieldID(jclazz, "bitDepth", "I");

    ScopedUtfChars filePath(env, path);
    png_structp pngPtr;
    png_infop infoPtr;
    FILE* file;

    do {
        file = fopen(filePath.c_str(), "rb");
        if (!file) {
            LOGI("PNG_Helper, open file failed")
            break;
        }

        char png_header[8];
        fread(png_header, 1, 8, file);
        if (png_sig_cmp((png_bytep) png_header, 0, 8)) {
            LOGE("PNG_Helper, not a png file")
            break;
        }

        pngPtr = png_create_read_struct(PNG_LIBPNG_VER_STRING, nullptr, nullptr, nullptr);
        infoPtr = png_create_info_struct(pngPtr);
        PNGAutoClean autoClean(pngPtr, infoPtr);
        if (setjmp(png_jmpbuf(pngPtr))) {
            LOGE("PNG_Helper, Failed to read the PNG file")
            break;
        }

        auto warningFunc = [](png_structp pngPtr, png_const_charp errMsg) {
            LOGW("PNG_Helper: read the PNG file warning: %s", errMsg)
        };
        auto errMsgFunc = [](png_structp pngPtr, png_const_charp errMsg) {
            LOGE("PNG_Helper: Failed to read the PNG file: %s", errMsg)
        };
        png_set_error_fn(pngPtr, nullptr, errMsgFunc, warningFunc);

        png_init_io(pngPtr, file);
        png_set_sig_bytes(pngPtr, 8);
        int transforms = PNG_TRANSFORM_STRIP_16 |
                                          PNG_TRANSFORM_GRAY_TO_RGB |
                                          PNG_TRANSFORM_PACKING |
                                          PNG_TRANSFORM_EXPAND;
        png_read_png(pngPtr, infoPtr, transforms, nullptr);

        auto width = png_get_image_width(pngPtr, infoPtr);
        auto height = png_get_image_height(pngPtr, infoPtr);
        auto colorType = png_get_color_type(pngPtr, infoPtr);
        auto bitDepth = png_get_bit_depth(pngPtr, infoPtr);
        LOGI("PNG_Helper, width:%d, height:%d, colorType:%d, bitDepth:%d", width, height, colorType, bitDepth)

        env->SetIntField(data, bitDepthFiled, (jint) bitDepth);
        env->SetIntField(data, colorTypeFiled, (jint) colorType);
        env->SetIntField(data, heightFiled, (jint) height);
        env->SetIntField(data, widthFiled, (jint) width);

        if (colorType == PNG_COLOR_TYPE_RGB_ALPHA) {
            jmethodID allocateFrame = env->GetMethodID(jclazz, "allocateFrameFromNative","(II)Ljava/nio/ByteBuffer;");
            jobject jByteBuffer = env->CallObjectMethod(data, allocateFrame, (jint) width, (jint) height);
            auto *buffer = (uint8_t *) env->GetDirectBufferAddress(jByteBuffer);
            memset(buffer, 0, width * height * 4);

            png_bytep *row_pointers = png_get_rows(pngPtr, infoPtr);

            int pos = 0, offset = 1;
            for (int row = 0; row < height; row++) {
                for (int col = 0; col < (4 * offset * width); col += (4 * offset)) {
                    buffer[pos++] = row_pointers[row][col + 0 * offset] * row_pointers[row][col + 3 * offset] / 255; // blue
                    buffer[pos++] = row_pointers[row][col + 1 * offset] * row_pointers[row][col + 3 * offset] / 255; // green
                    buffer[pos++] = row_pointers[row][col + 2 * offset] * row_pointers[row][col + 3 * offset] / 255; // red
                    buffer[pos++] = row_pointers[row][col + 3 * offset]; // alpha
                }
            }
        } else {
            LOGE("not impl")
            abort();
        }
    } while (false);

    if (file) {
        fclose(file);
    }
}