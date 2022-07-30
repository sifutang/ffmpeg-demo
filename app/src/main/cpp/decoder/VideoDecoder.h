#ifndef FFMPEGDEMO_VIDEODECODER_H
#define FFMPEGDEMO_VIDEODECODER_H

#include <jni.h>
#include <functional>
#include <string>

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}

class VideoDecoder {

public:
    VideoDecoder(int index, AVFormatContext *ftx);
    ~VideoDecoder();

    int getWidth() const;

    int getHeight() const;

    int getStreamIndex() const;

    double getDuration() const;

    bool prepare(AVCodecParameters *codecParameters);

    int decode(AVPacket *packet);

    void release();

    void setOnFrameArrived(std::function<void(AVFrame *)> listener);

    void setErrorMsgListener(std::function<void(int, std::string &)> listener);

private:
    int mWidth = -1;
    int mHeight = -1;

    int mVideoIndex = -1;

    double mDuration = 0;

    AVFormatContext *mFtx = nullptr;

    const AVCodec *mVideoCodec = nullptr;

    AVCodecContext *mVideoCodecContext = nullptr;

    SwsContext *mSwsContext = nullptr;

    std::function<void(AVFrame *)> mOnFrameArrivedListener = nullptr;

    std::function<void(int, std::string &)> mErrorMsgListener = nullptr;

};


#endif //FFMPEGDEMO_VIDEODECODER_H
