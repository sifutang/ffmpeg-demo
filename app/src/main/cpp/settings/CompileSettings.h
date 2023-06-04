//
// Created by 雪月清的随笔 on 14/6/23.
//

#ifndef FFMPEGDEMO_COMPILESETTINGS_H
#define FFMPEGDEMO_COMPILESETTINGS_H

enum ENCODE_TYPE {
    ENCODE_TYPE_H264,
    ENCODE_TYPE_GIF
};

enum PixelFormat {
    PIX_FMT_RGB8
};

enum MediaType {
    MEDIA_TYPE_VIDEO,
    MEDIA_TYPE_AUDIO
};

typedef struct CompileSettings {
    ENCODE_TYPE encodeType = ENCODE_TYPE_H264;

    int width = 0;

    int height = 0;

    PixelFormat pixelFormat = PIX_FMT_RGB8;

    MediaType mediaType = MEDIA_TYPE_VIDEO;

    int64_t bitRate = 4 * 1024 * 1024; // 4M

    int gopSize = 30;

    int maxBFrameCount = 0;

    int fps = 30;

    void operator=(const CompileSettings &settings) {
        this->encodeType = settings.encodeType;
        this->width = settings.width;
        this->height = settings.height;
        this->pixelFormat = settings.pixelFormat;
        this->mediaType = settings.mediaType;
        this->bitRate = settings.bitRate;
        this->gopSize = settings.gopSize;
        this->maxBFrameCount = settings.maxBFrameCount;
        this->fps = settings.fps;
    }

} CompileSettings;


#endif //FFMPEGDEMO_COMPILESETTINGS_H
