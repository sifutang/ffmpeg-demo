#ifndef FFMPEGDEMO_VIDEODECODER_H
#define FFMPEGDEMO_VIDEODECODER_H

#include <jni.h>
#include <functional>
#include <string>

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libavcodec/mediacodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}

class VideoDecoder {

public:
    VideoDecoder(int index, AVFormatContext *ftx, int useHw = false);
    ~VideoDecoder();

    int getWidth() const;

    int getHeight() const;

    int getStreamIndex() const;

    double getDuration() const;

    bool prepare(jobject surface);

    int decode(AVPacket *packet);

    void release();

    void setOnFrameArrived(std::function<void(AVFrame *)> listener);

    void setErrorMsgListener(std::function<void(int, std::string &)> listener);

private:
    int mWidth = -1;
    int mHeight = -1;

    int mVideoIndex = -1;

    int64_t mDuration = 0;

    bool mUseHwDecode = false;

    AVFormatContext *mFtx = nullptr;

    AVBufferRef *mHwDeviceCtx = nullptr;

    const AVCodec *mVideoCodec = nullptr;

    AVCodecContext *mVideoCodecContext = nullptr;

    AVMediaCodecContext *mMediaCodecContext = nullptr;

    AVFrame *mAvFrame = nullptr;

    SwsContext *mSwsContext = nullptr;

    std::function<void(AVFrame *)> mOnFrameArrivedListener = nullptr;

    std::function<void(int, std::string &)> mErrorMsgListener = nullptr;

};


#endif //FFMPEGDEMO_VIDEODECODER_H
