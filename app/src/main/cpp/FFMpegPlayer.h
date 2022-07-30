#ifndef FFMPEGDEMO_FFMPEGPLAYER_H
#define FFMPEGDEMO_FFMPEGPLAYER_H

#include <jni.h>
#include <string>
#include <sstream>
#include <ctime>
#include <pthread.h>
#include "Logger.h"
#include "./decoder/VideoDecoder.h"

extern "C" {
#include "libavutil/avutil.h"
#include "libavutil/frame.h"
#include "libavutil/time.h"
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
}

class FFMpegPlayer {

public:

    FFMpegPlayer();

    ~FFMpegPlayer();

    bool prepare(JNIEnv *env, std::string &path);

    void start(JNIEnv *env);

    void stop();

    void registerPlayerListener(JNIEnv *env, jobject listener);

    void release();

private:
    jmethodID onCallPrepared = nullptr;
    jmethodID onCallError = nullptr;
    jmethodID onCallCompleted = nullptr;
    jmethodID onFrameArrived = nullptr;
    jobject jPlayerListenerObj = nullptr;

    bool mIsRunning = false;

    int64_t mStartTime = -1;

    pthread_cond_t mCond;
    pthread_mutex_t mMutex;

    VideoDecoder *mVideoDecoder = nullptr;

    AVFormatContext *mFtx = nullptr;

    void sync(AVFrame *avFrame);

    void doRender(JNIEnv *env, AVFrame *avFrame);
};


#endif //FFMPEGDEMO_FFMPEGPLAYER_H
