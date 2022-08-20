#include "BaseDecoder.h"


BaseDecoder::BaseDecoder(int index, AVFormatContext *ftx) {
    mStreamIndex = index;
    mFtx = ftx;
    mTimeBase = mFtx->streams[index]->time_base;
}

BaseDecoder::~BaseDecoder() = default;

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
