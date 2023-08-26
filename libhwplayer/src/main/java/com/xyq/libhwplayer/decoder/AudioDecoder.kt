package com.xyq.libhwplayer.decoder

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.xyq.libbase.player.IPlayerListener
import com.xyq.libhwplayer.extractor.AudioExtractor
import com.xyq.libhwplayer.extractor.IExtractor
import java.lang.Exception

class AudioDecoder {

    companion object {
        private const val TAG = "AudioDecoder"
    }

    private var mListener: IPlayerListener? = null

    private var mExtractor: IExtractor? = null
    private var mCodec: MediaCodec? = null

    private var mHandlerThread: HandlerThread? = null
    private var mHandler: Handler? = null

    private var mSampleRate = -1
    private var mChannels = 1
    private var mPcmEncodeBit = AudioFormat.ENCODING_PCM_16BIT

    private var mSampleArray: ByteArray? = null

    private val mMediaCodecCallback: MediaCodec.Callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
            val inputBuffer = codec.getInputBuffer(index)
            Log.i(TAG, "onInputBufferAvailable: index: $index")
            inputBuffer?.let {
                val sampleSize = mExtractor!!.readBuffer(it)
                if (sampleSize > 0) {
                    codec.queueInputBuffer(index, 0, sampleSize, mExtractor!!.getCurrentTimestamp(), 0)
                } else {
                    Log.i(TAG, "onInputBufferAvailable: BUFFER_FLAG_END_OF_STREAM")
                    codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                }
            }
        }

        override fun onOutputBufferAvailable(
            codec: MediaCodec,
            index: Int,
            info: MediaCodec.BufferInfo
        ) {
            Log.i(TAG, "onOutputBufferAvailable: $index")
            if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                Log.e(TAG, "onOutputBufferAvailable: EOF")
            }
            val outputBuffer = codec.getOutputBuffer(index)
            outputBuffer?.position(0)
            outputBuffer?.let {
                if (mSampleArray == null || mSampleArray!!.size != info.size) {
                    mSampleArray = ByteArray(info.size)
                }
                it.get(mSampleArray!!, 0, info.size)
                mListener?.onAudioFrameArrived(mSampleArray, info.size, false)
            }
            codec.releaseOutputBuffer(index, false)
            Thread.sleep(20)
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "onError: ${e.message}")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "onOutputFormatChanged: ")
        }
    }

    init {
        mHandlerThread = HandlerThread("Audio-decode-thread")
        mHandlerThread?.start()
        mHandler = Handler(mHandlerThread!!.looper)
    }

    fun setPlayerListener(listener: IPlayerListener) {
        mListener = listener
    }

    fun prepare(path: String) {
        mExtractor = AudioExtractor(path)
        val format = mExtractor?.getFormat()!!

        try {
            mChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            mPcmEncodeBit = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                } else {
                    AudioFormat.ENCODING_PCM_16BIT
                }
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }
        } catch (e: Exception) {
            Log.e(TAG, "prepare: ${e.message}")
        }
        Log.i(TAG, "prepare: $path, channels: $mChannels, sampleRate: $mSampleRate, bit: $mPcmEncodeBit")

        mHandler?.post {
            try {
                mListener?.onAudioTrackPrepared()
                val type = format.getString(MediaFormat.KEY_MIME)
                type?.let {
                    mCodec = MediaCodec.createDecoderByType(type).apply {
                        setCallback(mMediaCodecCallback, mHandler)
                        configure(mExtractor!!.getFormat(), null, null, 0)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "prepare: ${e.message}")
            }
        }
    }

    fun start() {
        mHandler?.post {
            mCodec?.start()
        }
    }

    fun stop() {
        mHandler?.post {
            mCodec?.stop()
            mCodec?.release()
            Log.i(TAG, "codec release: ")
        }
    }

    fun release() {
        mHandlerThread?.quitSafely()
    }
}