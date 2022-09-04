#ifndef FFMPEGDEMO_BASEDECODER_H
#define FFMPEGDEMO_BASEDECODER_H

#include <functional>
#include <string>

extern "C" {
#include "libavcodec/avcodec.h"
#include "libavformat/avformat.h"
#include "libavutil/time.h"
}

#define DELAY_THRESHOLD 100 // 100ms

class BaseDecoder {

public:
    BaseDecoder(int index, AVFormatContext *ftx);
    ~BaseDecoder();

    virtual double getDuration();

    virtual bool prepare();

    virtual int decode(AVPacket *packet);

    virtual void avSync(AVFrame *frame);

    virtual int seek(double pos);

    virtual void flush();

    virtual void release();

    int getStreamIndex() const;

    AVRational getTimebase() const;

    void setErrorMsgListener(std::function<void(int, std::string &)> listener);

    void setOnFrameArrived(std::function<void(AVFrame *)> listener);

protected:
    AVFormatContext *mFtx = nullptr;

    AVCodecContext *mCodecContext = nullptr;

    AVRational mTimeBase{};

    double mDuration = 0;

    std::function<void(int, std::string &)> mErrorMsgListener = nullptr;

    std::function<void(AVFrame *)> mOnFrameArrivedListener = nullptr;

private:
    int mStreamIndex = -1;
};

#endif //FFMPEGDEMO_BASEDECODER_H
