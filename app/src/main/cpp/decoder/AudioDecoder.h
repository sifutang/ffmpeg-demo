#ifndef FFMPEGDEMO_AUDIODECODER_H
#define FFMPEGDEMO_AUDIODECODER_H

#include <functional>
#include <string>
#include "../utils/ImageDef.h"
#include "BaseDecoder.h"

extern "C" {
#include "../vendor/ffmpeg/libswresample/swresample.h"
#include "../vendor/ffmpeg/libavutil/imgutils.h"
}

class AudioDecoder: public BaseDecoder {

public:
    AudioDecoder(int index, AVFormatContext *ftx);
    ~AudioDecoder();

    virtual double getDuration() override;

    virtual bool prepare() override;

    virtual int decode(AVPacket *packet) override;

    virtual void avSync(AVFrame *frame) override;

    virtual int seek(double pos) override;

    virtual void release() override;

    int64_t getTimestamp() const;

    int64_t mCurTimeStampMs = 0;

    bool mNeedFlushRender = false;

    int mDataSize = 0;
    uint8_t *mAudioBuffer = nullptr;

private:
    int64_t mStartTimeMsForSync = -1;

    bool mFixStartTime = false;

    const AVCodec *mAudioCodec = nullptr;

    SwrContext *mSwrContext = nullptr;

    void updateTimestamp(AVFrame *frame);

    int resample(AVFrame *frame);
};


#endif //FFMPEGDEMO_AUDIODECODER_H
