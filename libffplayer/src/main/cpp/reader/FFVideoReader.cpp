#include "FFVideoReader.h"
#include "../utils/Logger.h"
#include "../utils/CommonUtils.h"
#include "../vendor/libyuv/libyuv.h"

extern "C" {
#include "libavutil/display.h"
}

FFVideoReader::FFVideoReader() {
    LOGI("FFVideoReader")
}

FFVideoReader::~FFVideoReader() {
    LOGI("~FFVideoReader")
    if (mSwsContext != nullptr) {
        sws_freeContext(mSwsContext);
        mSwsContext = nullptr;
    }

    mScaleBufferSize = -1;
    if (mScaleBuffer != nullptr) {
        free(mScaleBuffer);
        mScaleBuffer = nullptr;
    }

    if (mAvFrame != nullptr) {
        av_frame_free(&mAvFrame);
        av_free(mAvFrame);
        mAvFrame = nullptr;
    }
}

bool FFVideoReader::init(std::string &path) {
    mInit = FFReader::init(path);
    if (mInit) {
        mInit = selectTrack(Track_Video);
    }
    LOGI("[FFVideoReader], init: %d", mInit)
    return mInit;
}

void FFVideoReader::getFrame(int64_t pts, int width, int height, uint8_t *buffer, bool precise) {
    int64_t start = getCurrentTimeMs();
    LOGI("[FFVideoReader], getFrame: %" PRId64 ", mLastPts: %" PRId64 ", width: %d, height: %d", pts, mLastPts, width, height)
    if (mLastPts == -1) {
        LOGI("[FFVideoReader], seek")
        seek(pts);
    } else if (!precise || getKeyFrameIndex(mLastPts) != getKeyFrameIndex(pts)) {
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
    bool needScale = mediaInfo.width != width || mediaInfo.height != height;

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

        int sendRes = avcodec_send_packet(codecContext, pkt);
        int receiveRes = avcodec_receive_frame(codecContext, frame);
        decodeCount++;
        LOGD("[FFVideoReader], receiveRes: %d, sendRes: %d", receiveRes, sendRes)
        if (receiveRes == AVERROR(EAGAIN)) {
            continue;
        }

        // 非精准抽帧
        if (!precise) {
            find = true;
            break;
        }

        // 精准抽帧
        if (frame->pts >= target) {
            find = true;
            break;
        }

        av_packet_unref(pkt);
        av_frame_unref(frame);
    }

    if (find) {
        LOGE("[FFVideoReader], get frame decode done, pts: %" PRId64 ", time: %f, format: %d, consume: %" PRId64 ", decodeCount: %d",
             frame->pts,
             (frame->pts * av_q2d(mediaInfo.video_time_base)),
             frame->format,
             (getCurrentTimeMs() - start), decodeCount);

        if (frame->format == AV_PIX_FMT_NONE) {
            assert(false);
        } else if (frame->format == AV_PIX_FMT_YUV420P) {
            if (needScale) {
                int64_t scaleBufferSize = width * height * 3 / 2;
                if (mScaleBuffer && scaleBufferSize != mScaleBufferSize) {
                    free(mScaleBuffer);
                    mScaleBuffer = nullptr;
                }
                mScaleBufferSize = scaleBufferSize;
                if (mScaleBuffer == nullptr) {
                    mScaleBuffer = (uint8_t *) malloc(scaleBufferSize);
                }

                auto scaleBuffer = mScaleBuffer;
                libyuv::I420Scale(
                        frame->data[0], frame->linesize[0],
                        frame->data[1], frame->linesize[1],
                        frame->data[2], frame->linesize[2],
                        mediaInfo.width, mediaInfo.height,
                        scaleBuffer, width,
                        scaleBuffer + width * height, width / 2,
                        scaleBuffer + width * height * 5 / 4, width / 2,
                        width, height, libyuv::kFilterNone);

                libyuv::I420ToABGR(scaleBuffer, width,
                                   scaleBuffer + width * height, width / 2,
                                   scaleBuffer + width * height * 5 / 4, width / 2,
                                   buffer, width * 4, width, height);
            } else {
                libyuv::I420ToABGR(frame->data[0], frame->linesize[0],
                                   frame->data[1], frame->linesize[1],
                                   frame->data[2], frame->linesize[2],
                                   buffer, width * 4, width, height);
            }
        } else if (frame->format != AV_PIX_FMT_RGBA || (frame->format == AV_PIX_FMT_RGBA && needScale)) {
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
        }
    }

    av_packet_unref(pkt);
    av_packet_free(&pkt);
    av_free(pkt);

    av_frame_unref(frame);
    av_frame_free(&frame);
    av_free(frame);
}


void FFVideoReader::getNextFrame(const std::function<void(AVFrame *)>& frameArrivedCallback) {
    if (mAvFrame == nullptr) {
        mAvFrame = av_frame_alloc();
    }
    AVPacket *pkt = av_packet_alloc();
    AVFrame *frame = nullptr;
    while (true) {
        int ret = fetchAvPacket(pkt);
        if (ret < 0) {
            LOGE("[FFVideoReader], getNextFrame fetchAvPacket failed: %d", ret)
            break;
        }

        int sendRes = avcodec_send_packet(getCodecContext(), pkt);
        int receiveRes = avcodec_receive_frame(getCodecContext(), mAvFrame);
        LOGD("[FFVideoReader], getNextFrame receiveRes: %d, sendRes: %d, isKeyFrame: %d", receiveRes, sendRes, isKeyFrame(pkt))
        av_packet_unref(pkt);

        if (receiveRes == AVERROR(EAGAIN)) {
            continue;
        }

        if (receiveRes == 0) {
            frame = mAvFrame;
        }
        break;
    }
    av_packet_free(&pkt);

    if (frameArrivedCallback) {
        frameArrivedCallback(frame);
    }
    av_frame_unref(mAvFrame);
}

int FFVideoReader::getRotate(AVStream *stream) {
    AVDictionaryEntry *tag = nullptr;

    while ((tag = av_dict_get(stream->metadata, "", tag, AV_DICT_IGNORE_SUFFIX))) {
        LOGW("[video] metadata: %s, %s", tag->key, tag->value)
    }

    tag = av_dict_get(stream->metadata, "rotate", nullptr, 0);
    LOGE("try getRotate from tag(rotate): %s", tag == nullptr ? "-1" : tag->value)
    int rotate;
    if (tag != nullptr) {
        rotate = atoi(tag->value);
    } else {
        uint8_t* displayMatrix = av_stream_get_side_data(stream,AV_PKT_DATA_DISPLAYMATRIX, nullptr);
        double theta = 0;
        if (displayMatrix) {
            theta = -av_display_rotation_get((int32_t*) displayMatrix);
        }
        rotate = (int)theta;
    }

    LOGE("getRotate: %d", rotate)
    if (rotate < 0) { // CCW -> CC(Clockwise)
        rotate %= 360;
        rotate += 360;
        LOGE("getRotate fix: %d", rotate)
    }

    return rotate < 0 ? 0 : rotate;
}

int FFVideoReader::getRotate() {
    return getRotate(mFtx->streams[mMediaInfo.videoIndex]);
}
