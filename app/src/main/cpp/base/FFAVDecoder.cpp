#include "FFAVDecoder.h"

FFAVDecoder::FFAVDecoder() = default;

FFAVDecoder::~FFAVDecoder() = default;

void FFAVDecoder::init(std::string &path, TrackType type) {
    mReader = new FFReader();
    mReader->init(path);
    mReader->selectTrack(type);
}

int FFAVDecoder::decode(AVPacket *pkt) {
    return 0;
}

void FFAVDecoder::release() {
    if (mReader != nullptr) {
        mReader->release();
        delete mReader;
        mReader = nullptr;
    }
}
