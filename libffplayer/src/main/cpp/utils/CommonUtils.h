#ifndef FFMPEGDEMO_COMMONUTILS_H
#define FFMPEGDEMO_COMMONUTILS_H

#include <sys/time.h>

static int64_t getCurrentTimeMs() {
    struct timeval time;
    gettimeofday(&time, nullptr);
    return time.tv_sec * 1000.0 + time.tv_usec / 1000.0;
}

#endif //FFMPEGDEMO_COMMONUTILS_H
