#include "FFReader.h"
#include "../utils/Logger.h"

FFReader::FFReader() = default;

FFReader::~FFReader() = default;

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
            avcodec_close(codecContext);
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

    TrackType type = mCurStreamIndex == mVideoIndex ? Track_Video : Track_Audio;
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

    int ret = avcodec_open2(codecContext, mCodecArr[type], nullptr);
    if (ret != 0) {
        LOGE("[FFReader], open codec failed, name: %s, ret: %d", mCodecArr[type]->name, ret)
    }
    return ret;
}

int FFReader::fetchAvPacket(AVPacket *pkt) {
    return av_read_frame(mFtx, pkt);
}

AVCodecContext *FFReader::getCodecContext() {
    TrackType type = mCurStreamIndex == mVideoIndex ? Track_Video : Track_Audio;
    return mCodecContextArr[type];
}
