//
// Created by 雪月清的随笔 on 14/5/23.
//

#include "FFFilter.h"
#include "header/Logger.h"

FFFilter::FFFilter() {
    LOGI("FFFilter")
}

FFFilter::~FFFilter() {
    LOGI("~FFFilter")
    release();
}

bool FFFilter::init(const std::string& graphInArgs, const std::string& filterDesc) {
    LOGI("FFFilter init")
    const AVFilter *bufferSrc = avfilter_get_by_name("buffer");
    const AVFilter *bufferSink = avfilter_get_by_name("buffersink");

    mFilterOutputs = avfilter_inout_alloc();
    mFilterInputs = avfilter_inout_alloc();
    mFilterGraph = avfilter_graph_alloc();
    if (!mFilterOutputs || !mFilterInputs || !mFilterGraph) {
        LOGE("initFilters failed")
        return false;
    }

    int ret;
    do {
        const char *args = graphInArgs.c_str();
        LOGE("avfilter_graph_create_filter, args: %s", args)
        ret = avfilter_graph_create_filter(&mBufferScrCtx, bufferSrc, "in", args, nullptr, mFilterGraph);
        if (ret < 0) {
            LOGE("Cannot create buffer source, ret: %d", ret)
            break;
        }

        ret = avfilter_graph_create_filter(&mBufferSinkCtx, bufferSink, "out", nullptr, nullptr, mFilterGraph);
        if (ret < 0) {
            LOGE("Cannot create buffer sink, ret: %d", ret)
            break;
        }

        enum AVPixelFormat pix_fmts[] = {AV_PIX_FMT_YUV420P, AV_PIX_FMT_NONE};
        ret = av_opt_set_int_list(mBufferSinkCtx, "pix_fmts", pix_fmts,
                                  AV_PIX_FMT_NONE, AV_OPT_SEARCH_CHILDREN);
        if(ret < 0) {
            LOGE("set output pixel format failed, err=%d", ret);
            break;
        }

        mFilterOutputs->name = av_strdup("in");
        mFilterOutputs->filter_ctx = mBufferScrCtx;
        mFilterOutputs->pad_idx = 0;
        mFilterOutputs->next = nullptr;

        mFilterInputs->name = av_strdup("out");
        mFilterInputs->filter_ctx = mBufferSinkCtx;
        mFilterInputs->pad_idx = 0;
        mFilterInputs->next = nullptr;

        // ffmpeg -h filter=drawgrid
        ret = avfilter_graph_parse_ptr(mFilterGraph, filterDesc.c_str(), &mFilterInputs, &mFilterOutputs, nullptr);
        LOGI("avfilter_graph_parse_ptr, ret: %d", ret)
        if (ret < 0) {
            break;
        }

        ret = avfilter_graph_config(mFilterGraph, nullptr);
        LOGI("avfilter_graph_config, ret: %d", ret)
    } while (false);

    LOGI("FFFilter init: %d", ret)
    return ret >= 0;
}

AVFrame *FFFilter::process(AVFrame *origin) {
    if (mFilterAvFrame == nullptr) {
        mFilterAvFrame = av_frame_alloc();
    }

    if (mNeedUnRef) {
        av_frame_unref(mFilterAvFrame);
        mNeedUnRef = false;
    }

    int ret = av_buffersrc_add_frame_flags(mBufferScrCtx, origin, AV_BUFFERSRC_FLAG_KEEP_REF);
    LOGI("av_buffersrc_add_frame_flags, ret: %d", ret)

    ret = av_buffersink_get_frame(mBufferSinkCtx, mFilterAvFrame);
    LOGI("av_buffersink_get_frame, ret: %d, format: %d", ret, mFilterAvFrame->format)

    mNeedUnRef = true;

    return ret >= 0 ? mFilterAvFrame : nullptr;
}

bool FFFilter::release() {
    LOGI("FFFilter release")
    if (mFilterInputs != nullptr) {
        avfilter_inout_free(&mFilterInputs);
        mFilterInputs = nullptr;
    }

    if (mFilterOutputs != nullptr) {
        avfilter_inout_free(&mFilterOutputs);
        mFilterOutputs = nullptr;
    }

    if (mFilterGraph != nullptr) {
        avfilter_graph_free(&mFilterGraph);
        mFilterGraph = nullptr;
    }

    mBufferScrCtx = nullptr;
    mBufferSinkCtx = nullptr;

    if (mFilterAvFrame != nullptr) {
        if (mNeedUnRef) {
            av_frame_unref(mFilterAvFrame);
            mNeedUnRef = false;
        }
        av_frame_free(&mFilterAvFrame);
        av_freep(&mFilterAvFrame);
    }
    return true;
}

void FFFilter::createGridFilterDesc(AVCodecContext *codecContext,
                                                AVRational timebase,
                                                std::string &graphInArgs,
                                                std::string &filterDesc) {
    // graphInArgs
    char args[512];
    snprintf(args, sizeof(args),
             "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
             codecContext->width, codecContext->height, codecContext->pix_fmt,
             timebase.num, timebase.den,
             codecContext->sample_aspect_ratio.num, codecContext->sample_aspect_ratio.den);

    graphInArgs = std::string(args);

    // filterDesc
    filterDesc = "drawgrid=w=iw/3:h=ih/3:t=2:c=white@0.5";
}
