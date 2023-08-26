//
// Created by 雪月清的随笔 on 14/5/23.
//

#ifndef FFMPEGDEMO_FFVIDEOWRITER_H
#define FFMPEGDEMO_FFVIDEOWRITER_H

extern "C" {
#include "libavformat/avformat.h"
#include "../vendor/ffmpeg//libavcodec/avcodec.h"
#include "../vendor/ffmpeg/libavutil/imgutils.h"
#include "../vendor/ffmpeg/libswscale/swscale.h"
}

#include <string>
#include "../settings/CompileSettings.h"

class FFVideoWriter {
public:
    FFVideoWriter();

    ~FFVideoWriter();

    bool init(std::string &outputPath, CompileSettings &settings);

    void encode(AVFrame *frame);

    void signalEof();

    void release();

private:
    AVFormatContext *mOutputFtx = nullptr;
    AVCodecContext *mCodecContext = nullptr;
    AVCodecParameters *mCodecParameters = nullptr;
    AVStream *mStream = nullptr;

    SwsContext *mSwsContext = nullptr;
    AVFrame *mAvFrame = nullptr;

    int mFrameIndex = 0;
};

#endif //FFMPEGDEMO_FFVIDEOWRITER_H
