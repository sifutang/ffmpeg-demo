#ifndef FFMPEGDEMO_IMAGEDEF_H
#define FFMPEGDEMO_IMAGEDEF_H

#define FMT_VIDEO_NV12          0x01
#define FMT_VIDEO_YUV420        0x02
#define FMT_VIDEO_MEDIACODEC    0x03
#define FMT_AUDIO_PCM           0x04

typedef struct _tag_NativeAvData {

    int width;
    int height;
    int format;
    uint8_t *datas[3];
    int lizeSize[3];

    _tag_NativeAvData() {
        width = 0;
        height = 0;
        format = FMT_VIDEO_YUV420;

        datas[0] = nullptr;
        datas[1] = nullptr;
        datas[2] = nullptr;

        lizeSize[0] = 0;
        lizeSize[1] = 0;
        lizeSize[2] = 0;
    }

} NativeAvData;

#endif //FFMPEGDEMO_IMAGEDEF_H
