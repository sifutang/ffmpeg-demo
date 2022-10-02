#ifndef FFMPEGDEMO_FFMPEGPLAYER_H
#define FFMPEGDEMO_FFMPEGPLAYER_H

#include <jni.h>
#include <string>
#include <sstream>
#include <ctime>
#include <pthread.h>
#include <thread>
#include "../utils/Logger.h"
#include "../decoder/VideoDecoder.h"
#include "../decoder/AudioDecoder.h"
#include "../base/AVPacketQueue.h"

extern "C" {
#include "../vendor/ffmpeg/libavutil/avutil.h"
#include "../vendor/ffmpeg/libavutil/frame.h"
#include "../vendor/ffmpeg/libavutil/time.h"
#include "../vendor/ffmpeg/libavformat/avformat.h"
#include "../vendor/ffmpeg/libavcodec/avcodec.h"
#include "../vendor/ffmpeg/libavcodec/jni.h"
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

enum PlayerState {
    UNKNOWN,
    PREPARE,
    START,
    PLAYING,
    PAUSE,
    STOP
};

enum Filter {
    GRID = 0
};

class FFMpegPlayer {

public:

    FFMpegPlayer();

    ~FFMpegPlayer();

    void init(JNIEnv *env, jobject javaObj);

    bool prepare(JNIEnv *env, std::string &path, jobject surface);

    void start();

    void resume();

    void pause();

    void stop();

    void setMute(bool mute);

    void setFilter(int filter, bool enable);

    bool seek(double timeS);

    double getDuration();

private:
    JavaVM *mJvm = nullptr;
    PlayerJniContext mPlayerJni{};

    volatile PlayerState mPlayerState = UNKNOWN;

    bool mHasAbort = false;
    bool mIsMute = false;
    bool mIsReadEof = false;
    bool mIsSeek = false;

    pthread_cond_t mCond{};
    pthread_mutex_t mMutex{};

    volatile double mVideoSeekPos = -1;
    volatile double mAudioSeekPos = -1;

    std::thread *mReadPacketThread = nullptr;

    std::thread *mVideoThread = nullptr;
    AVPacketQueue *mVideoPacketQueue = nullptr;
    VideoDecoder *mVideoDecoder = nullptr;

    std::thread *mAudioThread = nullptr;
    AVPacketQueue *mAudioPacketQueue = nullptr;
    AudioDecoder *mAudioDecoder = nullptr;

    AVFormatContext *mFtx = nullptr;

    void doRender(JNIEnv *env, AVFrame *avFrame, bool isEnd = false);

    int readAvPacketToQueue();

    bool pushPacketToQueue(AVPacket *packet, AVPacketQueue *queue) const;

    void ReadPacketLoop();

    void VideoDecodeLoop();

    void AudioDecodeLoop();

    void wait();

    void wakeup();

    void updatePlayerState(PlayerState state);
};


#endif //FFMPEGDEMO_FFMPEGPLAYER_H
