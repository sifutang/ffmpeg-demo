#ifndef FFMPEGDEMO_FFAVDECODER_H
#define FFMPEGDEMO_FFAVDECODER_H

#include "FFReader.h"

class FFAVDecoder {

public:
    FFAVDecoder();
    ~FFAVDecoder();

    void init(std::string &path, TrackType type);

    int decode(AVPacket *pkt);

    void release();

private:
    FFReader *mReader = nullptr;

};


#endif //FFMPEGDEMO_FFAVDECODER_H
