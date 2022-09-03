#ifndef FFMPEGDEMO_VIDEODECODER_H
#define FFMPEGDEMO_VIDEODECODER_H

#include <jni.h>
#include <functional>
#include <string>
#include "BaseDecoder.h"

extern "C" {
#include "libavcodec/mediacodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}

class VideoDecoder: public BaseDecoder {

public:
    VideoDecoder(int index, AVFormatContext *ftx, int useHw = false);
    ~VideoDecoder();

    int getWidth() const;

    int getHeight() const;

    void setSurface(jobject surface);

    virtual double getDuration() override;

    virtual bool prepare() override;

    virtual int decode(AVPacket *packet) override;

    virtual void avSync(AVFrame *frame) override;

    virtual void release() override;

private:
    int mWidth = -1;
    int mHeight = -1;

    int64_t mStartTime = -1;

    bool mUseHwDecode = false;

    jobject mSurface = nullptr;

    AVBufferRef *mHwDeviceCtx = nullptr;

    const AVCodec *mVideoCodec = nullptr;

    AVCodecContext *mVideoCodecContext = nullptr;

    AVMediaCodecContext *mMediaCodecContext = nullptr;

    AVFrame *mAvFrame = nullptr;

    SwsContext *mSwsContext = nullptr;

};


#endif //FFMPEGDEMO_VIDEODECODER_H
