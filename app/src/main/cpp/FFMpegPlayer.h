#ifndef FFMPEGDEMO_FFMPEGPLAYER_H
#define FFMPEGDEMO_FFMPEGPLAYER_H

#include <jni.h>
#include <string>
#include <sstream>
#include <ctime>
#include <pthread.h>
#include <thread>
#include "Logger.h"
#include "decoder/VideoDecoder.h"
#include "decoder/AudioDecoder.h"
#include "base/AVPacketQueue.h"

extern "C" {
#include "libavutil/avutil.h"
#include "libavutil/frame.h"
#include "libavutil/time.h"
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libavcodec/jni.h"
}

class FFMpegPlayer {

public:

    FFMpegPlayer();

    ~FFMpegPlayer();

    bool prepare(JNIEnv *env, std::string &path, jobject surface);

    void start(JNIEnv *env);

    void stop();

    void registerPlayerListener(JNIEnv *env, jobject listener);

private:
    jmethodID onCallPrepared = nullptr;
    jmethodID onCallError = nullptr;
    jmethodID onCallCompleted = nullptr;
    jmethodID onFrameArrived = nullptr;
    jobject jPlayerListenerObj = nullptr;

    JavaVM *mJvm = nullptr;

    bool mIsRunning = false;
    bool mHasAbort = false;

    int64_t mStartTime = -1;

    pthread_cond_t mCond;
    pthread_mutex_t mMutex;

    std::thread *mVideoThread = nullptr;
    AVPacketQueue *mVideoPacketQueue = nullptr;
    VideoDecoder *mVideoDecoder = nullptr;

    std::thread *mAudioThread = nullptr;
    AVPacketQueue *mAudioPacketQueue = nullptr;
    AudioDecoder *mAudioDecoder = nullptr;

    AVFormatContext *mFtx = nullptr;

    void sync(AVFrame *avFrame);

    void doRender(JNIEnv *env, AVFrame *avFrame);

    void VideoDecodeLoop();

    void AudioDecodeLoop();
};


#endif //FFMPEGDEMO_FFMPEGPLAYER_H
