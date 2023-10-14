#ifndef FFMPEGDEMO_VIDEODECODER_H
#define FFMPEGDEMO_VIDEODECODER_H

#include <jni.h>
#include <functional>
#include <string>
#include "BaseDecoder.h"

extern "C" {
#include "../vendor/ffmpeg/libavcodec/mediacodec.h"
#include "../vendor/ffmpeg/libswscale/swscale.h"
#include "../vendor/ffmpeg/libavutil/imgutils.h"
#include "../vendor/ffmpeg/libavutil/display.h"
}

class VideoDecoder: public BaseDecoder {

public:
    VideoDecoder(int index, AVFormatContext *ftx);
    ~VideoDecoder();

    int getWidth() const;

    int getHeight() const;

    void setSurface(jobject surface);

    virtual double getDuration() override;

    virtual bool prepare() override;

    virtual int decode(AVPacket *packet) override;

    virtual void avSync(AVFrame *frame) override;

    virtual int seek(double pos) override;

    virtual void release() override;

    int64_t getTimestamp() const;

    int getRotate();

    AVRational getDisplayAspectRatio();

private:
    int mWidth = -1;
    int mHeight = -1;

    int RETRY_RECEIVE_COUNT = 7;

    int64_t mStartTimeMsForSync = -1;
    int64_t mCurTimeStampMs = 0;

    int64_t mSeekPos = INT64_MAX;
    int64_t mSeekStartTimeMs = -1;
    int64_t mSeekEndTimeMs = -1;

    jobject mSurface = nullptr;

    AVBufferRef *mHwDeviceCtx = nullptr;

    const AVCodec *mVideoCodec = nullptr;

    AVMediaCodecContext *mMediaCodecContext = nullptr;

    SwsContext *mSwsContext = nullptr;

    void updateTimestamp(AVFrame *frame);

    int swsScale(AVFrame *srcFrame, AVFrame *swFrame);
};


#endif //FFMPEGDEMO_VIDEODECODER_H
