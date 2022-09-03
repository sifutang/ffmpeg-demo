#ifndef FFMPEGDEMO_AUDIODECODER_H
#define FFMPEGDEMO_AUDIODECODER_H

#include <functional>
#include <string>
#include "../utils/ImageDef.h"
#include "BaseDecoder.h"

extern "C" {
#include "libswresample/swresample.h"
#include "libavutil/imgutils.h"
}

class AudioDecoder: public BaseDecoder {

public:
    AudioDecoder(int index, AVFormatContext *ftx);
    ~AudioDecoder();

    virtual double getDuration() override;

    virtual bool prepare() override;

    virtual int decode(AVPacket *packet) override;

    virtual void avSync(AVFrame *frame) override;

    virtual void release() override;

    int64_t mLastPts = 0;
    int mDataSize = 0;
    uint8_t *mAudioBuffer = nullptr;

private:
    int64_t mStartTime = -1;

    const AVCodec *mAudioCodec = nullptr;

    AVCodecContext *mAudioCodecContext = nullptr;

    AVFrame *mAvFrame = nullptr;

    SwrContext *mSwrContext = nullptr;
};


#endif //FFMPEGDEMO_AUDIODECODER_H
