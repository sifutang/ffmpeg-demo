#ifndef FFMPEGDEMO_FFVIDEOREADER_H
#define FFMPEGDEMO_FFVIDEOREADER_H

#include "FFReader.h"

class FFVideoReader: public FFReader{

public:
    FFVideoReader(std::string &path);
    ~FFVideoReader();

    void getFrame(int64_t pts, int width, int height, uint8_t *buffer);

private:
    bool mInit = false;

    int64_t mLastPts = -1;

    SwsContext *mSwsContext = nullptr;

};


#endif //FFMPEGDEMO_FFVIDEOREADER_H
