#ifndef FFMPEGDEMO_FFMPEGPLAYER_H
#define FFMPEGDEMO_FFMPEGPLAYER_H

#include <jni.h>
#include <string>
#include <sstream>
#include <ctime>
#include <pthread.h>
#include <thread>
#include "utils/Logger.h"
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

typedef struct PlayerJniContext {
    jobject instance;
    jmethodID onVideoPrepared;
    jmethodID onAudioPrepared;
    jmethodID onVideoFrameArrived;
    jmethodID onAudioFrameArrived;

    void reset() {
        instance = nullptr;
        onVideoPrepared = nullptr;
        onAudioPrepared = nullptr;
        onVideoFrameArrived = nullptr;
        onAudioFrameArrived = nullptr;
    }

} PlayerJniContext;

class FFMpegPlayer {

public:

    FFMpegPlayer();

    ~FFMpegPlayer();

    void init(JNIEnv *env, jobject javaObj);

    bool prepare(JNIEnv *env, std::string &path, jobject surface);

    void start();

    void stop();

private:
    JavaVM *mJvm = nullptr;
    PlayerJniContext mPlayerJni{};

    bool mIsRunning = false;
    bool mHasAbort = false;
    bool mIsAudioEOF = false;

    pthread_cond_t mCond{};
    pthread_mutex_t mMutex{};

    std::thread *mVideoThread = nullptr;
    AVPacketQueue *mVideoPacketQueue = nullptr;
    VideoDecoder *mVideoDecoder = nullptr;

    std::thread *mAudioThread = nullptr;
    AVPacketQueue *mAudioPacketQueue = nullptr;
    AudioDecoder *mAudioDecoder = nullptr;

    AVFormatContext *mFtx = nullptr;

    void doRender(JNIEnv *env, AVFrame *avFrame);

    void VideoDecodeLoop();

    void AudioDecodeLoop();
};


#endif //FFMPEGDEMO_FFMPEGPLAYER_H
