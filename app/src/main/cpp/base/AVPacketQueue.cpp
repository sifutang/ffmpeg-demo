#include "AVPacketQueue.h"
#include <ctime>
#include "../utils/Logger.h"

AVPacketQueue::AVPacketQueue(int64_t maxSize) {
    pthread_mutex_init(&mMutex, nullptr);
    pthread_cond_init(&mCond, nullptr);
    mMaxSize = maxSize;
}

AVPacketQueue::~AVPacketQueue() {
    LOGI("~AVPacketQueue")
    clear();
    pthread_mutex_destroy(&mMutex);
    pthread_cond_destroy(&mCond);
}

void AVPacketQueue::push(AVPacket *packet) {
    pthread_mutex_lock(&mMutex);
    mQueue.push(packet);
    pthread_mutex_unlock(&mMutex);
    notify();
}

AVPacket * AVPacketQueue::pop() {
    pthread_mutex_lock(&mMutex);
    AVPacket *packet = mQueue.front();
    mQueue.pop();
    pthread_mutex_unlock(&mMutex);
    return packet;
}

int AVPacketQueue::popTo(AVPacket *packet) {
    pthread_mutex_lock(&mMutex);
    bool isEmpty = mQueue.empty() && mQueue.size() <= 0;
    if (isEmpty) {
        pthread_mutex_unlock(&mMutex);
        return -1;
    }
    AVPacket *pkt = mQueue.front();
    int ref = av_packet_ref(packet, pkt);
    if (ref != 0) {
        LOGE("[AVPacketQueue], popTo failed, ref: %d", ref);
    }

    // flush packet
    if (pkt->size == 0 && pkt->data == nullptr) {
        // av_packet_ref出来后，packet->data不为nullptr了，这里强制reset下
        packet->size = 0;
        packet->data = nullptr;
    }

    av_packet_free(&pkt);
    av_free(pkt);
    mQueue.pop();
    pthread_mutex_unlock(&mMutex);
    return 0;
}

bool AVPacketQueue::isFull() {
    int64_t queueSize;
    pthread_mutex_lock(&mMutex);
    queueSize = (int)mQueue.size();
    pthread_mutex_unlock(&mMutex);

    return queueSize >= mMaxSize;
}

void AVPacketQueue::wait(unsigned int timeOutMs) {
    pthread_mutex_lock(&mMutex);

    if (timeOutMs > 0) {
        struct timespec abs_time{};
        struct timeval now_time{};
        gettimeofday(&now_time, nullptr);
        int n_sec = now_time.tv_usec * 1000 + (timeOutMs % 1000) * 1000000;
        abs_time.tv_nsec = n_sec % 1000000000;
        abs_time.tv_sec = now_time.tv_sec + n_sec / 1000000000 + timeOutMs / 1000;

        pthread_cond_timedwait(&mCond, &mMutex, &abs_time);
    } else {
        pthread_cond_wait(&mCond, &mMutex);
    }

    pthread_mutex_unlock(&mMutex);
}

void AVPacketQueue::notify() {
    pthread_mutex_lock(&mMutex);
    pthread_cond_signal(&mCond);
    pthread_mutex_unlock(&mMutex);
}

bool AVPacketQueue::isEmpty() {
    int64_t size;
    pthread_mutex_lock(&mMutex);
    size = (int64_t)mQueue.size();
    pthread_mutex_unlock(&mMutex);
    return size <= 0;
}

void AVPacketQueue::clear() {
    pthread_mutex_lock(&mMutex);
    int64_t size = mQueue.size();
    LOGI("[AVPacketQueue], clear queue, size: %" PRId64, size)
    while (!mQueue.empty() && size > 0) {
        AVPacket *packet = mQueue.front();
        if (packet != nullptr) {
            av_packet_free(&packet);
            av_freep(&packet);
        }
        mQueue.pop();
    }
    pthread_mutex_unlock(&mMutex);
    notify();
}

