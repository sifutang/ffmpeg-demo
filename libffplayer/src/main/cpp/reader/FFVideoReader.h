#ifndef FFMPEGDEMO_FFVIDEOREADER_H
#define FFMPEGDEMO_FFVIDEOREADER_H

#include "FFReader.h"
#include <functional>

class FFVideoReader: public FFReader {

public:
    FFVideoReader();
    ~FFVideoReader();

    bool init(std::string &path) override;

    static int getRotate(AVStream *stream);

    int getRotate();

    void getFrame(int64_t pts, int width, int height, uint8_t *buffer, bool precise = true);

    void getNextFrame(const std::function<void(AVFrame *)>& frameArrivedCallback);

private:
    bool mInit = false;

    int64_t mLastPts = -1;

    SwsContext *mSwsContext = nullptr;

    uint8_t *mScaleBuffer = nullptr;
    int64_t mScaleBufferSize = -1;

    AVFrame *mAvFrame = nullptr;
};


#endif //FFMPEGDEMO_FFVIDEOREADER_H
