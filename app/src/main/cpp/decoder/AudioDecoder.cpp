#include "AudioDecoder.h"
#include "../Logger.h"

AudioDecoder::AudioDecoder(int index, AVFormatContext *ftx) {
    LOGI("AudioDecoder")
    mAudioIndex = index;
    mFtx = ftx;
}

AudioDecoder::~AudioDecoder() {
    LOGI("~AudioDecoder")
}

bool AudioDecoder::prepare() {
    AVCodecParameters *params = mFtx->streams[mAudioIndex]->codecpar;

    mAudioCodec = avcodec_find_decoder(params->codec_id);
    if (mAudioCodec == nullptr) {
        std::string msg = "[audio] not find audio decoder";
        if (mErrorMsgListener) {
            mErrorMsgListener(-1000, msg);
        }
        return false;
    }

    // init codec context
    mAudioCodecContext = avcodec_alloc_context3(mAudioCodec);
    if (!mAudioCodecContext) {
        std::string msg = "[audio] codec context alloc failed";
        if (mErrorMsgListener) {
            mErrorMsgListener(-2000, msg);
        }
        return false;
    }
    avcodec_parameters_to_context(mAudioCodecContext, params);

    // open codec
    int ret = avcodec_open2(mAudioCodecContext, mAudioCodec, nullptr);
    if (ret != 0) {
        std::string msg = "[audio] codec open failed";
        if (mErrorMsgListener) {
            mErrorMsgListener(-3000, msg);
        }
        return false;
    }

    mAvFrame = av_frame_alloc();

    LOGI("[audio] prepare, sample rate: %d, channels: %d, channel_layout: %" PRId64 ", fmt: %d",
         mAudioCodecContext->sample_rate, mAudioCodecContext->channels,
         mAudioCodecContext->channel_layout, mAudioCodecContext->sample_fmt)

    return true;
}

int AudioDecoder::decode(AVPacket *avPacket) {
    int res = avcodec_send_packet(mAudioCodecContext, avPacket);
    LOGI("[audio] avcodec_send_packet...pts: %" PRId64 ", dts: %" PRId64 ", res: %d", avPacket->pts, avPacket->dts, res)

    res = avcodec_receive_frame(mAudioCodecContext, mAvFrame);
    if (res != 0) {
        LOGE("[audio] avcodec_receive_frame err: %d", res)
        av_frame_unref(mAvFrame);
        return res;
    }
    LOGI("[audio] avcodec_receive_frame...pts: %" PRId64 ", format: %d", mAvFrame->pts, mAvFrame->format)

    if (mSwrContext == nullptr) {
        mSwrContext = swr_alloc_set_opts(
                nullptr,
                AV_CH_LAYOUT_STEREO,
                AV_SAMPLE_FMT_S16,
                44100,

                mAvFrame->channel_layout,
                AVSampleFormat(mAvFrame->format),
                mAvFrame->sample_rate,
                0,
                nullptr
        );
        int ret = swr_init(mSwrContext);
        LOGI("alloc swr context, sample_rate: %d, channel_layout: %" PRId64 ", nb_samples: %d, ret: %d",
             mAvFrame->sample_rate, mAvFrame->channel_layout, mAvFrame->nb_samples, ret)
    }

    int out_nb = (int) av_rescale_rnd(mAvFrame->nb_samples, 44100, mAvFrame->sample_rate, AV_ROUND_UP);
    int out_channels = av_get_channel_layout_nb_channels(AV_CH_LAYOUT_STEREO);
    int size = av_samples_get_buffer_size(nullptr, out_channels, out_nb, AV_SAMPLE_FMT_S16, 1);
    if (mAudioBuffer == nullptr) {
        LOGI("audio frame, out_channels: %d, out_nb: %d, size: %d, ", out_channels, out_nb, size)
        mAudioBuffer = (uint8_t *) av_malloc(size);
    }

    int nb = swr_convert(
            mSwrContext,
            &mAudioBuffer,
            size / out_channels,
            (const uint8_t**)mAvFrame->data,
            mAvFrame->nb_samples
            );

    mDataSize = nb * out_channels * av_get_bytes_per_sample(AV_SAMPLE_FMT_S16);
    LOGI("swr_convert, dataSize: %d, nb: %d, out_channels: %d", mDataSize, nb, out_channels)

    if (mOnFrameArrivedListener != nullptr) {
        mOnFrameArrivedListener(mAvFrame);
    }

    return 0;
}

void AudioDecoder::release() {
    if (mAudioBuffer != nullptr) {
        av_free(mAudioBuffer);
        mAudioBuffer = nullptr;
        LOGI("[audio] buffer...release")
    }

    if (mAvFrame != nullptr) {
        av_frame_free(&mAvFrame);
        av_freep(&mAvFrame);
        LOGI("[audio] av frame...release")
    }

    if (mSwrContext != nullptr) {
        swr_free(&mSwrContext);
        mSwrContext = nullptr;
        LOGI("[audio] sws context...release")
    }

    if (mAudioCodecContext != nullptr) {
        avcodec_close(mAudioCodecContext);
        avcodec_free_context(&mAudioCodecContext);
        mAudioCodecContext = nullptr;
        LOGI("[audio] codec...release")
    }
}

int AudioDecoder::getStreamIndex() const {
    return mAudioIndex;
}

void AudioDecoder::setErrorMsgListener(std::function<void(int, std::string &)> listener) {
    mErrorMsgListener = std::move(listener);
}

void AudioDecoder::setOnFrameArrived(std::function<void(AVFrame *)> listener) {
    mOnFrameArrivedListener = std::move(listener);
}
