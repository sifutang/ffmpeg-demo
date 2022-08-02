#include "VideoDecoder.h"
#include "../Logger.h"

VideoDecoder::VideoDecoder(int index, AVFormatContext *ftx) {
    mVideoIndex = index;
    mFtx = ftx;
}

VideoDecoder::~VideoDecoder() {
    release();
}

bool VideoDecoder::prepare(AVCodecParameters *codecParameters) {
    mWidth = codecParameters->width;
    mHeight = codecParameters->height;
    mDuration = mFtx->duration;

    // find decoder
    mVideoCodec = avcodec_find_decoder(codecParameters->codec_id);
    if (!mVideoCodec) {
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

    // open codec
    int ret = avcodec_open2(mVideoCodecContext, mVideoCodec, nullptr);
    if (ret != 0) {
        std::string msg = "codec open failed";
        if (mErrorMsgListener) {
            mErrorMsgListener(-3000, msg);
        }
        return false;
    }

    LOGI("codec name: %s", mVideoCodec->name)
    return true;
}

int VideoDecoder::decode(AVPacket *avPacket) {
    int res = avcodec_send_packet(mVideoCodecContext, avPacket);
    LOGI("decode video packet...pts: %lld, dts: %lld, res: %d", avPacket->pts, avPacket->dts, res)
    AVFrame *avFrame = av_frame_alloc();
    res = avcodec_receive_frame(mVideoCodecContext, avFrame);
    if (res != 0) {
        LOGE("avcodec_receive_frame err: %d", res)
        av_frame_free(&avFrame);
        av_freep(&avFrame);
        return res;
    }

    if (avFrame->format != AV_PIX_FMT_YUV420P) {
        AVFrame *yuvFrame = av_frame_alloc();
        int size = av_image_get_buffer_size(AV_PIX_FMT_YUV420P, avFrame->width, avFrame->height, 1);
        auto *buffer = static_cast<uint8_t *>(av_malloc(size * sizeof(uint8_t)));
        av_image_fill_arrays(yuvFrame->data, yuvFrame->linesize, buffer, AV_PIX_FMT_YUV420P, avFrame->width, avFrame->height, 1);

        if (mSwsContext == nullptr) {
            mSwsContext = sws_getContext(avFrame->width, avFrame->height, AVPixelFormat(avFrame->format),
                                         avFrame->width, avFrame->height, AV_PIX_FMT_YUV420P,
                                         SWS_FAST_BILINEAR,nullptr, nullptr, nullptr);
            if (!mSwsContext) {
                av_frame_free(&yuvFrame);
                av_free(yuvFrame);
                av_free(buffer);
                return -1;
            }
        }

        // transform
        sws_scale(mSwsContext,
                  reinterpret_cast<const uint8_t *const *>(avFrame->data),
                  avFrame->linesize,
                  0,
                  avFrame->height,
                  yuvFrame->data,
                  yuvFrame->linesize
        );

        if (mOnFrameArrivedListener) {
            mOnFrameArrivedListener(yuvFrame);
        }
        av_frame_free(&yuvFrame);
        av_free(yuvFrame);
        av_free(buffer);
    } else {
        if (mOnFrameArrivedListener) {
            mOnFrameArrivedListener(avFrame);
        }
    }

    // release frame
    av_frame_free(&avFrame);
    av_freep(&avFrame);

    return res;
}

void VideoDecoder::release() {
    if (mSwsContext != nullptr) {
        sws_freeContext(mSwsContext);
        mSwsContext = nullptr;
        LOGI("sws context...release")
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
    return mDuration;
}
