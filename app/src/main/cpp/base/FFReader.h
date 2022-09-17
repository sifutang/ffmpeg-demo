#ifndef FFMPEGDEMO_FFREADER_H
#define FFMPEGDEMO_FFREADER_H

#include <string>

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
}

enum TrackType {
    Track_Video,
    Track_Audio
};

/**
 * read AVPacket class
 */
class FFReader {

public:
    FFReader();
    ~FFReader();

    bool init(std::string &path);

    bool selectTrack(TrackType type);

    int fetchAvPacket(AVPacket *pkt);

    AVCodecContext *getCodecContext();

    void release();

private:
    AVFormatContext *mFtx = nullptr;

    const AVCodec *mCodecArr[2]{nullptr, nullptr};
    AVCodecContext *mCodecContextArr[2]{nullptr, nullptr};

    int mVideoIndex = -1;
    int mAudioIndex = -1;
    int mCurStreamIndex = -1;

    int prepare();
};


#endif //FFMPEGDEMO_FFREADER_H
