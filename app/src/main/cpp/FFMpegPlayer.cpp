#include "FFMpegPlayer.h"

FFMpegPlayer::FFMpegPlayer() {
    LOGI("FFMpegPlayer")
    pthread_mutex_init(&mMutex, nullptr);
    pthread_cond_init(&mCond, nullptr);
}

FFMpegPlayer::~FFMpegPlayer() {
    mJvm = nullptr;
    pthread_mutex_destroy(&mMutex);
    pthread_cond_destroy(&mCond);
    LOGI("~FFMpegPlayer")
}

bool FFMpegPlayer::prepare(JNIEnv *env, std::string &path, jobject surface) {
    // step0: register jvm to ffmpeg for mediacodec decoding
    if (mJvm == nullptr) {
        env->GetJavaVM(&mJvm);
    }
    av_jni_set_java_vm(mJvm, nullptr);

    // step1: alloc format context
    mFtx = avformat_alloc_context();

    // step2: open input file
    int ret = avformat_open_input(&mFtx, path.c_str(), nullptr, nullptr);
    if (ret < 0) {
        env->CallVoidMethod(jPlayerListenerObj, onCallError, ret, env->NewStringUTF(av_err2str(ret)));
        return false;
    }

    // step3: find video stream index
    avformat_find_stream_info(mFtx, nullptr);
    int videoIndex = -1;
    int audioIndex = -1;
    for (int i = 0; i < mFtx->nb_streams; i++) {
        if (mFtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_VIDEO) {
            videoIndex = i;
        } else if (mFtx->streams[i]->codecpar->codec_type == AVMEDIA_TYPE_AUDIO) {
            audioIndex = i;
        }
    }

    if (videoIndex < 0) {
        std::string msg = "not find video stream";
        env->CallVoidMethod(jPlayerListenerObj, onCallError, ret, env->NewStringUTF(msg.c_str()));
        return false;
    }

    // step4: prepare video decoder
    mVideoDecoder = new VideoDecoder(videoIndex, mFtx);
    mVideoPacketQueue = new AVPacketQueue(50);
    mVideoThread = new std::thread(&FFMpegPlayer::VideoDecodeLoop, this);
    mVideoDecoder->setErrorMsgListener([](int err, std::string &msg) {
        LOGE("[video] err code: %d, msg: %s", err, msg.c_str())
    });

    bool prepared = mVideoDecoder->prepare(surface);
    mIsRunning = prepared;
    mHasAbort = false;

    // prepare audio decoder
    if (audioIndex > 0) {
        mAudioDecoder = new AudioDecoder(audioIndex, mFtx);
        mAudioPacketQueue = new AVPacketQueue(50);
        mAudioThread = new std::thread(&FFMpegPlayer::AudioDecodeLoop, this);
        mAudioDecoder->setErrorMsgListener([](int err, std::string &msg) {
            LOGE("[audio] err code: %d, msg: %s", err, msg.c_str())
        });
        mAudioDecoder->prepare();
    }

    env->CallVoidMethod(jPlayerListenerObj, onCallPrepared, mVideoDecoder->getWidth(), mVideoDecoder->getHeight());
    return prepared;
}

void FFMpegPlayer::start(JNIEnv *env) {
    while (mIsRunning) {
        AVPacket *avPacket = av_packet_alloc();
        int ret = av_read_frame(mFtx, avPacket);
        if (ret != 0) {
            LOGE("read packet...end")
            av_packet_unref(avPacket);
            break;
        }

        if (mVideoDecoder && mVideoPacketQueue && avPacket->stream_index == mVideoDecoder->getStreamIndex()) {
            while (mVideoPacketQueue->isFull()) {
                mVideoPacketQueue->wait(10);
                LOGE("video queue is full, wait 10ms")
            }
            mVideoPacketQueue->push(avPacket);
        } else if (mAudioDecoder && mAudioPacketQueue && avPacket->stream_index == mAudioDecoder->getStreamIndex()) {
            while (mAudioPacketQueue->isFull()) {
                mAudioPacketQueue->wait(10);
                LOGE("audio queue is full, wait 10ms")
            }
            mAudioPacketQueue->push(avPacket);
        } else {
            av_packet_free(&avPacket);
            av_freep(&avPacket);
            LOGI("read packet...other")
        }
    }

    if (mIsRunning) {
        LOGI("decode...end")
        env->CallVoidMethod(jPlayerListenerObj, onCallCompleted);
        mIsRunning = false;
    } else {
        LOGI("decode...abort")
    }

    pthread_mutex_lock(&mMutex);
    pthread_cond_signal(&mCond);
    pthread_mutex_unlock(&mMutex);
}

void FFMpegPlayer::stop() {
    LOGE("stop, mIsRunning: %d", mIsRunning)
    if (mIsRunning) {
        mIsRunning = false;
        pthread_mutex_lock(&mMutex);
        pthread_cond_wait(&mCond, &mMutex);
        pthread_mutex_unlock(&mMutex);
    }

    mHasAbort = true;
    mStartTime = -1;
    LOGE("stop done")

    if (mVideoThread != nullptr) {
        LOGE("join video thread")
        mVideoThread->join();
        delete mVideoThread;
        mVideoThread = nullptr;
        LOGE("release video thread")
    }
    if (mVideoDecoder != nullptr) {
        delete mVideoDecoder;
        mVideoDecoder = nullptr;
    }

    if (mAudioThread != nullptr) {
        LOGE("join audio thread")
        mAudioThread->join();
        delete mAudioThread;
        mAudioThread = nullptr;
        LOGE("release audio thread")
    }
    if (mAudioDecoder != nullptr) {
        delete mAudioDecoder;
        mAudioDecoder = nullptr;
    }

    if (mFtx != nullptr) {
        avformat_close_input(&mFtx);
        avformat_free_context(mFtx);
        mFtx = nullptr;
        LOGI("format context...release")
    }
}

void FFMpegPlayer::sync(AVFrame *avFrame) {
    if (mVideoDecoder == nullptr) {
        return;
    }
    auto pts = avFrame->best_effort_timestamp;
    if (pts == AV_NOPTS_VALUE) {
        pts = 0;
        LOGE("AV_NOPTS_VALUE")
    }
    // s -> us
    pts = pts * av_q2d(mFtx->streams[mVideoDecoder->getStreamIndex()]->time_base) * 1000 * 1000;
    int64_t elapsedTime;
    if (mStartTime < 0) {
        mStartTime = av_gettime();
        elapsedTime = 0;
    } else {
        elapsedTime = av_gettime() - mStartTime;
    }
    int64_t diff = pts - elapsedTime;
    LOGI("video frame arrived, pts: %" PRId64 ", elapsedTime: %" PRId64 " diff: %" PRId64, pts, elapsedTime, diff)
    if (diff > 0) {
        av_usleep(diff);
    }
}

void FFMpegPlayer::doRender(JNIEnv *env, AVFrame *avFrame) {
    if (avFrame->format == AV_PIX_FMT_YUV420P) {
        if (!avFrame->data[0] || !avFrame->data[1] || !avFrame->data[2]) {
            LOGE("doRender failed, no yuv buffer")
            return;
        }

        int ySize = avFrame->width * avFrame->height;
        auto y = env->NewByteArray(ySize);
        env->SetByteArrayRegion(y, 0, ySize, reinterpret_cast<const jbyte *>(avFrame->data[0]));
        auto u = env->NewByteArray(ySize / 4);
        env->SetByteArrayRegion(u, 0, ySize / 4, reinterpret_cast<const jbyte *>(avFrame->data[1]));
        auto v = env->NewByteArray(ySize / 4);
        env->SetByteArrayRegion(v, 0, ySize / 4, reinterpret_cast<const jbyte *>(avFrame->data[2]));

        env->CallVoidMethod(jPlayerListenerObj, onFrameArrived,
                            avFrame->width, avFrame->height, y, u, v);

        env->DeleteLocalRef(y);
        env->DeleteLocalRef(u);
        env->DeleteLocalRef(v);
    } else if (avFrame->format == AV_PIX_FMT_NV12) {
        if (!avFrame->data[0] || !avFrame->data[1]) {
            LOGE("doRender failed, no nv21 buffer")
            return;
        }

        int ySize = avFrame->width * avFrame->height;
        auto y = env->NewByteArray(ySize);
        env->SetByteArrayRegion(y, 0, ySize, reinterpret_cast<const jbyte *>(avFrame->data[0]));
        auto uv = env->NewByteArray(ySize / 2);
        env->SetByteArrayRegion(uv, 0, ySize / 2, reinterpret_cast<const jbyte *>(avFrame->data[1]));

        env->CallVoidMethod(jPlayerListenerObj, onFrameArrived,
                            avFrame->width, avFrame->height, y, uv, nullptr);

        env->DeleteLocalRef(y);
        env->DeleteLocalRef(uv);
    } else if (avFrame->format == AV_PIX_FMT_MEDIACODEC) {
        av_mediacodec_release_buffer((AVMediaCodecBuffer *)avFrame->data[3], 1);
    } else if (avFrame->format == AV_SAMPLE_FMT_FLTP) {
        if (mAudioDecoder) {
            int dataSize = mAudioDecoder->mDataSize;
            if (dataSize > 0) {
                auto jByteArray = env->NewByteArray(dataSize);
                env->SetByteArrayRegion(jByteArray, 0, dataSize, reinterpret_cast<const jbyte *>(mAudioDecoder->mAudioBuffer));
                env->CallVoidMethod(jPlayerListenerObj, onFrameArrived,
                                    dataSize, -1, jByteArray, nullptr, nullptr);
                env->DeleteLocalRef(jByteArray);
            }
        }
    }
}

void FFMpegPlayer::registerPlayerListener(JNIEnv *env, jobject listener) {
    if (listener != nullptr) {
        jclass jclazz = env->GetObjectClass(listener);
        if (!jclazz) {
            return;
        }

        jPlayerListenerObj = env->NewGlobalRef(listener);

        onCallPrepared = env->GetMethodID(jclazz, "onPrepared", "(II)V");
        onCallError = env->GetMethodID(jclazz, "onError", "(ILjava/lang/String;)V");
        onCallCompleted = env->GetMethodID(jclazz, "onCompleted", "()V");
        onFrameArrived = env->GetMethodID(jclazz, "onFrameArrived", "(II[B[B[B)V");
    } else {
        if (jPlayerListenerObj != nullptr) {
            env->DeleteGlobalRef(jPlayerListenerObj);
            jPlayerListenerObj = nullptr;
        }
        onCallPrepared = nullptr;
        onCallError = nullptr;
        onCallCompleted = nullptr;
        onFrameArrived = nullptr;
    }
}

void FFMpegPlayer::VideoDecodeLoop() {
    if (mVideoDecoder == nullptr || mVideoPacketQueue == nullptr) {
        return;
    }

    JNIEnv *env = nullptr;
    if (mJvm->GetEnv((void **)&env, JNI_VERSION_1_4) == JNI_EDETACHED) {
        mJvm->AttachCurrentThread(&env, nullptr);
        LOGE("[video] AttachCurrentThread")
    }
    mVideoDecoder->setOnFrameArrived([this, env](AVFrame *frame) {
        if (!mHasAbort) {
            sync(frame);
            doRender(env, frame);
        } else {
            LOGE("[video] setOnFrameArrived, has abort")
        }
    });

    while (true) {
        if (mHasAbort) {
            LOGE("[video] has abort...")
            break;
        }

        while (mVideoPacketQueue->isEmpty()) {
            mVideoPacketQueue->wait(-1);
            LOGE("[video] no packet, wait...")
        }

        AVPacket *packet = mVideoPacketQueue->pop();
        int ret = -1;
        if (packet != nullptr) {
            ret = mVideoDecoder->decode(packet);
            av_packet_free(&packet);
            av_freep(&packet);
        }
        if (ret == AVERROR_EOF) {
            LOGE("VideoDecodeLoop AVERROR_EOF")
            break;
        }
    }

    mVideoPacketQueue->clear();
    delete mVideoPacketQueue;
    mVideoPacketQueue = nullptr;

    mJvm->DetachCurrentThread();
    LOGE("[video] DetachCurrentThread");
}

void FFMpegPlayer::AudioDecodeLoop() {
    if (mAudioDecoder == nullptr || mAudioPacketQueue == nullptr) {
        return;
    }

    JNIEnv *env = nullptr;
    if (mJvm->GetEnv((void **)&env, JNI_VERSION_1_4) == JNI_EDETACHED) {
        mJvm->AttachCurrentThread(&env, nullptr);
        LOGE("[audio] AttachCurrentThread")
    }

    mAudioDecoder->setOnFrameArrived([this, env](AVFrame *frame) {
        if (!mHasAbort) {
            doRender(env, frame);
        } else {
            LOGE("[audio] setOnFrameArrived, has abort")
        }
    });

    while (true) {
        if (mHasAbort) {
            LOGE("[audio] has abort...")
            break;
        }

        while (mAudioPacketQueue->isEmpty()) {
            mAudioPacketQueue->wait(-1);
            LOGE("[audio] no packet, wait...")
        }

        AVPacket *packet = mAudioPacketQueue->pop();
        int ret = -1;
        if (packet != nullptr) {
            ret = mAudioDecoder->decode(packet);
            av_packet_free(&packet);
            av_freep(&packet);
        }
        if (ret == AVERROR_EOF) {
            LOGE("AudioDecodeLoop AVERROR_EOF")
            break;
        }
    }

    mAudioPacketQueue->clear();
    delete mAudioPacketQueue;
    mAudioPacketQueue = nullptr;

    mJvm->DetachCurrentThread();
    LOGE("[audio] DetachCurrentThread");
}
