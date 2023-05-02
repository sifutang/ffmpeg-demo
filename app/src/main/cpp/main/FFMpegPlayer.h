#ifndef FFMPEGDEMO_FFMPEGPLAYER_H
#define FFMPEGDEMO_FFMPEGPLAYER_H

#include <jni.h>
#include <string>
#include <sstream>
#include <ctime>
#include <thread>
#include <memory.h>
#include "../utils/Logger.h"
#include "../utils/MutexObj.h"
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
    jmethodID onPlayCompleted;

    void reset() {
        instance = nullptr;
        onVideoPrepared = nullptr;
        onAudioPrepared = nullptr;
        onVideoFrameArrived = nullptr;
        onAudioFrameArrived = nullptr;
        onPlayCompleted = nullptr;
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

    int getRotate();

private:
    JavaVM *mJvm = nullptr;
    PlayerJniContext mPlayerJni{};

    volatile PlayerState mPlayerState = UNKNOWN;

    bool mHasAbort = false;
    bool mIsMute = false;
    bool mIsSeek = false;

    std::shared_ptr<MutexObj> mMutexObj = nullptr;

    volatile double mVideoSeekPos = -1;
    volatile double mAudioSeekPos = -1;

    std::thread *mReadPacketThread = nullptr;

    std::thread *mVideoThread = nullptr;
    std::shared_ptr<AVPacketQueue> mVideoPacketQueue = nullptr;
    std::shared_ptr<VideoDecoder> mVideoDecoder = nullptr;

    std::thread *mAudioThread = nullptr;
    std::shared_ptr<AVPacketQueue> mAudioPacketQueue = nullptr;
    std::shared_ptr<AudioDecoder> mAudioDecoder = nullptr;

    AVFormatContext *mFtx = nullptr;

    void doRender(JNIEnv *env, AVFrame *avFrame);

    int readAvPacketToQueue();

    bool pushPacketToQueue(AVPacket *packet, const std::shared_ptr<AVPacketQueue>& queue) const;

    void ReadPacketLoop();

    void VideoDecodeLoop();

    void AudioDecodeLoop();

    void updatePlayerState(PlayerState state);
};


#endif //FFMPEGDEMO_FFMPEGPLAYER_H
