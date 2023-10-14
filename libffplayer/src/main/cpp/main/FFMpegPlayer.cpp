#include "FFMpegPlayer.h"
#include "../vendor/nlohmann/json.hpp"

FFMpegPlayer::FFMpegPlayer() {
    LOGI("FFMpegPlayer")
    mMutexObj = std::make_shared<MutexObj>();
}

FFMpegPlayer::~FFMpegPlayer() {
    mJvm = nullptr;
    mPlayerJni.reset();
    LOGI("~FFMpegPlayer")
}

void FFMpegPlayer::init(JNIEnv *env, jobject thiz) {
    jclass jclazz = env->GetObjectClass(thiz);
    if (jclazz == nullptr) {
        return;
    }

    mPlayerJni.reset();
    mPlayerJni.instance = env->NewGlobalRef(thiz);
    mPlayerJni.onVideoPrepared = env->GetMethodID(jclazz, "onNative_videoTrackPrepared", "(IID)V");
    mPlayerJni.onVideoFrameArrived = env->GetMethodID(jclazz, "onNative_videoFrameArrived", "(III[B[B[B)V");

    mPlayerJni.onAudioPrepared = env->GetMethodID(jclazz, "onNative_audioTrackPrepared", "()V");
    mPlayerJni.onAudioFrameArrived = env->GetMethodID(jclazz, "onNative_audioFrameArrived", "([BIZ)V");
    mPlayerJni.onPlayCompleted = env->GetMethodID(jclazz, "onNative_playComplete", "()V");
    mPlayerJni.onPlayProgress = env->GetMethodID(jclazz, "onNative_playProgress", "(D)V");
}

bool FFMpegPlayer::prepare(JNIEnv *env, std::string &path, jobject surface) {
    // step0: register jvm to ffmpeg for mediacodec decoding
    if (mJvm == nullptr) {
        env->GetJavaVM(&mJvm);
    }
    // not call, use NDKMediaCodec on ffmpeg6.0,
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
        LOGI("select video stream, index: %d", videoIndex)
        mVideoDecoder = std::make_shared<VideoDecoder>(videoIndex, mFtx);
        mVideoPacketQueue = std::make_shared<AVPacketQueue>(50);
        mVideoThread = new std::thread(&FFMpegPlayer::VideoDecodeLoop, this);
        mVideoDecoder->setErrorMsgListener([](int err, std::string &msg) {
            LOGE("[video] err code: %d, msg: %s", err, msg.c_str())
        });

        mVideoDecoder->setSurface(surface);
        videoPrepared = mVideoDecoder->prepare();
        bool isHwDecoder = surface != nullptr;
        if (surface != nullptr && !videoPrepared) {
            mVideoDecoder->release();
            LOGE("[video] hw decoder prepare failed, fallback to software decoder")
            mVideoDecoder->setSurface(nullptr);
            videoPrepared = mVideoDecoder->prepare();
            isHwDecoder = false;
        }

        if (!isHwDecoder && mEnableGridFilter) {
            mGridFilter = std::make_unique<FFFilter>();
            std::string graphInArgs;
            std::string filterDesc;
            FFFilter::createGridFilterDesc(mVideoDecoder->getCodecContext(), mVideoDecoder->getTimeBase(), graphInArgs, filterDesc);
            if (!mGridFilter->init(graphInArgs, filterDesc)) {
                mGridFilter = nullptr;
            }
        }

        if (mPlayerJni.isValid()) {
            AVRational dar = mVideoDecoder->getDisplayAspectRatio();
            double ratio = dar.num / (double)dar.den;
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoPrepared, mVideoDecoder->getWidth(), mVideoDecoder->getHeight(), ratio);
        }
    }

    bool audioPrePared = false;
    // prepare audio decoder
    if (audioIndex >= 0) {
        LOGI("select audio stream, index: %d", audioIndex)
        mAudioDecoder = std::make_shared<AudioDecoder>(audioIndex, mFtx);
        audioPrePared = mAudioDecoder->prepare();
        if (audioPrePared) {
            mAudioPacketQueue = std::make_shared<AVPacketQueue>(50);
            mAudioThread = new std::thread(&FFMpegPlayer::AudioDecodeLoop, this);
            mAudioDecoder->setErrorMsgListener([](int err, std::string &msg) {
                LOGE("[audio] err code: %d, msg: %s", err, msg.c_str())
            });
            if (mPlayerJni.isValid()) {
                env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onAudioPrepared);
            }
        } else {
            mAudioDecoder = nullptr;
            mAudioPacketQueue = nullptr;
            mAudioThread = nullptr;
            LOGE("audio track prepared failed!!!")
        }
    }
    bool prepared = videoPrepared || audioPrePared;
    LOGI("videoPrepared: %d, audioPrePared: %d, path: %s", videoPrepared, audioPrePared, path.c_str())
    if (prepared) {
        updatePlayerState(PlayerState::PREPARE);
    }

    nlohmann::json j;
    j["path"] = path;
    LOGE("mediainfo json: %s", j.dump().c_str())
    return prepared;
}

void FFMpegPlayer::start() {
    LOGI("FFMpegPlayer::start, state: %d", mPlayerState)
    if (mPlayerState != PlayerState::PREPARE) {  // prepared failed
        return;
    }
    updatePlayerState(PlayerState::START);
    if (mReadPacketThread == nullptr) {
        mReadPacketThread = new std::thread(&FFMpegPlayer::ReadPacketLoop, this);
    }
}

void FFMpegPlayer::resume() {
    updatePlayerState(PlayerState::PLAYING);
    if(mVideoDecoder) {
        mVideoDecoder->needFixStartTime();
    }
    if (mAudioDecoder) {
        mAudioDecoder->needFixStartTime();
    }
    mMutexObj->wakeUp();
}

void FFMpegPlayer::pause() {
    updatePlayerState(PlayerState::PAUSE);
}

void FFMpegPlayer::stop() {
    LOGI("FFMpegPlayer::stop")
    // wakeup read packet thread and release it
    updatePlayerState(PlayerState::STOP);
    mMutexObj->wakeUp();
    if (mReadPacketThread != nullptr) {
        LOGE("join read thread")
        mReadPacketThread->join();
        delete mReadPacketThread;
        mReadPacketThread = nullptr;
        LOGE("release read thread")
    }

    mHasAbort = true;
    mIsMute = false;
    mIsSeek = false;
    mVideoSeekPos = -1;
    mAudioSeekPos = -1;

    // release video res
    if (mVideoThread != nullptr) {
        LOGE("join video thread")
        if (mVideoPacketQueue) {
            mVideoPacketQueue->clear();
        }
        mVideoThread->join();
        delete mVideoThread;
        mVideoThread = nullptr;
    }
    mVideoDecoder = nullptr;
    LOGE("release video res")

    // release audio res
    if (mAudioThread != nullptr) {
        LOGE("join audio thread")
        if (mAudioPacketQueue) {
            mAudioPacketQueue->clear();
        }
        mAudioThread->join();
        delete mAudioThread;
        mAudioThread = nullptr;
    }
    mAudioDecoder = nullptr;
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
            LOGW("FFMpegPlayer::ReadPacketLoop wait")
            mMutexObj->wait();
            LOGW("FFMpegPlayer::ReadPacketLoop wakeup")
            // double check stop state
            // or check pause state for pause & seek

            // check mIsSeek: play complete -> seek -> play
            if (mIsSeek && mVideoSeekPos < 0 && mAudioSeekPos < 0) {
                mIsSeek = false;
                LOGW("FFMpegPlayer::ReadPacketLoop, reset seek status")
            }
            continue;
        }

        // check is seek
        bool isSeeking = false;
        while (mVideoSeekPos >= 0 || mAudioSeekPos >= 0) {
            isSeeking = true;
            LOGI("seek wait...mVideoSeekPos: %f, mAudioSeekPos: %f", mVideoSeekPos, mAudioSeekPos)
            mMutexObj->wait();
        }
        if (isSeeking) {
            mIsSeek = false;
            LOGW("FFMpegPlayer::ReadPacketLoop, seek prepare has ready")
        }

        // read packet to queue
        updatePlayerState(PlayerState::PLAYING);
        bool isEnd = readAvPacketToQueue() != 0;
        if (isEnd) {
            LOGW("read av packet end, mPlayerState: %d", mPlayerState)
            if (mPlayerState == PlayerState::PLAYING) {
                updatePlayerState(PlayerState::PAUSE);
            }
        }
    }
    LOGI("FFMpegPlayer::ReadPacketLoop end")
}

int FFMpegPlayer::readAvPacketToQueue() {
    AVPacket *avPacket = av_packet_alloc();
    int ret = av_read_frame(mFtx, avPacket);
    bool suc = false;
    if (ret == 0) {
        if (mVideoDecoder && mVideoPacketQueue && avPacket->stream_index == mVideoDecoder->getStreamIndex()) {
            suc = pushPacketToQueue(avPacket, mVideoPacketQueue);
        } else if (mAudioDecoder && mAudioPacketQueue && avPacket->stream_index == mAudioDecoder->getStreamIndex()) {
            suc = pushPacketToQueue(avPacket, mAudioPacketQueue);
        }
    } else {
        // send flush packet
        AVPacket *videoFlushPkt = av_packet_alloc();
        videoFlushPkt->size = 0;
        videoFlushPkt->data = nullptr;
        if (!pushPacketToQueue(videoFlushPkt, mVideoPacketQueue)) {
            av_packet_free(&videoFlushPkt);
            av_freep(&videoFlushPkt);
        }

        AVPacket *audioFlushPkt = av_packet_alloc();
        audioFlushPkt->size = 0;
        audioFlushPkt->data = nullptr;
        if (!pushPacketToQueue(audioFlushPkt, mAudioPacketQueue)) {
            av_packet_free(&audioFlushPkt);
            av_freep(&audioFlushPkt);
        }
        LOGE("read packet...end or failed: %d", ret)
        ret = -1;
    }

    if (!suc) {
        LOGI("av_read_frame, other...pts: %" PRId64 ", index: %d", avPacket->pts, avPacket->stream_index)
        av_packet_free(&avPacket);
        av_freep(&avPacket);
    }
    return ret;
}

bool FFMpegPlayer::pushPacketToQueue(AVPacket *packet, const std::shared_ptr<AVPacketQueue>& queue) const {
    if (queue == nullptr) {
        return false;
    }

    bool suc = false;
    while (queue->isFull()) {
        queue->wait(10);
        LOGD("queue is full, wait 10ms, packet index: %d", packet->stream_index)
    }
    if (!mIsSeek) {
        queue->push(packet);
        suc = true;
    } else {
        LOGE("discard packet for seek, packet index: %d", packet->stream_index)
    }
    return suc;
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
            if (mAudioDecoder) {
                auto diff = mAudioDecoder->getTimestamp() - mVideoDecoder->getTimestamp();
                LOGW("[video] frame arrived, AV time diff: %" PRId64, diff)
            }
            AVFrame *finalFrame = nullptr;
            if (mGridFilter != nullptr) {
                finalFrame = mGridFilter->process(frame);
            }
            if (finalFrame == nullptr) {
                finalFrame = frame;
            }
            mVideoDecoder->avSync(frame);
            doRender(env, finalFrame);
            if (!mAudioDecoder && mPlayerJni.isValid()) { // no audio track
                double timestamp = mVideoDecoder->getTimestamp();
                env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onPlayProgress, timestamp);
            }
        } else {
            LOGE("[video] setOnFrameArrived, has abort")
        }
    });

    while (true) {
        if (mVideoSeekPos >= 0) {
            mVideoPacketQueue->clear();
            mVideoDecoder->seek(mVideoSeekPos);
            mVideoSeekPos = -1;
            LOGI("clear video queue via seek")
            mMutexObj->wakeUp();
        }

        while (!mHasAbort && mVideoPacketQueue->isEmpty() && mVideoSeekPos < 0) {
            LOGE("[video] no packet, wait...")
            mVideoPacketQueue->wait();
        }

        if (mHasAbort) {
            LOGE("[video] has abort...")
            break;
        }

        AVPacket *packet = av_packet_alloc();
        if (packet != nullptr) {
            int ret = mVideoPacketQueue->popTo(packet);
            if (ret == 0) {
                do {
                    ret = mVideoDecoder->decode(packet);
                } while (mVideoDecoder->isNeedResent());

                av_packet_unref(packet);
                av_packet_free(&packet);
                if (ret == AVERROR_EOF) {
                    LOGE("VideoDecodeLoop AVERROR_EOF")
                    if (!mAudioDecoder) { // 存在音轨以音频播放结束为准
                        onPlayCompleted(env);
                    }
                }
            } else {
                LOGE("VideoDecodeLoop pop packet failed...")
            }
        }
    }

    mVideoPacketQueue->clear();
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
            if (mPlayerJni.isValid()) {
                double timestamp = mAudioDecoder->getTimestamp();
                env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onPlayProgress, timestamp);
            }
        } else {
            LOGE("[audio] setOnFrameArrived, has abort")
        }
    });

    while (true) {
        if (mAudioSeekPos >= 0) {
            mAudioPacketQueue->clear();
            mAudioDecoder->seek(mAudioSeekPos);
            mAudioSeekPos = -1;
            LOGI("clear audio queue via seek")
            mMutexObj->wakeUp();
        }

        while (!mHasAbort && mAudioPacketQueue->isEmpty() && mAudioSeekPos < 0) {
             LOGE("[audio] no packet, wait...")
             mAudioPacketQueue->wait();
        }

        if (mHasAbort) {
            LOGE("[audio] has abort...")
            break;
        }

        AVPacket *packet = av_packet_alloc();
        if (packet != nullptr) {
            int ret = mAudioPacketQueue->popTo(packet);
            if (ret == 0) {
                do {
                    ret = mAudioDecoder->decode(packet);
                } while (mAudioDecoder->isNeedResent());

                av_packet_unref(packet);
                av_packet_free(&packet);
                if (ret == AVERROR_EOF) {
                    LOGE("AudioDecodeLoop AVERROR_EOF")
                    onPlayCompleted(env);
                }
            } else {
                LOGE("AudioDecodeLoop pop packet failed...")
            }
        }
    }

    mAudioPacketQueue->clear();
    mAudioPacketQueue = nullptr;

    mJvm->DetachCurrentThread();
    LOGE("[audio] DetachCurrentThread");
}

void FFMpegPlayer::checkStrideAndFill(JNIEnv *env, jbyteArray *component, int width, int height,
                                      int lineStride, int pixelStride, uint8_t *src) {
    int lineLength = width * pixelStride;
    if (lineLength >= lineStride) {
        env->SetByteArrayRegion(*component, 0, lineLength * height, reinterpret_cast<const jbyte *>(src));
    } else {
        uint8_t *pSrc = src;
        for (int i = 0; i < height; i++) {
            env->SetByteArrayRegion(*component, lineLength * i, lineLength, reinterpret_cast<const jbyte *>(pSrc));
            pSrc += lineStride;
        }
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
        checkStrideAndFill(env, &y, avFrame->width, avFrame->height, avFrame->linesize[0], 1, avFrame->data[0]);

        auto u = env->NewByteArray(ySize / 4);
        checkStrideAndFill(env, &u, avFrame->width / 2, avFrame->height / 2, avFrame->linesize[1], 1, avFrame->data[1]);

        auto v = env->NewByteArray(ySize / 4);
        checkStrideAndFill(env, &v, avFrame->width / 2, avFrame->height / 2, avFrame->linesize[2], 1, avFrame->data[2]);

        if (mPlayerJni.isValid()) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoFrameArrived,
                                avFrame->width, avFrame->height, FMT_VIDEO_YUV420, y, u, v);
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
        checkStrideAndFill(env, &y, avFrame->width, avFrame->height, avFrame->linesize[0], 1, avFrame->data[0]);

        auto uv = env->NewByteArray(ySize / 2);
        checkStrideAndFill(env, &uv, avFrame->width / 2, avFrame->height / 2, avFrame->linesize[1], 2, avFrame->data[1]);

        if (mPlayerJni.isValid()) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoFrameArrived,
                                avFrame->width, avFrame->height, FMT_VIDEO_NV12, y, uv, nullptr);
        }

        env->DeleteLocalRef(y);
        env->DeleteLocalRef(uv);
    } else if (avFrame->format == AV_PIX_FMT_RGBA) {
        if (!avFrame->data[0]) {
            LOGE("doRender failed, no rgba buffer")
            return;
        }

        int size = avFrame->width * avFrame->height * 4;
        auto rgba = env->NewByteArray(size);
        checkStrideAndFill(env, &rgba, avFrame->width, avFrame->height, avFrame->linesize[0], 4, avFrame->data[0]);

        if (mPlayerJni.isValid()) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoFrameArrived,
                                avFrame->width, avFrame->height, FMT_VIDEO_RGBA, rgba, nullptr, nullptr);
        }

        env->DeleteLocalRef(rgba);
    } else if (avFrame->format == AV_PIX_FMT_RGB24) {
        if (!avFrame->data[0]) {
            LOGE("doRender failed, no rgb24 buffer")
            return;
        }

        int size = avFrame->width * avFrame->height * 3;
        auto rgb24 = env->NewByteArray(size);
        checkStrideAndFill(env, &rgb24, avFrame->width, avFrame->height, avFrame->linesize[0], 3, avFrame->data[0]);

        if (mPlayerJni.isValid()) {
            env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onVideoFrameArrived,
                                avFrame->width, avFrame->height, FMT_VIDEO_RGB, rgb24, nullptr, nullptr);
        }

        env->DeleteLocalRef(rgb24);
    } else if (avFrame->format == AV_PIX_FMT_MEDIACODEC) {
        av_mediacodec_release_buffer((AVMediaCodecBuffer *)avFrame->data[3], 1);
    } else if (avFrame->format == AV_SAMPLE_FMT_FLTP) {
        int dataSize = mAudioDecoder->mDataSize;
        bool flushRender = mAudioDecoder->mNeedFlushRender;
        if (dataSize > 0) {
            uint8_t *audioBuffer = mAudioDecoder->mAudioBuffer;
            if (mIsMute) {
                memset(audioBuffer, 0, dataSize);
            }
            auto jByteArray = env->NewByteArray(dataSize);
            env->SetByteArrayRegion(jByteArray, 0, dataSize, reinterpret_cast<const jbyte *>(audioBuffer));

            if (mPlayerJni.isValid()) {
                env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onAudioFrameArrived, jByteArray, dataSize, flushRender);
            }
            env->DeleteLocalRef(jByteArray);
        }
    }
}

void FFMpegPlayer::setMute(bool mute) {
    mIsMute = mute;
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
    LOGI("seek to: %f, player state: %d", timeS, mPlayerState)
    mIsSeek = true;
    if (mVideoPacketQueue != nullptr) {
        mVideoSeekPos = timeS;
        mVideoPacketQueue->clear();
    }
    if (mAudioPacketQueue != nullptr) {
        mAudioSeekPos = timeS;
        mAudioPacketQueue->clear();
    }
    return true;
}

void FFMpegPlayer::updatePlayerState(PlayerState state) {
    if (mPlayerState != state) {
        LOGI("updatePlayerState from %d to %d", mPlayerState, state);
        mPlayerState = state;
    }
}

int FFMpegPlayer::getRotate() {
    if (mVideoDecoder) {
        return mVideoDecoder->getRotate();
    }
    return 0;
}

void FFMpegPlayer::onPlayCompleted(JNIEnv *env) {
    if (mPlayerJni.isValid()) {
        env->CallVoidMethod(mPlayerJni.instance, mPlayerJni.onPlayCompleted);
    }
}
