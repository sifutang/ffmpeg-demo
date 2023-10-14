#include "FFReader.h"
#include "header/Logger.h"

FFReader::FFReader() {
    LOGI("FFReader")
}

FFReader::~FFReader() {
    release();
    LOGI("~FFReader")
}

bool FFReader::init(std::string &path) {
    mFtx = avformat_alloc_context();
    int ret = avformat_open_input(&mFtx, path.c_str(), nullptr, nullptr);
    if (ret < 0) {
        LOGE("[FFReader], avformat_open_input failed, ret: %d, err: %s", ret, av_err2str(ret))
        return false;
    }
    return true;
}

void FFReader::release() {
    for (int i = 0; i < 2; i++) {
        AVCodecContext *codecContext = mCodecContextArr[i];
        if (codecContext != nullptr) {
            avcodec_free_context(&codecContext);
        }
        mCodecContextArr[i] = nullptr;
        mCodecArr[i] = nullptr;
    }

    if (mFtx != nullptr) {
        avformat_close_input(&mFtx);
        avformat_free_context(mFtx);
        mFtx = nullptr;
    }

    mCurStreamIndex = -1;
    mVideoIndex = -1;
    mAudioIndex = -1;
    LOGI("[FFReader], release")
}

bool FFReader::selectTrack(TrackType type) {
    mCurTrackType = type;
    if (mCodecContextArr[type] != nullptr) {
        mCurStreamIndex = type == Track_Video ? mVideoIndex : mAudioIndex;
        return true;
    }

    if (mVideoIndex == -1 || mAudioIndex == -1) {
        avformat_find_stream_info(mFtx, nullptr);
        int codec_type;
        for (int i = 0; i < mFtx->nb_streams; i++) {
            codec_type = mFtx->streams[i]->codecpar->codec_type;
            if (codec_type == AVMEDIA_TYPE_VIDEO) {
                mVideoIndex = i;
            } else if (codec_type == AVMEDIA_TYPE_AUDIO) {
                mAudioIndex = i;
            }
        }
    }
    mCurStreamIndex = type == Track_Video ? mVideoIndex : mAudioIndex;
    LOGI("[FFReader], electTrack, type: %d, index: %d", type, mCurStreamIndex)
    int ret = prepare();
    return ret == 0;
}

int FFReader::prepare() {
    if (mCurStreamIndex < 0) {
        LOGE("[FFReader], prepare failed, index invalid.")
        return -1;
    }

    TrackType type = mCurTrackType;
    LOGI("[FFReader], prepare, index: %d, type: %d", mCurStreamIndex, type)
    AVCodecParameters *params = mFtx->streams[mCurStreamIndex]->codecpar;
    const AVCodec *codec = avcodec_find_decoder(params->codec_id);
    if (codec == nullptr) {
        LOGE("[FFReader], prepare failed, no codec")
        return -2;
    }
    mCodecArr[type] = codec;

    AVCodecContext *codecContext = avcodec_alloc_context3(mCodecArr[type]);
    if (codecContext == nullptr) {
        LOGE("[FFReader], prepare failed, no codec ctx")
        return -3;
    }
    mCodecContextArr[type] = codecContext;

    avcodec_parameters_to_context(codecContext, params);
    if (type == Track_Video) {
        mMediaInfo.videoIndex = mVideoIndex;
        mMediaInfo.video_time_base = mFtx->streams[mCurStreamIndex]->time_base;
        mMediaInfo.width = codecContext->width;
        mMediaInfo.height = codecContext->height;
        if (mDiscardType != DISCARD_NONE) {
            switch (mDiscardType) {
                case DISCARD_NONREF: {
                    codecContext->skip_frame = AVDISCARD_NONREF;
                    break;
                }
                case DISCARD_NONKEY: {
                    codecContext->skip_frame = AVDISCARD_NONKEY;
                    break;
                }
                default:
                    break;
            }
        }
    } else if (type == Track_Audio) {
        mMediaInfo.audioIndex = mAudioIndex;
        mMediaInfo.audio_time_base = mFtx->streams[mCurStreamIndex]->time_base;
    }

    int ret = avcodec_open2(codecContext, mCodecArr[type], nullptr);
    if (ret != 0) {
        LOGE("[FFReader], open codec failed, name: %s, ret: %d", mCodecArr[type]->name, ret)
    }
    return ret;
}

int FFReader::fetchAvPacket(AVPacket *pkt) {
    int ret = -1;
    while (av_read_frame(mFtx, pkt) == 0) {
        if (pkt->stream_index == mFtx->streams[mCurStreamIndex]->index) {
            ret = 0;
            break;
        }
        av_packet_unref(pkt);
    }
    if (ret != 0) {
        av_packet_unref(pkt);
    }
    return ret;
}

AVCodecContext *FFReader::getCodecContext() {
    return mCodecContextArr[mCurTrackType];
}

AVCodecParameters *FFReader::getCodecParameters() {
    return mFtx->streams[mCurStreamIndex]->codecpar;
}

int FFReader::getKeyFrameIndex(int64_t timestamp) {
    int64_t target = av_rescale_q((int64_t)(timestamp * AV_TIME_BASE), AV_TIME_BASE_Q, mFtx->streams[mCurStreamIndex]->time_base);
    int index = av_index_search_timestamp(
            mFtx->streams[mCurStreamIndex], target, AVSEEK_FLAG_BACKWARD);
    index = FFMAX(index, 0);
    return index;
}

void FFReader::flush() {
    LOGI("[FFReader], avcodec_flush_buffers")
    avcodec_flush_buffers(mCodecContextArr[mCurTrackType]);
}

void FFReader::seek(int64_t timestamp) {
    AVRational time_base = mFtx->streams[mCurStreamIndex]->time_base;
    int64_t seekPos = av_rescale_q((int64_t)(timestamp * AV_TIME_BASE), AV_TIME_BASE_Q, time_base);
    int ret = avformat_seek_file(mFtx, mCurStreamIndex, INT64_MIN, seekPos, INT64_MAX, AVSEEK_FLAG_BACKWARD | AVSEEK_FLAG_FRAME);
    if (ret < 0) {
        LOGE("[FFReader], avformat_seek_file failed, ret: %d, timestamp: %" PRId64, ret, timestamp)
    } else {
        LOGI("[FFReader], avformat_seek_file, ret: %d, timestamp: %" PRId64 ", seekPos: %" PRId64, ret, timestamp, seekPos)
    }
}

bool FFReader::isKeyFrame(AVPacket *pkt) {
    return pkt->flags & AV_PKT_FLAG_KEY;
}

MediaInfo FFReader::getMediaInfo() {
    return mMediaInfo;
}

double FFReader::getDuration() {
    int64_t duration = mFtx->streams[mCurStreamIndex]->duration;
    AVRational time_base = mFtx->streams[mCurStreamIndex]->time_base;
    return duration * av_q2d(time_base);
}

void FFReader::setDiscardType(DiscardType type) {
    mDiscardType = type;
}
