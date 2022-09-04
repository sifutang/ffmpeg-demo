#include "FFMpegPlayer.h"

FFMpegPlayer::FFMpegPlayer() {
    LOGI("FFMpegPlayer")
    pthread_mutex_init(&mMutex, nullptr);
    pthread_cond_init(&mCond, nullptr);
}

FFMpegPlayer::~FFMpegPlayer() {
    mJvm = nullptr;
    mPlayerJni.reset();
    pthread_mutex_destroy(&mMutex);
    pthread_cond_destroy(&mCond);
    LOGI("~FFMpegPlayer")
}

void FFMpegPlayer::init(JNIEnv *env, jobject thiz) {
    jclass jclazz = env->GetObjectClass(thiz);
    if (jclazz == nullptr) {
        return;
    }

    mPlayerJni.reset();
    mPlayerJni.instance = env->NewGlobalRef(thiz);
    mPlayerJni.onVideoPrepared = env->GetMethodID(jclazz, "onNative_videoTrackPrepared", "(II)V");
    mPlayerJni.onVideoFrameArrived = env->GetMethodID(jclazz, "onNative_videoFrameArrived", "(II[B[B[B)V");

    mPlayerJni.onAudioPrepared = env->GetMethodID(jclazz, "onNative_audioTrackPrepared", "()V");
    mPlayerJni.onAudioFrameArrived = env->GetMethodID(jclazz, "onNative_audioFrameArrived", "([BIDZ)V");
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
        LOGE("avformat_open_input failed, ret: %d, err: %s", ret, av_err2str(ret))
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

    mHasAbort = false;
    bool videoPrepared = false;
    // step4: prepare video decoder
    if (videoIndex >= 0) {
        mHasVideoStream = true;
        mVideoDecoder = new VideoDecoder(videoIndex, mFtx, false);
        mVideoPacketQueue = new AVPacketQueue(50);
        mVideoThread = new std::thread(&FFMpegPlayer::VideoDecodeLoop, this);
        mVideoDecoder->setErrorMsgListener([](int err, std::string &msg) {
            LOGE("[video] err code: %d, msg: %s", err, msg.c_str())
        });

        mVideoDecoder->setSurface(surface);
        videoPrepared = mVideoDecoder->prepare();
        if (mPlayerJni.instance != nullptr && mPlayerJni.onVideoPrepared != nullptr) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoPrepared, mVideoDecoder->getWidth(), mVideoDecoder->getHeight());
        }
    }

    bool audioPrePared = false;
    // prepare audio decoder
    if (audioIndex >= 0) {
        mHasAudioStream = true;
        mAudioDecoder = new AudioDecoder(audioIndex, mFtx);
        mAudioPacketQueue = new AVPacketQueue(50);
        mAudioThread = new std::thread(&FFMpegPlayer::AudioDecodeLoop, this);
        mAudioDecoder->setErrorMsgListener([](int err, std::string &msg) {
            LOGE("[audio] err code: %d, msg: %s", err, msg.c_str())
        });
        audioPrePared = mAudioDecoder->prepare();
        if (mPlayerJni.instance != nullptr && mPlayerJni.onAudioPrepared != nullptr) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onAudioPrepared);
        }
    }
    bool prepared = videoPrepared || audioPrePared;
    LOGI("videoPrepared: %d, audioPrePared: %d", videoPrepared, audioPrePared)
    if (prepared) {
        mPlayerState = PlayerState::PREPARE;
    }
    return prepared;
}

void FFMpegPlayer::start() {
    LOGI("FFMpegPlayer::start, state: %d", mPlayerState)
    if (mPlayerState != PlayerState::PREPARE) {  // prepared failed
        return;
    }
    mPlayerState = PlayerState::START;
    if (mReadPacketThread == nullptr) {
        mReadPacketThread = new std::thread(&FFMpegPlayer::ReadPacketLoop, this);
    }
}

void FFMpegPlayer::stop() {
    LOGI("FFMpegPlayer::stop")
    // wakeup read packet thread
    mPlayerState = PlayerState::STOP;
    wakeup();

    if (mReadPacketThread != nullptr) {
        LOGE("join read thread")
        mReadPacketThread->join();
        delete mReadPacketThread;
        mReadPacketThread = nullptr;
        LOGE("release read thread")
    }

    mHasAbort = true;
    mVideoSeekPos = -1;
    mAudioSeekPos = -1;

    // release video res
    if (mVideoThread != nullptr) {
        LOGE("join video thread")
        if (mVideoPacketQueue) {
            mVideoPacketQueue->notify();
        }
        mVideoThread->join();
        delete mVideoThread;
        mVideoThread = nullptr;
    }
    if (mVideoDecoder != nullptr) {
        delete mVideoDecoder;
        mVideoDecoder = nullptr;
    }
    LOGE("release video res")

    // release audio res
    if (mAudioThread != nullptr) {
        LOGE("join audio thread")
        if (mAudioPacketQueue) {
            mAudioPacketQueue->notify();
        }
        mAudioThread->join();
        delete mAudioThread;
        mAudioThread = nullptr;
    }
    if (mAudioDecoder != nullptr) {
        delete mAudioDecoder;
        mAudioDecoder = nullptr;
    }
    LOGE("release audio res")

    if (mFtx != nullptr) {
        avformat_close_input(&mFtx);
        avformat_free_context(mFtx);
        mFtx = nullptr;
        LOGI("format context...release")
    }
}

void FFMpegPlayer::ReadPacketLoop() {
    LOGI("FFMpegPlayer::ReadPacketLoop start")
    while (mPlayerState != PlayerState::STOP) {
        if (mPlayerState == PlayerState::PAUSE) {
            wait();
        }

        // check is seek
        while (mVideoSeekPos >= 0 || mAudioSeekPos >= 0) {
            LOGE("seek wait...mVideoSeekPos: %f, mAudioSeekPos: %f", mVideoSeekPos, mAudioSeekPos)
            wait();
        }

        // read packet to queue
        mPlayerState = PlayerState::PLAYING;
        bool isEnd = readAvPacket() != 0;
        if (isEnd) {
            LOGE("read av packet end")
            mPlayerState = PlayerState::PAUSE;
        }
    }
    LOGI("FFMpegPlayer::ReadPacketLoop end")
}

int FFMpegPlayer::readAvPacket() {
    AVPacket *avPacket = av_packet_alloc();
    int ret = av_read_frame(mFtx, avPacket);
    if (ret != 0) {
        LOGE("read packet...end or failed: %d", ret)
        av_packet_unref(avPacket);
        return -1;
    }

    if (mVideoDecoder && mVideoPacketQueue && avPacket->stream_index == mVideoDecoder->getStreamIndex() && mVideoSeekPos < 0) {
        while (mVideoPacketQueue->isFull()) {
            mVideoPacketQueue->wait(10);
            LOGE("video queue is full, wait 10ms")
        }
        mVideoPacketQueue->push(avPacket);
    } else if (mAudioDecoder && mAudioPacketQueue && avPacket->stream_index == mAudioDecoder->getStreamIndex() && mAudioSeekPos < 0) {
        while (mAudioPacketQueue->isFull()) {
            mAudioPacketQueue->wait(10);
            LOGE("audio queue is full, wait 10ms")
        }
        mAudioPacketQueue->push(avPacket);
        LOGE("push audio packet")
    } else {
        av_packet_free(&avPacket);
        av_freep(&avPacket);
    }
    return 0;
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
        if (!mHasAbort && mVideoDecoder) {
            mVideoDecoder->avSync(frame);
            doRender(env, frame);
        } else {
            LOGE("[video] setOnFrameArrived, has abort")
        }
    });

    while (true) {
        if (mVideoSeekPos >= 0) {
            mVideoPacketQueue->clear();
            mVideoDecoder->seek(mVideoSeekPos);
            mVideoSeekPos = -1;
            wakeup();
            LOGE("clear video queue via seek")
        }

        while (!mHasAbort && mVideoPacketQueue->isEmpty() && mVideoSeekPos < 0) {
            LOGE("[video] no packet, wait...")
            mVideoPacketQueue->wait();
        }

        if (mHasAbort) {
            LOGE("[video] has abort...")
            break;
        }

        AVPacket *packet = mVideoPacketQueue->pop();
        if (packet != nullptr) {
            int ret = mVideoDecoder->decode(packet);
            av_packet_free(&packet);
            av_freep(&packet);
            if (ret == AVERROR_EOF) {
                LOGE("VideoDecodeLoop AVERROR_EOF")
                break;
            }
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
        if (!mHasAbort && mAudioDecoder) {
            mAudioDecoder->avSync(frame);
            doRender(env, frame);
        } else {
            LOGE("[audio] setOnFrameArrived, has abort")
        }
    });

    while (true) {
        if (mAudioSeekPos >= 0) {
            mAudioPacketQueue->clear();
            mAudioDecoder->seek(mAudioSeekPos);
            mAudioSeekPos = -1;
            wakeup();
            LOGE("clear audio queue via seek")
        }

        while (!mHasAbort && mAudioPacketQueue->isEmpty() && mAudioSeekPos < 0) {
             LOGE("[audio] no packet, wait...")
             mAudioPacketQueue->wait();
        }

        if (mHasAbort) {
            LOGE("[audio] has abort...")
            break;
        }

        AVPacket *packet = mAudioPacketQueue->pop();
        if (packet != nullptr) {
            int ret = mAudioDecoder->decode(packet);
            av_packet_free(&packet);
            av_freep(&packet);
            if (ret == AVERROR_EOF) {
                LOGE("AudioDecodeLoop AVERROR_EOF")
                break;
            }
        }
    }

    mAudioPacketQueue->clear();
    delete mAudioPacketQueue;
    mAudioPacketQueue = nullptr;

    mJvm->DetachCurrentThread();
    LOGE("[audio] DetachCurrentThread");
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

        if (mPlayerJni.instance != nullptr && mPlayerJni.onVideoFrameArrived != nullptr) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoFrameArrived,
                                avFrame->width, avFrame->height, y, u, v);
        }

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

        if (mPlayerJni.instance != nullptr && mPlayerJni.onVideoFrameArrived != nullptr) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoFrameArrived,
                                avFrame->width, avFrame->height, y, uv, nullptr);
        }

        env->DeleteLocalRef(y);
        env->DeleteLocalRef(uv);
    } else if (avFrame->format == AV_PIX_FMT_MEDIACODEC) {
        av_mediacodec_release_buffer((AVMediaCodecBuffer *)avFrame->data[3], 1);
    } else if (avFrame->format == AV_SAMPLE_FMT_FLTP) {
        if (mAudioDecoder) {
            int dataSize = mAudioDecoder->mDataSize;
            double timestamp = mAudioDecoder->mCurTimeStampMs;
            bool flushRender = mAudioDecoder->mNeedFlushRender;
            if (dataSize > 0) {
                auto jByteArray = env->NewByteArray(dataSize);
                env->SetByteArrayRegion(jByteArray, 0, dataSize, reinterpret_cast<const jbyte *>(mAudioDecoder->mAudioBuffer));

                if (mPlayerJni.instance != nullptr && mPlayerJni.onAudioFrameArrived != nullptr) {
                    env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onAudioFrameArrived, jByteArray, dataSize, timestamp, flushRender);
                }
                env->DeleteLocalRef(jByteArray);
            }
        }
    }
}

double FFMpegPlayer::getDuration() {
    if (mAudioDecoder != nullptr) {
        return mAudioDecoder->getDuration();
    }

    if (mVideoDecoder != nullptr) {
        return mVideoDecoder->getDuration();
    }
    return 0;
}

bool FFMpegPlayer::seek(double timeS) {
    bool ret = true;
    if (mHasVideoStream) {
        mVideoSeekPos = timeS;
    }
    if (mHasAudioStream) {
        mAudioSeekPos = timeS;
    }
    // clear queue first
    if (mVideoPacketQueue != nullptr) {
        mVideoPacketQueue->clear();
    }
    if (mAudioPacketQueue != nullptr) {
        mAudioPacketQueue->clear();
    }
    LOGI("seek to: %f, player state: %d", timeS, mPlayerState)
    return ret;
}

void FFMpegPlayer::lock() {
    pthread_mutex_lock(&mMutex);
}

void FFMpegPlayer::unlock() {
    pthread_mutex_unlock(&mMutex);
}

void FFMpegPlayer::wakeup() {
    pthread_mutex_lock(&mMutex);
    pthread_cond_signal(&mCond);
    pthread_mutex_unlock(&mMutex);
}

void FFMpegPlayer::wait() {
    pthread_mutex_lock(&mMutex);
    pthread_cond_wait(&mCond, &mMutex);
    pthread_mutex_unlock(&mMutex);
}