#ifndef FFMPEGDEMO_BASEDECODER_H
#define FFMPEGDEMO_BASEDECODER_H

#include <functional>
#include <string>

extern "C" {
#include "../vendor/ffmpeg//libavcodec/avcodec.h"
#include "../vendor/ffmpeg/libavformat/avformat.h"
#include "../vendor/ffmpeg/libavutil/time.h"
}

#define DELAY_THRESHOLD 100 // 100ms

class BaseDecoder {

public:
    BaseDecoder(int index, AVFormatContext *ftx);
    virtual ~BaseDecoder();

    virtual double getDuration();

    virtual bool prepare();

    virtual int decode(AVPacket *packet);

    virtual void avSync(AVFrame *frame);

    virtual int seek(double pos);

    virtual void flush();

    virtual void release();

    bool isNeedResent() const;

    int getStreamIndex() const;

    void needFixStartTime();

    void setErrorMsgListener(std::function<void(int, std::string &)> listener);

    void setOnFrameArrived(std::function<void(AVFrame *)> listener);

protected:
    AVFormatContext *mFtx = nullptr;

    AVCodecContext *mCodecContext = nullptr;

    AVRational mTimeBase{};

    double mDuration = 0;

    AVFrame *mAvFrame = nullptr;

    std::function<void(int, std::string &)> mErrorMsgListener = nullptr;

    std::function<void(AVFrame *frame)> mOnFrameArrivedListener = nullptr;

    bool mNeedResent = false;

    int64_t mRetryReceiveCount = 7;

    bool mFixStartTime = false;

private:
    int mStreamIndex = -1;
};

#endif //FFMPEGDEMO_BASEDECODER_H
