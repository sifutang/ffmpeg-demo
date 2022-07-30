#ifndef FFMPEGDEMO_LOGGER_H
#define FFMPEGDEMO_LOGGER_H

#include <android/log.h>

#define LOGI(FORMAT,...) __android_log_print(ANDROID_LOG_INFO, "NativeLog",FORMAT, ##__VA_ARGS__);
#define LOGE(FORMAT,...) __android_log_print(ANDROID_LOG_ERROR, "NativeLog",FORMAT, ##__VA_ARGS__);

#endif //FFMPEGDEMO_LOGGER_H
