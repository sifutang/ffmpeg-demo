#ifndef FFMPEGDEMO_AVPACKETQUEUE_H
#define FFMPEGDEMO_AVPACKETQUEUE_H

#include <queue>
#include <pthread.h>

extern "C" {
#include "libavcodec/packet.h"
#include "libavutil/avutil.h"
}

class AVPacketQueue {

public:
    AVPacketQueue(int64_t maxSize);

    ~AVPacketQueue();

    void push(AVPacket *packet);

    AVPacket* pop();

    int popTo(AVPacket *packet);

    void clear();

    bool isFull();

    bool isEmpty();

    void wait(unsigned int timeOutMs = -1);

    void notify();

private:

    int64_t mMaxSize = INT64_MAX;

    std::queue<AVPacket *> mQueue;

    pthread_cond_t mCond{};
    pthread_mutex_t mMutex{};
};


#endif //FFMPEGDEMO_AVPACKETQUEUE_H
