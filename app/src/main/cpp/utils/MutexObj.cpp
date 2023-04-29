//
// Created by 雪月清的随笔 on 2023/4/18.
//

#include "MutexObj.h"

MutexObj::MutexObj() {
    pthread_mutex_init(&mMutex, nullptr);
    pthread_cond_init(&mCond, nullptr);
}

MutexObj::~MutexObj() {
    pthread_mutex_destroy(&mMutex);
    pthread_cond_destroy(&mCond);
}

void MutexObj::wakeUp() {
    pthread_mutex_lock(&mMutex);
    pthread_cond_signal(&mCond);
    pthread_mutex_unlock(&mMutex);
}

void MutexObj::wait() {
    pthread_mutex_lock(&mMutex);
    pthread_cond_wait(&mCond, &mMutex);
    pthread_mutex_unlock(&mMutex);
}
