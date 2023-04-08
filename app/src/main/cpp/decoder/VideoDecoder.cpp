#include "VideoDecoder.h"
#include "../utils/Logger.h"
#include "../utils/CommonUtils.h"

static enum AVPixelFormat hw_pix_fmt = AV_PIX_FMT_NONE;
static enum AVPixelFormat get_hw_format(AVCodecContext *ctx,
                                        const enum AVPixelFormat *pix_fmts) {
    const enum AVPixelFormat *p;

    for (p = pix_fmts; *p != -1; p++) {
        if (*p == hw_pix_fmt) {
            LOGE("get HW surface format: %d", *p);
            return *p;
        }
    }

    LOGE("Failed to get HW surface format");
    return AV_PIX_FMT_NONE;
}

VideoDecoder::VideoDecoder(int index, AVFormatContext *ftx, int useHw): BaseDecoder(index, ftx) {
    mUseHwDecode = useHw;
}

VideoDecoder::~VideoDecoder() {
    release();
}

void VideoDecoder::setSurface(jobject surface) {
    mSurface = surface;
}

bool VideoDecoder::prepare() {
    AVCodecParameters *params = mFtx->streams[getStreamIndex()]->codecpar;
    mWidth = params->width;
    mHeight = params->height;

    // find decoder
    if (mUseHwDecode) {
        AVHWDeviceType type = av_hwdevice_find_type_by_name("mediacodec");
        if (type == AV_HWDEVICE_TYPE_NONE) {
            while ((type = av_hwdevice_iterate_types(type)) != AV_HWDEVICE_TYPE_NONE) {
                LOGI("av_hwdevice_iterate_types: %d", type)
            }
        }

        const AVCodec *h264Mediacodec = avcodec_find_decoder_by_name("h264_mediacodec");
        if (h264Mediacodec) {
            LOGE("find h264_mediacodec")
            for (int i = 0; ; ++i) {
                const AVCodecHWConfig *config = avcodec_get_hw_config(h264Mediacodec, i);
                if (!config) {
                    LOGE("Decoder: %s does not support device type: %s", h264Mediacodec->name,
                         av_hwdevice_get_type_name(type))
                    break;
                }
                if (config->methods & AV_CODEC_HW_CONFIG_METHOD_HW_DEVICE_CTX && config->device_type == type) {
                    // AV_PIX_FMT_MEDIACODEC(165)
                    hw_pix_fmt = config->pix_fmt;
                    LOGE("Decoder: %s support device type: %s, hw_pix_fmt: %d, AV_PIX_FMT_MEDIACODEC: %d", h264Mediacodec->name,
                         av_hwdevice_get_type_name(type), hw_pix_fmt, AV_PIX_FMT_MEDIACODEC);
                    break;
                }
            }

            if (hw_pix_fmt == AV_PIX_FMT_NONE) {
                LOGE("not use surface decoding")
                mVideoCodec = avcodec_find_decoder(params->codec_id);
            } else {
                mVideoCodec = h264Mediacodec;
                int ret = av_hwdevice_ctx_create(&mHwDeviceCtx, type, nullptr, nullptr, 0);
                if (ret != 0) {
                    LOGE("av_hwdevice_ctx_create err: %d", ret)
                }
            }
        } else {
            LOGE("not find h264_mediacodec")
            mVideoCodec = avcodec_find_decoder(params->codec_id);
            mUseHwDecode = false;
        }
    } else {
        mVideoCodec = avcodec_find_decoder(params->codec_id);
    }

    if (mVideoCodec == nullptr) {
        std::string msg = "not find decoder";
        if (mErrorMsgListener) {
            mErrorMsgListener(-1000, msg);
        }
        return false;
    }

    // init codec context
    mCodecContext = avcodec_alloc_context3(mVideoCodec);
    if (!mCodecContext) {
        std::string msg = "codec context alloc failed";
        if (mErrorMsgListener) {
            mErrorMsgListener(-2000, msg);
        }
        return false;
    }
    avcodec_parameters_to_context(mCodecContext, params);

    if (mHwDeviceCtx) {
        mCodecContext->get_format = get_hw_format;
        mCodecContext->hw_device_ctx = av_buffer_ref(mHwDeviceCtx);

        if (mSurface != nullptr) {
            mMediaCodecContext = av_mediacodec_alloc_context();
            av_mediacodec_default_init(mCodecContext, mMediaCodecContext, mSurface);
        }
    }

    // open codec
    int ret = avcodec_open2(mCodecContext, mVideoCodec, nullptr);
    if (ret != 0) {
        std::string msg = "codec open failed";
        if (mErrorMsgListener) {
            mErrorMsgListener(-3000, msg);
        }
        return false;
    }

    mAvFrame = av_frame_alloc();
    mStartTimeMsForSync = -1;
    mRetryReceiveCount = 30;
    LOGI("codec name: %s", mVideoCodec->name)

    initFilters();

    return true;
}

int VideoDecoder::decode(AVPacket *avPacket) {
    // 主动塞到队列中的flush帧
    bool isEof = avPacket->size == 0 && avPacket->data == nullptr;
    int sendRes = avcodec_send_packet(mCodecContext, avPacket);

    bool isKeyFrame = avPacket->flags & AV_PKT_FLAG_KEY;
    LOGI("[video] avcodec_send_packet...pts: %" PRId64 ", dts: %" PRId64 ", isKeyFrame: %d, res: %d, isEof: %d", avPacket->pts, avPacket->dts, isKeyFrame, sendRes, isEof)

    // avcodec_send_packet的-11表示要先读output，然后pkt需要重发
    mNeedResent = sendRes == AVERROR(EAGAIN);

    // avcodec_receive_frame的-11，表示需要发新帧
    int receiveRes = avcodec_receive_frame(mCodecContext, mAvFrame);

    if (isEof && receiveRes != AVERROR_EOF && mRetryReceiveCount >= 0) {
        mNeedResent = true;
        mRetryReceiveCount--;
        LOGE("[video] send eof, not receive eof...retry count: %" PRId64, mRetryReceiveCount)
    }

    if (receiveRes != 0) {
        LOGE("[video] avcodec_receive_frame err: %d, resent: %d", receiveRes, mNeedResent)
        av_frame_unref(mAvFrame);
        return receiveRes;
    }

    LOGI("[video] avcodec_receive_frame...pts: %" PRId64 ", format: %d, need retry: %d", mAvFrame->pts, mAvFrame->format, mNeedResent)

    AVFrame *finalFrame = mAvFrame;
    if (mEnableFilter && mBufferSinkCtx && mBufferScrCtx) {
        int ret = av_buffersrc_add_frame_flags(mBufferScrCtx, mAvFrame, AV_BUFFERSRC_FLAG_KEEP_REF);
        LOGI("av_buffersrc_add_frame_flags, ret: %d", ret)
        if (mFilterAvFrame == nullptr) {
            mFilterAvFrame = av_frame_alloc();
        }
        ret = av_buffersink_get_frame(mBufferSinkCtx, mFilterAvFrame);
        LOGI("av_buffersink_get_frame, ret: %d, format: %d", ret, mFilterAvFrame->format)
        if (ret >= 0) {
            finalFrame = mFilterAvFrame;
        }
    }

    updateTimestamp(finalFrame);

    if (finalFrame->pts >= mSeekPos) {
        mSeekPos = INT64_MAX;
        mSeekEndTimeMs = getCurrentTimeMs();
        int64_t precisionSeekConsume = mSeekEndTimeMs - mSeekStartTimeMs;
        LOGE("[video] avcodec_receive_frame...pts: %" PRId64 ", precision seek consume: %" PRId64, mAvFrame->pts, precisionSeekConsume)
    }

    if (finalFrame->format == AV_PIX_FMT_YUV420P || mAvFrame->format == AV_PIX_FMT_NV12 || mAvFrame->format == hw_pix_fmt) {
        notifyFrameArrived(finalFrame);
    } else if (finalFrame->format != AV_PIX_FMT_NONE) {
        AVFrame *swFrame = av_frame_alloc();
        int size = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, finalFrame->width, finalFrame->height, 1);
        auto *buffer = static_cast<uint8_t *>(av_malloc(size * sizeof(uint8_t)));
        av_image_fill_arrays(swFrame->data, swFrame->linesize, buffer, AV_PIX_FMT_YUV420P, finalFrame->width, finalFrame->height, 1);

        if (swsScale(finalFrame, swFrame) > 0) {
            notifyFrameArrived(swFrame);
        }

        av_frame_free(&swFrame);
        av_freep(&swFrame);
        av_free(buffer);
    } else {
        LOGE("[video] frame format is AV_PIX_FMT_NONE")
    }

    av_frame_unref(mFilterAvFrame);
    av_frame_unref(mAvFrame);

    return receiveRes;
}

int VideoDecoder::swsScale(AVFrame *srcFrame, AVFrame *swFrame) {
    if (mSwsContext == nullptr) {
        mSwsContext = sws_getContext(srcFrame->width, srcFrame->height, AVPixelFormat(srcFrame->format),
                                     srcFrame->width, srcFrame->height, AV_PIX_FMT_YUV420P,
                                     SWS_FAST_BILINEAR,nullptr, nullptr, nullptr);
        if (!mSwsContext) {
            return -1;
        }
    }

    // transform
    int ret = sws_scale(mSwsContext,
              reinterpret_cast<const uint8_t *const *>(srcFrame->data),
              srcFrame->linesize,
              0,
              srcFrame->height,
              swFrame->data,
              swFrame->linesize
    );

    return ret;
}

void VideoDecoder::notifyFrameArrived(AVFrame *frame) {
    if (mOnFrameArrivedListener) {
        mOnFrameArrivedListener(frame);
    }
}

int64_t VideoDecoder::getTimestamp() const {
    return mCurTimeStampMs;
}

void VideoDecoder::updateTimestamp(AVFrame *frame) {
    if (mStartTimeMsForSync < 0) {
        LOGE("update video start time")
        mStartTimeMsForSync = getCurrentTimeMs();
    }
    if (frame->pkt_dts != AV_NOPTS_VALUE) {
        mCurTimeStampMs = frame->pkt_dts;
    } else if (frame->pts != AV_NOPTS_VALUE) {
        mCurTimeStampMs = frame->pts;
    }
    // s -> ms
    mCurTimeStampMs = (int64_t)(mCurTimeStampMs * av_q2d(mTimeBase) * 1000);

    if (mFixStartTime) {
        mStartTimeMsForSync = getCurrentTimeMs() - mCurTimeStampMs;
        mFixStartTime = false;
        LOGE("fix video start time")
    }
}

int VideoDecoder::getWidth() const {
    return mWidth;
}

int VideoDecoder::getHeight() const {
    return mHeight;
}

double VideoDecoder::getDuration() {
    return mDuration;
}

void VideoDecoder::avSync(AVFrame *frame) {
    int64_t elapsedTimeMs = getCurrentTimeMs() - mStartTimeMsForSync;
    int64_t diff = mCurTimeStampMs - elapsedTimeMs;
    diff = FFMIN(diff, DELAY_THRESHOLD);
    LOGI("[video] avSync, pts: %" PRId64 "ms, diff: %" PRId64 "ms", mCurTimeStampMs, diff)
    if (diff > 0) {
        av_usleep(diff * 1000);
    }
}

int VideoDecoder::seek(double pos) {
    flush();
    int64_t seekPos = av_rescale_q((int64_t)(pos * AV_TIME_BASE), AV_TIME_BASE_Q, mTimeBase);
    int ret = avformat_seek_file(mFtx, getStreamIndex(),
                                 INT64_MIN, seekPos, INT64_MAX, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME);
    LOGE("[video] seek to: %f, seekPos: %" PRId64 ", ret: %d", pos, seekPos, ret)
    // seek后需要恢复起始时间
    mFixStartTime = true;
    mSeekPos = seekPos;
    mSeekStartTimeMs = getCurrentTimeMs();
    return ret;
}

void VideoDecoder::release() {
    mFixStartTime = false;
    mStartTimeMsForSync = -1;
    if (mAvFrame != nullptr) {
        av_frame_free(&mAvFrame);
        av_freep(&mAvFrame);
        LOGI("av frame...release")
    }

    if (mSwsContext != nullptr) {
        sws_freeContext(mSwsContext);
        mSwsContext = nullptr;
        LOGI("sws context...release")
    }

    if (mFilterAvFrame != nullptr) {
        av_frame_free(&mFilterAvFrame);
        av_freep(&mFilterAvFrame);
    }

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

    if (mMediaCodecContext != nullptr) {
        av_mediacodec_default_free(mCodecContext);
        mMediaCodecContext = nullptr;
        LOGI("mediacodec context...release")
    }

    if (mHwDeviceCtx != nullptr) {
        av_buffer_unref(&mHwDeviceCtx);
        mHwDeviceCtx = nullptr;
        LOGI("hw device context...release")
    }

    if (mCodecContext != nullptr) {
        avcodec_close(mCodecContext);
        avcodec_free_context(&mCodecContext);
        mCodecContext = nullptr;
        LOGI("codec...release")
    }
}

void VideoDecoder::initFilters() {
    const AVFilter *bufferSrc = avfilter_get_by_name("buffer");
    const AVFilter *bufferSink = avfilter_get_by_name("buffersink");

    mFilterOutputs = avfilter_inout_alloc();
    mFilterInputs = avfilter_inout_alloc();
    mFilterGraph = avfilter_graph_alloc();
    if (!mFilterOutputs || !mFilterInputs || !mFilterGraph) {
        LOGE("initFilters failed")
        return;
    }

    int ret;
    do {
        char args[512];
        snprintf(args, sizeof(args),
                 "video_size=%dx%d:pix_fmt=%d:time_base=%d/%d:pixel_aspect=%d/%d",
                 mCodecContext->width, mCodecContext->height, mCodecContext->pix_fmt,
                 mTimeBase.num, mTimeBase.den,
                 mCodecContext->sample_aspect_ratio.num, mCodecContext->sample_aspect_ratio.den);
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
        std::string filterDesc = "drawgrid=w=iw/3:h=ih/3:t=2:c=white@0.5";
        ret = avfilter_graph_parse_ptr(mFilterGraph, filterDesc.c_str(), &mFilterInputs, &mFilterOutputs, nullptr);
        LOGI("avfilter_graph_parse_ptr, ret: %d", ret)
        if (ret < 0) {
            break;
        }

        ret = avfilter_graph_config(mFilterGraph, nullptr);
        LOGI("avfilter_graph_config, ret: %d", ret)
    } while (false);

    if (ret < 0) {
        avfilter_inout_free(&mFilterInputs);
        mFilterInputs = nullptr;
        avfilter_inout_free(&mFilterOutputs);
        mFilterOutputs = nullptr;

        mBufferScrCtx = nullptr;
        mBufferSinkCtx = nullptr;

        avfilter_graph_free(&mFilterGraph);
        mFilterGraph = nullptr;
        LOGE("initFilters failed, clean ctx")
    }
}

void VideoDecoder::enableGridFilter(bool enable) {
    mEnableFilter = enable;
}

