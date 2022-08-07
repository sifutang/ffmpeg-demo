#include "VideoDecoder.h"
#include "../Logger.h"

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

VideoDecoder::VideoDecoder(int index, AVFormatContext *ftx) {
    mVideoIndex = index;
    mFtx = ftx;
}

VideoDecoder::~VideoDecoder() {
    release();
}

bool VideoDecoder::prepare(AVCodecParameters *codecParameters, jobject surface) {
    mWidth = codecParameters->width;
    mHeight = codecParameters->height;
    mDuration = mFtx->duration;

    // find decoder
    if (mUseHwEncode) {
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
                mVideoCodec = avcodec_find_decoder(codecParameters->codec_id);
            } else {
                mVideoCodec = h264Mediacodec;
                int ret = av_hwdevice_ctx_create(&mHwDeviceCtx, type, nullptr, nullptr, 0);
                if (ret != 0) {
                    LOGE("av_hwdevice_ctx_create err: %d", ret)
                }
            }
        } else {
            LOGE("not find h264_mediacodec")
            mVideoCodec = avcodec_find_decoder(codecParameters->codec_id);
            mUseHwEncode = false;
        }
    } else {
        mVideoCodec = avcodec_find_decoder(codecParameters->codec_id);
    }

    if (mVideoCodec == nullptr) {
        std::string msg = "not find decoder";
        if (mErrorMsgListener) {
            mErrorMsgListener(-1000, msg);
        }
        return false;
    }

    // init codec context
    mVideoCodecContext = avcodec_alloc_context3(mVideoCodec);
    if (!mVideoCodecContext) {
        std::string msg = "codec context alloc failed";
        if (mErrorMsgListener) {
            mErrorMsgListener(-2000, msg);
        }
        return false;
    }
    avcodec_parameters_to_context(mVideoCodecContext, codecParameters);

    if (mHwDeviceCtx) {
        mVideoCodecContext->get_format = get_hw_format;
        mVideoCodecContext->hw_device_ctx = av_buffer_ref(mHwDeviceCtx);

        if (surface != nullptr) {
            mMediaCodecContext = av_mediacodec_alloc_context();
            av_mediacodec_default_init(mVideoCodecContext, mMediaCodecContext, surface);
        }
    }

    // open codec
    int ret = avcodec_open2(mVideoCodecContext, mVideoCodec, nullptr);
    if (ret != 0) {
        std::string msg = "codec open failed";
        if (mErrorMsgListener) {
            mErrorMsgListener(-3000, msg);
        }
        return false;
    }

    mAvFrame = av_frame_alloc();
    LOGI("codec name: %s", mVideoCodec->name)
    return true;
}

int VideoDecoder::decode(AVPacket *avPacket) {
    int res = avcodec_send_packet(mVideoCodecContext, avPacket);
    LOGI("avcodec_send_packet...pts: %" PRId64 ", dts: %" PRId64 ", res: %d", avPacket->pts, avPacket->dts, res)

    do {
        res = avcodec_receive_frame(mVideoCodecContext, mAvFrame);
        if (res != 0) {
            LOGE("avcodec_receive_frame err: %d", res)
        }
        // todo 有点奇怪，但是这样解码出来才对，不会丢帧，oppo reno ace
    } while (mUseHwEncode && res == AVERROR(EAGAIN));

    if (res != 0) {
        LOGE("avcodec_receive_frame err: %d", res)
        av_frame_unref(mAvFrame);
        return res;
    }

    LOGI("avcodec_receive_frame...pts: %" PRId64 ", format: %d", mAvFrame->pts, mAvFrame->format)
    if (mAvFrame->format == hw_pix_fmt) {
        LOGI("hw frame")
//        AVFrame *sw_frame = av_frame_alloc();
//        int code = av_hwframe_transfer_data(sw_frame, mAvFrame, 0);
        if (mOnFrameArrivedListener) {
            mOnFrameArrivedListener(mAvFrame);
        }
    } else if (mAvFrame->format == AV_PIX_FMT_YUV420P || mAvFrame->format == AV_PIX_FMT_NV12) {
        if (mOnFrameArrivedListener) {
            mOnFrameArrivedListener(mAvFrame);
        }
    } else if (mAvFrame->format != AV_PIX_FMT_YUV420P) {
        AVFrame *swFrame = av_frame_alloc();
        int size = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, mAvFrame->width, mAvFrame->height, 1);
        auto *buffer = static_cast<uint8_t *>(av_malloc(size * sizeof(uint8_t)));
        av_image_fill_arrays(swFrame->data, swFrame->linesize, buffer, AV_PIX_FMT_YUV420P, mAvFrame->width, mAvFrame->height, 1);

        if (mSwsContext == nullptr) {
            mSwsContext = sws_getContext(mAvFrame->width, mAvFrame->height, AVPixelFormat(mAvFrame->format),
                                         mAvFrame->width, mAvFrame->height, AV_PIX_FMT_YUV420P,
                                         SWS_FAST_BILINEAR,nullptr, nullptr, nullptr);
            if (!mSwsContext) {
                av_frame_free(&swFrame);
                av_freep(&swFrame);
                av_free(buffer);
                return -1;
            }
        }

        // transform
        sws_scale(mSwsContext,
                  reinterpret_cast<const uint8_t *const *>(mAvFrame->data),
                  mAvFrame->linesize,
                  0,
                  mAvFrame->height,
                  swFrame->data,
                  swFrame->linesize
        );

        if (mOnFrameArrivedListener) {
            mOnFrameArrivedListener(swFrame);
        }
        av_frame_free(&swFrame);
        av_freep(&swFrame);
        av_free(buffer);
    }
    av_frame_unref(mAvFrame);

    return res;
}

void VideoDecoder::release() {
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

    if (mMediaCodecContext != nullptr) {
        av_mediacodec_default_free(mVideoCodecContext);
        mMediaCodecContext = nullptr;
        LOGI("mediacodec context...release")
    }

    if (mHwDeviceCtx != nullptr) {
        av_buffer_unref(&mHwDeviceCtx);
        mHwDeviceCtx = nullptr;
        LOGI("hw device context...release")
    }

    if (mVideoCodecContext != nullptr) {
        avcodec_close(mVideoCodecContext);
        avcodec_free_context(&mVideoCodecContext);
        mVideoCodecContext = nullptr;
        LOGI("codec...release")
    }
}

void VideoDecoder::setOnFrameArrived(std::function<void(AVFrame *)> listener) {
    mOnFrameArrivedListener = std::move(listener);
}

void VideoDecoder::setErrorMsgListener(std::function<void(int, std::string &)> listener) {
    mErrorMsgListener = std::move(listener);
}

int VideoDecoder::getWidth() const {
    return mWidth;
}

int VideoDecoder::getHeight() const {
    return mHeight;
}

int VideoDecoder::getStreamIndex() const {
    return mVideoIndex;
}

double VideoDecoder::getDuration() const {
    return (double)mDuration;
}
