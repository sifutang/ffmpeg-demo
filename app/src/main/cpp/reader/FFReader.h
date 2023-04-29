#ifndef FFMPEGDEMO_FFREADER_H
#define FFMPEGDEMO_FFREADER_H

#include <string>

extern "C" {
#include "libavformat/avformat.h"
#include "libavcodec/avcodec.h"
#include "libswscale/swscale.h"
#include "libavutil/imgutils.h"
}

enum TrackType {
    Track_Video,
    Track_Audio
};

typedef struct MediaInfo {
    // video
    int width = -1;
    int height = -1;
    int videoIndex = -1;
    AVRational video_time_base;

    // audio
    int audioIndex = -1;
    AVRational audio_time_base;

} MediaInfo;

/**
 * read AVPacket class
 */
class FFReader {

public:
    FFReader();
    virtual ~FFReader();

    virtual bool init(std::string &path);

    bool selectTrack(TrackType type);

    int fetchAvPacket(AVPacket *pkt);

    bool isKeyFrame(AVPacket *pkt);

    /**
     * 获取timestamp对应的关键帧index，基于BACKWARD
     * @param timestamp: 时间单位s
     * @return
     */
    int getKeyFrameIndex(int64_t timestamp);

    double getDuration();

    /**
     * seek
     * @param timestamp: 时间单位s
     */
    void seek(int64_t timestamp);

    void flush();

    void enableSkipNonRefFrame(bool enable);

    AVCodecContext *getCodecContext();

    MediaInfo getMediaInfo();

    void release();

private:
    AVFormatContext *mFtx = nullptr;

    const AVCodec *mCodecArr[2]{nullptr, nullptr};
    AVCodecContext *mCodecContextArr[2]{nullptr, nullptr};

    int mVideoIndex = -1;
    int mAudioIndex = -1;
    int mCurStreamIndex = -1;
    TrackType mCurTrackType = Track_Video;

    MediaInfo mMediaInfo;

    bool mSkipNonRefFrame = false;

    int prepare();
};


#endif //FFMPEGDEMO_FFREADER_H
