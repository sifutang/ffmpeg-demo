//
// Created by 雪月清的随笔 on 14/6/23.
//

#ifndef FFMPEGDEMO_FFCONVERTER_H
#define FFMPEGDEMO_FFCONVERTER_H

extern "C" {
#include "../vendor/ffmpeg/libavformat/avformat.h"
#include "../vendor/ffmpeg//libavcodec/avcodec.h"
#include "../vendor/ffmpeg/libavutil/imgutils.h"
#include "../vendor/ffmpeg/libswscale/swscale.h"
}

#include "../settings/CompileSettings.h"

static AVMediaType getAvMediaType(MediaType type) {
    if (type == MEDIA_TYPE_VIDEO) {
        return AVMEDIA_TYPE_VIDEO;
    } else if (type == MEDIA_TYPE_AUDIO) {
        return AVMEDIA_TYPE_AUDIO;
    }
    return AVMEDIA_TYPE_UNKNOWN;
}

static AVPixelFormat getAvPixelFormat(PixelFormat format) {
    if (format == PIX_FMT_RGB8) {
        return AV_PIX_FMT_RGB8;
    }
    return AV_PIX_FMT_NONE;
}

static AVCodecID getAvCodecID(ENCODE_TYPE type) {
    if (type == ENCODE_TYPE_GIF) {
        return AV_CODEC_ID_GIF;
    } else if (type == ENCODE_TYPE_H264) {
        return AV_CODEC_ID_H264;
    }
    return AV_CODEC_ID_NONE;
}

#endif //FFMPEGDEMO_FFCONVERTER_H
