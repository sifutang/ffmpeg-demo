//
// Created by 雪月清的随笔 on 14/5/23.
//

#ifndef FFMPEGDEMO_FFFILTER_H
#define FFMPEGDEMO_FFFILTER_H

#include <string>

extern "C" {
#include "../vendor/ffmpeg/libavfilter/avfilter.h"
#include "../vendor/ffmpeg/libavfilter/buffersrc.h"
#include "../vendor/ffmpeg/libavfilter/buffersink.h"
#include "../vendor/ffmpeg/libavutil/opt.h"
#include "../vendor/ffmpeg//libavcodec/avcodec.h"
}

class FFFilter {

public:
    FFFilter();

    ~FFFilter();

    bool init(const std::string& graphInArgs, const std::string& filterDesc);

    AVFrame* process(AVFrame *origin);

    bool release();

    static void createGridFilterDesc(AVCodecContext *codecContext,
                                            AVRational timebase,
                                            std::string &graphInArgs,
                                            std::string &filterDesc);

private:
    AVFrame *mFilterAvFrame = nullptr;
    AVFilterContext *mBufferScrCtx = nullptr;
    AVFilterContext *mBufferSinkCtx = nullptr;
    AVFilterInOut *mFilterOutputs = nullptr;
    AVFilterInOut *mFilterInputs = nullptr;
    AVFilterGraph *mFilterGraph = nullptr;

    bool mNeedUnRef = false;

};


#endif //FFMPEGDEMO_FFFILTER_H
