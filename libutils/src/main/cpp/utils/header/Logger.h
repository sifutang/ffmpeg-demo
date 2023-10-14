#ifndef FFMPEGDEMO_LOGGER_H
#define FFMPEGDEMO_LOGGER_H

#include<android/log.h>

#define LOGD(FORMAT,...) __android_log_print(ANDROID_LOG_DEBUG, "NativeLog",FORMAT, ##__VA_ARGS__);
#define LOGI(FORMAT,...) __android_log_print(ANDROID_LOG_INFO, "NativeLog",FORMAT, ##__VA_ARGS__);
#define LOGW(FORMAT,...) __android_log_print(ANDROID_LOG_WARN, "NativeLog",FORMAT, ##__VA_ARGS__);
#define LOGE(FORMAT,...) __android_log_print(ANDROID_LOG_ERROR, "NativeLog",FORMAT, ##__VA_ARGS__);

#endif //FFMPEGDEMO_LOGGER_H
