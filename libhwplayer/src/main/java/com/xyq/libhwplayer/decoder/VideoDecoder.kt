package com.xyq.libhwplayer.decoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.xyq.libbase.player.IPlayerListener
import com.xyq.libhwplayer.extractor.IExtractor
import com.xyq.libhwplayer.extractor.VideoExtractor
import java.lang.Exception

class VideoDecoder {

    companion object {
        private const val TAG = "VideoDecoder"
    }

    private var mListener: IPlayerListener? = null

    private var mExtractor: IExtractor? = null
    private var mCodec: MediaCodec? = null

    private var mHandlerThread: HandlerThread? = null
    private var mHandler: Handler? = null

    private var mStartTimeStamp = 0L

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
            val cur = System.currentTimeMillis()
            val passTime = cur - mStartTimeStamp

            val pts = info.presentationTimeUs / 1000
            val diff = pts - passTime
            Log.i(TAG, "onOutputBufferAvailable: index: $index, pts: $pts, diff: $diff")
            if (diff > 0) {
                Thread.sleep(diff)
            }
            codec.releaseOutputBuffer(index, true)
            if (info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) {
                Log.e(TAG, "onOutputBufferAvailable: EOF")
                mListener?.onPlayComplete()
            } else {
                mListener?.onPlayProgress(pts.toDouble())
            }
        }

        override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "onError: ${e.message}")
        }

        override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
            Log.i(TAG, "onOutputFormatChanged: ")
        }
    }

    init {
        mHandlerThread = HandlerThread("Video-decode-thread")
        mHandlerThread?.start()
        mHandler = Handler(mHandlerThread!!.looper)
    }

    fun setPlayerListener(listener: IPlayerListener) {
        mListener = listener
    }

    fun prepare(path: String, surface: Surface?) {
        Log.i(TAG, "prepare: $path")
        mExtractor = VideoExtractor(path)
        mHandler?.post {
            try {
                val format = mExtractor?.getFormat()
                val width = format!!.getInteger(MediaFormat.KEY_WIDTH)
                val height = format.getInteger(MediaFormat.KEY_HEIGHT)
                mListener?.onVideoTrackPrepared(width, height, -1.0)

                val type = format.getString(MediaFormat.KEY_MIME)
                type?.let {
                    mCodec = MediaCodec.createDecoderByType(type).apply {
                        setCallback(mMediaCodecCallback, mHandler)
                        configure(mExtractor!!.getFormat(), surface!!, null, 0)
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
        mStartTimeStamp = System.currentTimeMillis()
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

    fun getRotate(): Int {
        val format =  mExtractor!!.getFormat()!!
        var res = 0
        try {
            res = format.getInteger(MediaFormat.KEY_ROTATION)
        } catch (e: Exception) {
            Log.e(TAG, "getRotate: ${e.message}")
        }
        Log.i(TAG, "getRotate: $res")
        return res
    }

    fun getDuration(): Double {
        var res = 0.0
        try {
            res = (mExtractor!!.getFormat()!!.getLong(MediaFormat.KEY_DURATION) / 1000 / 1000).toDouble()
        } catch (e: Exception) {
            Log.e(TAG, "getDuration: ${e.message}")
        }
        Log.i(TAG, "getDuration: $res")
        return res
    }
}