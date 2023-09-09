#include "BaseDecoder.h"
#include "../utils/Logger.h"

BaseDecoder::BaseDecoder(int index, AVFormatContext *ftx) {
    mStreamIndex = index;
    mFtx = ftx;
    mTimeBase = mFtx->streams[index]->time_base;
    mDuration = mFtx->streams[index]->duration * av_q2d(mTimeBase);
    LOGE("[BaseDecoder], index: %d, duration: %f, time base: {num: %d, den: %d}",
         index, mDuration, mTimeBase.num, mTimeBase.den);
    if (mDuration < 0.0) {
        mDuration = 0.0;
    }
}

BaseDecoder::~BaseDecoder() = default;

double BaseDecoder::getDuration() {
    return mFtx->duration * av_q2d(AV_TIME_BASE_Q);
}

bool BaseDecoder::prepare() {
    return false;
}

int BaseDecoder::decode(AVPacket *packet) {
    return 0;
}

void BaseDecoder::release() {

}

void BaseDecoder::setErrorMsgListener(std::function<void(int, std::string &)> listener) {
    mErrorMsgListener = std::move(listener);
}

void BaseDecoder::setOnFrameArrived(std::function<void(AVFrame *)> listener) {
    mOnFrameArrivedListener = std::move(listener);
}

int BaseDecoder::getStreamIndex() const {
    return mStreamIndex;
}

void BaseDecoder::avSync(AVFrame *frame) {
}

int BaseDecoder::seek(double pos) {
    return -1;
}

void BaseDecoder::flush() {
    if (mCodecContext != nullptr) {
        avcodec_flush_buffers(mCodecContext);
    }
}

bool BaseDecoder::isNeedResent() const {
    return mNeedResent;
}

void BaseDecoder::needFixStartTime() {
    mFixStartTime = true;
}

AVCodecContext *BaseDecoder::getCodecContext() {
    return mCodecContext;
}

AVRational BaseDecoder::getTimeBase() {
    return mTimeBase;
}
