#ifndef FFMPEGDEMO_AUDIODECODER_H
#define FFMPEGDEMO_AUDIODECODER_H

#include <functional>
#include <string>

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswresample/swresample.h"
#include "libavutil/imgutils.h"
}

class AudioDecoder {

public:
    AudioDecoder(int index, AVFormatContext *ftx);
    ~AudioDecoder();

    bool prepare();

    int decode(AVPacket *packet);

    void release();

    void setErrorMsgListener(std::function<void(int, std::string &)> listener);

    void setOnFrameArrived(std::function<void(AVFrame *)> listener);

    int getStreamIndex() const;

    int mDataSize = 0;
    uint8_t *mAudioBuffer = nullptr;

private:
    int mAudioIndex = -1;

    AVFormatContext *mFtx = nullptr;

    const AVCodec *mAudioCodec = nullptr;

    AVCodecContext *mAudioCodecContext = nullptr;

    AVFrame *mAvFrame = nullptr;

    SwrContext *mSwrContext = nullptr;

    std::function<void(int, std::string &)> mErrorMsgListener = nullptr;

    std::function<void(AVFrame *)> mOnFrameArrivedListener = nullptr;

};


#endif //FFMPEGDEMO_AUDIODECODER_H
