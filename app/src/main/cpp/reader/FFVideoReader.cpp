#include "FFVideoReader.h"
#include "../utils/Logger.h"
#include "../utils/CommonUtils.h"
#include "../vendor/libyuv/libyuv.h"

FFVideoReader::FFVideoReader(std::string &path) {
    mInit = init(path);
    if (mInit) {
        mInit = selectTrack(Track_Video);
    }
    LOGE("[FFVideoReader], init: %d", mInit)
}

FFVideoReader::~FFVideoReader() {
    if (mSwsContext != nullptr) {
        sws_freeContext(mSwsContext);
        mSwsContext = nullptr;
    }
}

void FFVideoReader::getFrame(int64_t pts, int width, int height, uint8_t *buffer) {
    int64_t start = getCurrentTimeMs();
    LOGI("[FFVideoReader], getFrame: %" PRId64 ", mLastPts: %" PRId64 ", width: %d, height: %d", pts, mLastPts, width, height)
    if (mLastPts == -1) {
        LOGI("[FFVideoReader], seek")
        seek(pts);
    } else if (getKeyFrameIndex(mLastPts) != getKeyFrameIndex(pts)) {
        LOGI("[FFVideoReader], flush & seek")
        flush();
        seek(pts);
    } else {
        // only need loop decode
        LOGI("[FFVideoReader], only need loop decode")
    }
    mLastPts = pts;

    AVCodecContext *codecContext = getCodecContext();
    MediaInfo mediaInfo = getMediaInfo();
    LOGI("[FFVideoReader], getFrame, origin: %dx%d, dst: %dx%d", mediaInfo.width, mediaInfo.height, width, height)

    AVFrame *frame = av_frame_alloc();
    AVPacket *pkt = av_packet_alloc();

    int64_t target = av_rescale_q(pts * AV_TIME_BASE, AV_TIME_BASE_Q, mediaInfo.video_time_base);
    bool find = false;
    int decodeCount = 0;
    while (true) {
        int ret = fetchAvPacket(pkt);
        if (ret < 0) {
            LOGE("[FFVideoReader], fetchAvPacket failed: %d", ret)
            break;
        }

        avcodec_send_packet(codecContext, pkt);
        avcodec_receive_frame(codecContext, frame);
        decodeCount++;

        if (frame->pts >= target) {
            find = true;
            LOGE("[FFVideoReader], get frame decode done, pts: %" PRId64 ", time: %f, format: %d, consume: %" PRId64 ", decodeCount: %d",
                 frame->pts,
                 (frame->pts * av_q2d(mediaInfo.video_time_base)),
                 frame->format,
                 (getCurrentTimeMs() - start), decodeCount)
            break;
        }

        av_packet_unref(pkt);
        av_frame_unref(frame);
    }

    if (find) {
        if (frame->format == AV_PIX_FMT_YUV420P) {
            libyuv::I420ToABGR(frame->data[0], frame->linesize[0],
                               frame->data[1], frame->linesize[1],
                               frame->data[2], frame->linesize[2],
                               buffer, width * 4, width, height);
        } else if (frame->format != AV_PIX_FMT_RGBA) {
            AVFrame *swFrame = av_frame_alloc();
            unsigned int size = av_image_get_buffer_size(AV_PIX_FMT_RGBA, width, height, 1);
            auto *rgbaBuffer = static_cast<uint8_t *>(av_malloc(size * sizeof(uint8_t)));
            av_image_fill_arrays(swFrame->data, swFrame->linesize, rgbaBuffer, AV_PIX_FMT_RGBA,
                                 width, height, 1);

            if (mSwsContext == nullptr) {
                mSwsContext = sws_getContext(frame->width, frame->height,
                                             AVPixelFormat(frame->format),
                                             width, height, AV_PIX_FMT_RGBA,
                                             SWS_BICUBIC, nullptr, nullptr, nullptr);
                if (!mSwsContext) {
                    LOGE("[FFVideoReader], sws_getContext failed")
                }
            }

            // transform
            int ret = sws_scale(mSwsContext,
                                reinterpret_cast<const uint8_t *const *>(frame->data),
                                frame->linesize,
                                0,
                                frame->height,
                                swFrame->data,
                                swFrame->linesize
            );
            if (ret <= 0) {
                LOGE("[FFVideoReader], sws_scale failed, ret: %d", ret)
            }

            uint8_t *src = swFrame->data[0];
            int srcStride = swFrame->linesize[0];
            int dstStride = width * 4;
            for (int i = 0; i < height; i++) {
                memcpy(buffer + i * dstStride, src + i * srcStride, srcStride);
            }

            av_frame_free(&swFrame);
            av_freep(&swFrame);
            av_free(rgbaBuffer);
        } else {
            memcpy(buffer, frame->data[0], width * height * 4);
        }
    }

    av_packet_unref(pkt);
    av_packet_free(&pkt);
    av_free(pkt);

    av_frame_unref(frame);
    av_frame_free(&frame);
    av_free(frame);

}
