package com.xyq.ffmpegdemo.player

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.Visualizer
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.xyq.ffmpegdemo.render.*
import com.xyq.ffmpegdemo.render.core.CameraDrawer
import com.xyq.ffmpegdemo.render.model.RenderData
import com.xyq.ffmpegdemo.utils.CommonUtils
import com.xyq.ffmpegdemo.view.AudioVisualizeView
import java.nio.ByteBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot

class FFPlayer(private val mContext: Context,
               private val mGlSurfaceView: GLSurfaceView,
               private val mAudioVisualizeView: AudioVisualizeView): GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "FFPlayer"
    }

    enum class State {
        IDLE,
        INIT,
        PREPARE,
        START,
        RESUME,
        PAUSE,
        STOP,
        RELEASE
    }

    enum class Filter {
        GRID
    }

    private var mNativePtr = -1L

    private var mAudioTrack: AudioTrack? = null
    private var mVisualizer: Visualizer? = null

    private val mUseHWDecoder = true
    private val mRenderManager = RenderManager(mContext)
    private var mWaterMarkBitmap: Bitmap? = null

    private var mSurface: Surface? = null

    private var mState = State.IDLE

    private var mDuration = -1.0

    private var mVideoRotate = 0

    private var mPath = ""

    private var mIsPlayComplete = false

    interface FFPlayerListener {
        fun onProgress(timestamp: Double)

        fun onComplete()
    }

    private var mFFPlayerListener: FFPlayerListener? = null

    init {
        mNativePtr = nativeInit()
        mState = State.INIT

        mGlSurfaceView.setEGLContextClientVersion(2)
        mGlSurfaceView.setRenderer(this)
        mGlSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        mWaterMarkBitmap = CommonUtils.generateTextBitmap("雪月清的随笔", 20f, mContext)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: ")
        mRenderManager.init()
        mRenderManager.setWaterMark(mWaterMarkBitmap!!)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: $width, $height")
        mRenderManager.setSurfaceSize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.d(TAG, "onDrawFrame: ")
        mRenderManager.draw()
    }

    fun setFilterProgress(value: Float) {
        mRenderManager.setGreyFilterProgress(value)
    }

    fun setListener(listener: FFPlayerListener?) {
        mFFPlayerListener = listener
    }

    fun prepare(path: String) {
        if (path.isEmpty()) {
            throw IllegalStateException("must first call setDataSource")
        }

        Log.i(TAG, "prepare: ")
        if (mUseHWDecoder) {
            mSurface?.let {
                it.release()
                mSurface = null
            }

            val drawer = mRenderManager.take(RenderManager.RenderFormat.OES, mContext)
            mRenderManager.makeCurrent(RenderManager.RenderFormat.OES)
            mGlSurfaceView.requestRender()

            val st = (drawer as CameraDrawer).getSurfaceTexture()
            st.setOnFrameAvailableListener {
                Log.d(TAG, "setOnFrameAvailableListener")
                mGlSurfaceView.requestRender()
            }
            mSurface = Surface(st)
        }

        nativePrepare(mNativePtr, path, mSurface)
        mState = State.PREPARE
        mPath = path
        mIsPlayComplete = false

        mDuration = getDuration()
        mVideoRotate = nativeGetRotate(mNativePtr)
        mRenderManager.setVideoRotate(mVideoRotate)
        Log.i(TAG, "prepare: done, duration: $mDuration, rotate: $mVideoRotate")
    }

    /**
     * get file duration, time is s
     */
    fun getDuration(): Double {
        if (mState < State.PREPARE) {
            throw IllegalStateException("not prepared")
        }

        if (mDuration < 0) {
            mDuration = nativeGetDuration(mNativePtr)
        }
        return mDuration
    }

    fun isPlayComplete(): Boolean {
        return mIsPlayComplete
    }

    /**
     * seek to position, time is s
     */
    fun seek(position: Double): Boolean {
        if (mState < State.PREPARE || mState >= State.STOP) {
            return false
        }

        mIsPlayComplete = false
        return nativeSeek(mNativePtr, position)
    }

    fun setMute(v: Boolean) {
        if (mState < State.PREPARE || mState >= State.STOP) {
            return
        }

        nativeSetMute(mNativePtr, v)
    }

    fun setFilter(filter: Filter, enable: Boolean) {
        if (mState < State.PREPARE || mState >= State.STOP) {
            return
        }

        nativeSetFilter(mNativePtr, filter.ordinal, enable)
    }

    fun start() {
        nativeStart(mNativePtr)
        mState = State.START
    }

    fun resume() {
        if (mState == State.PAUSE) {
            nativeResume(mNativePtr)
            mState = State.RESUME
        } else {
            Log.e(TAG, "resume: failed, state: $mState")
        }

        enableAudioVisualizer(true)
    }

    fun pause() {
        if (mState == State.START || mState == State.RESUME) {
            nativePause(mNativePtr)
            mState = State.PAUSE
        } else {
            Log.e(TAG, "pause: failed, state: $mState")
        }

        enableAudioVisualizer(false)
    }

    fun stop() {
        if (mState == State.STOP) {
            Log.e(TAG, "has stopped")
            return
        }
        Log.i(TAG, "stop: ")
        mState = State.STOP
        mDuration = -1.0
        mVideoRotate = 0
        mIsPlayComplete = false

        val visualizer = mVisualizer
        mVisualizer = null
        visualizer?.enabled = false
        visualizer?.release()

        val audioTrack = mAudioTrack
        mAudioTrack = null
        audioTrack?.stop()
        audioTrack?.release()

        nativeStop(mNativePtr)
    }

    fun release() {
        mPath = ""
        mState = State.RELEASE
        nativeRelease(mNativePtr)
        mNativePtr = -1
        mSurface?.release()
        mRenderManager.release()
    }

    private fun initAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        mAudioTrack = AudioTrack(
            AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build(),
            AudioFormat.Builder().setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .build(),
            bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        mAudioTrack!!.play()
        Log.i(TAG, "initAudioTrack: audio buffer size: $bufferSize")
    }

    private fun initAudioVisualizer() {
        val sessionId = mAudioTrack!!.audioSessionId
        val sizeRange = Visualizer.getCaptureSizeRange()
        val maxRate = Visualizer.getMaxCaptureRate()
        Log.i(TAG, "sessionId: $sessionId, " +
                "getCaptureSizeRange: ${Arrays.toString(sizeRange)}, maxRate: $maxRate")
        mVisualizer = Visualizer(sessionId)
        mVisualizer!!.captureSize = sizeRange[1] // use max range
        mVisualizer!!.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
            }

            override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                if (fft != null) {
                    val n = fft.size
                    val magnitudes = FloatArray(n / 2 + 1)
                    magnitudes[0] = abs(fft[0].toFloat())  // DC
                    magnitudes[n / 2] = abs(fft[1].toFloat()) // Nyquist
                    for (k in 1 until n / 2) {
                        magnitudes[k] = hypot(fft[k * 2].toDouble(), fft[k * 2 + 1].toDouble()).toFloat()
                    }
                    mAudioVisualizeView.setFftAudioData(magnitudes)
                }
            }
        }, maxRate / 2, false, true)
    }

    private fun enableAudioVisualizer(enable: Boolean) {
        mVisualizer?.enabled = true
    }

    private fun onNative_videoTrackPrepared(width: Int, height: Int) {
        Log.i(TAG, "onNative_videoTrackPrepared: $width, $height")
        mRenderManager.setVideoSize(width, height)
    }

    private fun onNative_videoFrameArrived(width: Int, height: Int, format: Int, y: ByteArray?, u: ByteArray?, v: ByteArray?) {
        val fmt = mRenderManager.convert(format)
        Log.d(TAG, "onNative_videoFrameArrived: $width, $height, fmt: $fmt")

        val renderData = RenderData(
            width, height,
            y?.let { ByteBuffer.wrap(y) },
            u?.let { ByteBuffer.wrap(u) },
            v?.let { ByteBuffer.wrap(v) },
        )
        mRenderManager.pushVideoData(fmt, renderData)
        mGlSurfaceView.requestRender()
    }

    private fun onNative_audioTrackPrepared() {
        Log.i(TAG, "onNative_audioTrackPrepared: ")
        // audio track
        initAudioTrack()

        // audio visualizer
        initAudioVisualizer()
        enableAudioVisualizer(true)
    }

    /**
     * buffer: audio sample
     * size: audio size
     * timestamp: ms
     */
    private fun onNative_audioFrameArrived(buffer: ByteArray?, size: Int, timestamp: Double, flush: Boolean) {
        buffer?.apply {
            if (flush) {
                mAudioTrack?.flush()
                Log.w(TAG, "onNative_audioFrameArrived: flush audio track")
            }
            val code = mAudioTrack?.write(buffer, 0, size, AudioTrack.WRITE_NON_BLOCKING)
            Log.d(TAG, "onNative_audioFrameArrived, size: $size, timestamp: ${timestamp}ms, code: $code")
            mFFPlayerListener?.onProgress(timestamp / 1000)
        }
    }

    private fun onNative_playComplete() {
        mState = State.PAUSE
        mIsPlayComplete = true
        enableAudioVisualizer(false)
        mFFPlayerListener?.onComplete()
    }

    private external fun nativeInit(): Long

    private external fun nativeSeek(handle: Long, position: Double): Boolean

    private external fun nativeSetMute(handle: Long, mute: Boolean)

    private external fun nativeSetFilter(handle: Long, filter: Int, enable: Boolean)

    private external fun nativePrepare(handle: Long, path: String, surface: Surface?): Boolean

    private external fun nativeStart(handle: Long)

    private external fun nativeResume(handle: Long)

    private external fun nativePause(handle: Long)

    private external fun nativeStop(handle: Long)

    private external fun nativeRelease(handle: Long)

    private external fun nativeGetDuration(handle: Long): Double

    private external fun nativeGetRotate(handle: Long): Int
}