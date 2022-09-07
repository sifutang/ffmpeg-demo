package com.xyq.ffmpegdemo.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.Visualizer
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.xyq.ffmpegdemo.render.*
import com.xyq.ffmpegdemo.view.AudioVisualizeView
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot

class FFPlayer(context: Context,
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

    private var mNativePtr = -1L

    private var mSurfaceWidth = -1
    private var mSurfaceHeight = -1

    private var mVideoWidth = -1
    private var mVideoHeight = -1

    private var mAudioTrack: AudioTrack? = null
    private var mVisualizer: Visualizer? = null

    private var mDrawer: IDrawer? = null

    private var mSurface: Surface? = null

    private var mState = State.IDLE

    private var mDuration = -1.0

    interface FFPlayerListener {
        fun onProgress(timestamp: Double)

        fun onComplete()
    }

    private var mFFPlayerListener: FFPlayerListener? = null

    init {
        mNativePtr = nativeInit()
        mState = State.INIT

        // 默认ffmpeg使用硬解
        mDrawer = CameraDrawer(context)

        mGlSurfaceView.setEGLContextClientVersion(2)
        mGlSurfaceView.setRenderer(this)
        mGlSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: ")
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        mDrawer?.init(false)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: $width, $height")
        mSurfaceWidth = width
        mSurfaceHeight = height
        mDrawer?.setWorldSize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(23f / 255, 23f / 255, 23f / 255, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        mDrawer?.draw()
    }

    fun setFilterProgress(value: Float) {
        if (mDrawer is CameraDrawer) {
            (mDrawer as CameraDrawer).setFilterProgress(value)
        }
    }

    fun setListener(listener: FFPlayerListener?) {
        mFFPlayerListener = listener
    }

    fun prepare(path: String) {
        if (path.isEmpty()) {
            throw IllegalStateException("must first call setDataSource")
        }

        mSurface?.release()
        mSurface = null

        val st: SurfaceTexture?
        if (mDrawer is CameraDrawer) {
            st = (mDrawer as CameraDrawer).getSurfaceTexture()
            st.setOnFrameAvailableListener {
                Log.i(TAG, "setOnFrameAvailableListener")
                mGlSurfaceView.requestRender()
            }
            mSurface = Surface(st)
        }
        nativePrepare(mNativePtr, path, mSurface)
        mState = State.PREPARE

        mDuration = getDuration()
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

    /**
     * seek to position, time is s
     */
    fun seek(position: Double): Boolean {
        if (mState < State.PREPARE || mState >= State.STOP) {
            return false
        }

        return nativeSeek(mNativePtr, position)
    }

    fun setMute(v: Boolean) {
        if (mState < State.PREPARE || mState >= State.STOP) {
            return
        }

        nativeSetMute(mNativePtr, v)
    }

    fun start() {
        nativeStart(mNativePtr)
        mState = State.START
    }

    fun resume() {
        if (mState == State.PAUSE) {
            nativeResume(mNativePtr)
            mState = State.RESUME
        }
    }

    fun pause() {
        if (mState == State.START || mState == State.RESUME) {
            nativePause(mNativePtr)
            mState = State.PAUSE
        }
    }

    fun stop() {
        Log.e(TAG, "stop: ")
        mState = State.STOP
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
        mState = State.RELEASE
        nativeRelease(mNativePtr)
        mNativePtr = -1
        mSurface?.release()
        mDrawer?.release()
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
        Log.e(TAG, "initAudioTrack: audio buffer size: $bufferSize")
    }

    private fun initAudioVisualizer() {
        val sessionId = mAudioTrack!!.audioSessionId
        val sizeRange = Visualizer.getCaptureSizeRange()
        val maxRate = Visualizer.getMaxCaptureRate()
        Log.e(TAG, "sessionId: $sessionId, " +
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
        mVisualizer!!.enabled = true
    }

    private fun onNative_videoTrackPrepared(width: Int, height: Int) {
        Log.i(TAG, "onNative_videoTrackPrepared: $width, $height")
        mVideoWidth = width
        mVideoHeight = height
        mDrawer?.setVideoSize(mVideoWidth, mVideoHeight)
    }

    private fun onNative_videoFrameArrived(width: Int, height: Int, y: ByteArray?, u: ByteArray?, v: ByteArray?) {
        Log.i(TAG, "onNative_videoFrameArrived: $width, $height, v is null: ${v == null}")
        if (mDrawer is CameraDrawer) {
            mDrawer!!.release()
            mDrawer = null
            Log.e(TAG, "onFrameArrived: is yuv420: ${v != null}")
        }

        if (mDrawer == null) {
            mDrawer = if (v == null) {
                NV12Drawer()
            } else {
                YuvDrawer()
            }
            mDrawer!!.init(true)
            mDrawer!!.setVideoSize(mVideoWidth, mVideoHeight)
            mDrawer!!.setWorldSize(mSurfaceWidth, mSurfaceHeight)
        }

        if (mDrawer is NV12Drawer) {
            (mDrawer as NV12Drawer).pushNv21(width, height, y, u)
        } else {
            (mDrawer as YuvDrawer).pushYuv(width, height, y, u, v)
        }

        mGlSurfaceView.requestRender()
    }

    private fun onNative_audioTrackPrepared() {
        Log.i(TAG, "onNative_audioTrackPrepared: ")
        // audio track
        initAudioTrack()

        // audio visualizer
        initAudioVisualizer()
    }

    /**
     * buffer: audio sample
     * size: audio size
     * timestamp: ms
     */
    private fun onNative_audioFrameArrived(buffer: ByteArray?, size: Int, timestamp: Double, flush: Boolean, isEnd: Boolean) {
        buffer?.apply {
            if (flush) {
                mAudioTrack?.flush()
                Log.e(TAG, "onNative_audioFrameArrived: flush audio track")
            }
            val code = mAudioTrack?.write(buffer, 0, size, AudioTrack.WRITE_NON_BLOCKING)
            Log.i(TAG, "onNative_audioFrameArrived, size: $size, timestamp: ${timestamp}ms, code: $code, isEnd: $isEnd")
            mFFPlayerListener?.onProgress(timestamp / 1000)
            if (isEnd) {
                mFFPlayerListener?.onComplete()
            }
        }
    }

    private external fun nativeInit(): Long

    private external fun nativeGetDuration(handle: Long): Double

    private external fun nativeSeek(handle: Long, position: Double): Boolean

    private external fun nativeSetMute(handle: Long, mute: Boolean)

    private external fun nativePrepare(handle: Long, path: String, surface: Surface?): Boolean

    private external fun nativeStart(handle: Long)

    private external fun nativeResume(handle: Long)

    private external fun nativePause(handle: Long)

    private external fun nativeStop(handle: Long)

    private external fun nativeRelease(handle: Long)
}