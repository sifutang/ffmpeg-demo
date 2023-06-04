//
// Created by 雪月清的随笔 on 14/5/23.
//

#include "FFVideoWriter.h"
#include "../utils/Logger.h"
#include "../utils/FFConverter.h"

FFVideoWriter::FFVideoWriter() {
    LOGI("FFVideoWriter")
}

FFVideoWriter::~FFVideoWriter() {
    release();
    LOGI("~FFVideoWriter")
}

bool FFVideoWriter::init(std::string &outputPath, CompileSettings &settings) {
    do {
        // AVFormatContext
        int ret = avformat_alloc_output_context2(&mOutputFtx, nullptr, nullptr, outputPath.c_str());
        if (ret != 0) {
            LOGE("FFVideoWriter, avformat_alloc_output_context2 failed: %d", ret)
            break;
        }

        int width = settings.width;
        int height = settings.height;
        AVPixelFormat format = getAvPixelFormat(settings.pixelFormat);
        AVCodecID codecId = getAvCodecID(settings.encodeType);

        // AVCodecParameters
        mCodecParameters = avcodec_parameters_alloc();
        mCodecParameters->codec_type = getAvMediaType(settings.mediaType);
        mCodecParameters->width = width;
        mCodecParameters->height = height;
        mCodecParameters->format = format;
        mCodecParameters->codec_id = codecId;

        // AVStream
        mStream = avformat_new_stream(mOutputFtx, nullptr);
        avcodec_parameters_copy(mStream->codecpar, mCodecParameters);

        // AVCodec
        const AVCodec *encoder = avcodec_find_encoder(codecId);

        // AVCodecContext
        mCodecContext = avcodec_alloc_context3(encoder);
        avcodec_parameters_to_context(mCodecContext, mCodecParameters);

        if (codecId == AV_CODEC_ID_GIF) {
            mCodecContext->time_base = (AVRational){1, settings.fps};
        } else {
            mCodecContext->time_base = (AVRational){1, 90000};
        }
        mCodecContext->framerate = (AVRational){settings.fps, 1};
        mCodecContext->pix_fmt = format;
        mCodecContext->max_b_frames = settings.maxBFrameCount;
        mCodecContext->gop_size = settings.gopSize;
        mCodecContext->bit_rate = settings.bitRate;

        if (!(mOutputFtx->oformat->flags & AVFMT_NOFILE)) {
            avio_open(&mOutputFtx->pb, outputPath.c_str(), AVIO_FLAG_WRITE);
        }

        ret = avcodec_open2(mCodecContext, encoder, nullptr);
        if (ret < 0) {
            LOGE("FFVideoWriter, avcodec_open2 failed: %d", ret)
            break;
        }

        ret = avformat_write_header(mOutputFtx, nullptr);
        if (ret < 0) {
            LOGE("FFVideoWriter, avformat_write_header failed: %d", ret)
            break;
        }

        mAvFrame = av_frame_alloc();
        if (mAvFrame == nullptr) {
            LOGE("FFVideoWriter, av_frame_alloc failed")
            break;
        }
        mAvFrame->width = width;
        mAvFrame->height = height;
        mAvFrame->format = format;

        ret = av_frame_get_buffer(mAvFrame, 0);
        if (ret < 0) {
            LOGE("FFVideoWriter, av_frame_get_buffer failed: %d", ret)
            break;
        }
    } while (false);

    return false;
}

void FFVideoWriter::encode(AVFrame *frame) {
    /** Make sure the frame data is writable.
     * On the first round, the frame is fresh from av_frame_get_buffer() and therefore we know it is writable.
     * But on the next rounds, encode() will have called avcodec_send_frame(), and the codec may have kept a reference to
     * the frame in its internal structures, that makes the frame unwritable.
     * av_frame_make_writable() checks that and allocates a new buffer for the frame only if necessary.
    */
    int ret = av_frame_make_writable(mAvFrame);
    if (ret < 0) {
        LOGE("FFVideoWriter, av_frame_make_writable failed: %d", ret)
        return;
    }

    // transform
    AVFrame *encodeFrame = nullptr;
    if (frame != nullptr) {
        if (mSwsContext == nullptr) {
            mSwsContext = sws_getContext(frame->width, frame->height, AVPixelFormat(frame->format),
                                         mAvFrame->width, mAvFrame->height, AVPixelFormat(mAvFrame->format),
                                         SWS_BICUBIC,nullptr, nullptr, nullptr);
            if (!mSwsContext) {
                LOGE("FFVideoWriter, sws_getContext failed")
                return;
            }
        }

        ret = sws_scale(mSwsContext, frame->data, frame->linesize, 0, frame->height, mAvFrame->data, mAvFrame->linesize);
        if (ret < 0) {
            LOGE("FFVideoWriter, sws_scale failed: %d", ret)
            return;
        }
        mAvFrame->pts = mFrameIndex++;
        encodeFrame = mAvFrame;
    }

    // encode
    int sendRes = avcodec_send_frame(mCodecContext, encodeFrame);
    if (sendRes != 0) {
        LOGE("FFVideoWriter, avcodec_send_frame: %d", sendRes)
    }

    AVPacket *pkt = av_packet_alloc();
    int receiveRes = avcodec_receive_packet(mCodecContext, pkt);
    if (receiveRes == 0) {
        pkt->stream_index = mStream->index;
        ret = av_interleaved_write_frame(mOutputFtx, pkt);
        if (ret != 0) {
            LOGE("FFVideoWriter, av_interleaved_write_frame: %d", ret)
        }
    } else if (receiveRes == AVERROR_EOF) {
        LOGE("FFVideoWriter, avcodec_receive_packet: AVERROR_EOF")
    } else {
        LOGE("FFVideoWriter, avcodec_receive_packet: %d", receiveRes)
    }
    av_packet_unref(pkt);
    av_packet_free(&pkt);
}

void FFVideoWriter::signalEof() {
    // flush the encoder
    encode(nullptr);
    int ret = av_write_trailer(mOutputFtx);
    LOGI("FFVideoWriter, av_write_trailer: %d", ret)
}

void FFVideoWriter::release() {
    LOGI("FFVideoWriter, release start")
    if (mAvFrame != nullptr) {
        av_frame_free(&mAvFrame);
        mAvFrame = nullptr;
    }

    if (mSwsContext != nullptr) {
        sws_freeContext(mSwsContext);
        mSwsContext = nullptr;
    }

    if (mCodecParameters != nullptr) {
        avcodec_parameters_free(&mCodecParameters);
        mCodecParameters = nullptr;
    }

    if (mCodecContext != nullptr) {
        avcodec_free_context(&mCodecContext);
        mCodecContext = nullptr;
    }

    if (mOutputFtx != nullptr) {
        if (!(mOutputFtx->flags & AVFMT_NOFILE)) {
            avio_closep(&mOutputFtx->pb);
        }
        avformat_free_context(mOutputFtx);
        mOutputFtx = nullptr;
    }
    LOGI("FFVideoWriter, release end")
}
