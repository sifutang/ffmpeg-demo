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
import com.xyq.libbase.player.IPlayer
import com.xyq.libbase.player.IPlayerListener
import com.xyq.libffplayer.FFPlayer
import com.xyq.libhwplayer.HwPlayer
import com.xyq.librender.RenderManager
import com.xyq.librender.core.OesDrawer
import com.xyq.librender.filter.GreyFilter
import com.xyq.librender.filter.IFilter
import com.xyq.librender.filter.RadiusCornerFilter
import com.xyq.librender.model.RenderData
import com.xyq.libutils.CommonUtils
import java.nio.ByteBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.hypot

class MyPlayer(private val mContext: Context,
               private val mGlSurfaceView: GLSurfaceView,
               private val mConfig: PlayerConfig): GLSurfaceView.Renderer, IMediaPlayer, IPlayerListener {

    companion object {
        private const val TAG = "MyPlayer"
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

    private var mPlayerProxy: IPlayer = if (mConfig.decodeConfig == PlayerConfig.DecodeConfig.USE_FF_HW_DECODER || mConfig.decodeConfig == PlayerConfig.DecodeConfig.USE_FF_SW_DECODER) {
        FFPlayer()
    } else {
        HwPlayer()
    }

    private var mAudioTrack: AudioTrack? = null
    private var mVisualizer: Visualizer? = null

    private val mRenderManager = RenderManager(mContext)
    private var mWaterMarkBitmap: Bitmap? = null
    private var mGreyFilter: IFilter? = null
    private var mRadiusCornerFilter: IFilter? = null

    private var mSurface: Surface? = null

    private var mState = State.IDLE

    private var mDuration = -1.0

    private var mVideoRotate = 0

    private var mPath = ""

    private var mIsPlayComplete = false

    private var mMediaPlayerStatusListener: IMediaPlayerStatusListener? = null

    init {
        mPlayerProxy.init()
        mPlayerProxy.setPlayerListener(this)
        mState = State.INIT

        mGlSurfaceView.setEGLContextClientVersion(2)
        mGlSurfaceView.setRenderer(this)
        mGlSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        mWaterMarkBitmap = CommonUtils.generateTextBitmap("雪月清的随笔", 16f, mContext)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: ")
        mRenderManager.init()
        mRenderManager.setWaterMark(mWaterMarkBitmap!!)

        mGreyFilter = GreyFilter(mContext).apply {
            setVal(GreyFilter.VAL_PROGRESS, 0.5f)
        }
        mRadiusCornerFilter = RadiusCornerFilter(mContext).apply {
            setVal(RadiusCornerFilter.VAL_RADIUS, 50f)
        }
        mRenderManager.addFilter(mGreyFilter!!)
        mRenderManager.addFilter(mRadiusCornerFilter!!) // must be last filter
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: $width, $height")
        mRenderManager.setCanvasSize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        Log.d(TAG, "onDrawFrame: ")
        mRenderManager.draw()
    }

    fun setFilterProgress(value: Float) {
        val progress = CommonUtils.clamp(0f, 1f, value)
        mGreyFilter?.setVal(GreyFilter.VAL_PROGRESS, progress)
    }

    override fun setListener(listener: IMediaPlayerStatusListener?) {
        mMediaPlayerStatusListener = listener
    }

    override fun prepare(path: String) {
        if (path.isEmpty()) {
            throw IllegalStateException("must first call setDataSource")
        }

        Log.i(TAG, "prepare: ")
        if (mConfig.decodeConfig == PlayerConfig.DecodeConfig.USE_FF_HW_DECODER || mConfig.decodeConfig == PlayerConfig.DecodeConfig.USE_HW_DECODER) {
            mSurface?.let {
                it.release()
                mSurface = null
            }

            val drawer = mRenderManager.take(RenderManager.RenderFormat.OES, mContext)
            mRenderManager.makeCurrent(RenderManager.RenderFormat.OES)
            mGlSurfaceView.requestRender()

            val st = (drawer as OesDrawer).getSurfaceTexture()
            st.setOnFrameAvailableListener {
                Log.d(TAG, "setOnFrameAvailableListener")
                mGlSurfaceView.requestRender()
            }
            mSurface = Surface(st)
        }

        mPlayerProxy.prepare(path, mSurface)
        mState = State.PREPARE
        mPath = path
        mIsPlayComplete = false

        mDuration = getDuration()
        mVideoRotate = mPlayerProxy.getRotate()
        mRenderManager.setVideoRotate(mVideoRotate)
        mGreyFilter?.setVal(GreyFilter.VAL_PROGRESS, 0.5f)
        Log.i(TAG, "prepare: done, duration: $mDuration, rotate: $mVideoRotate")
    }

    /**
     * get file duration, time is s
     */
    override fun getDuration(): Double {
        if (mState < State.PREPARE) {
            throw IllegalStateException("not prepared")
        }

        if (mDuration < 0) {
            mDuration = mPlayerProxy.getDuration()
        }
        return mDuration
    }

    override fun isPlayComplete(): Boolean {
        return mIsPlayComplete
    }

    /**
     * seek to position, time is s
     */
    override fun seek(position: Double): Boolean {
        if (mState < State.PREPARE || mState >= State.STOP) {
            return false
        }

        mIsPlayComplete = false
        return mPlayerProxy.seek(position)
    }

    override fun setMute(mute: Boolean) {
        if (mState < State.PREPARE || mState >= State.STOP) {
            return
        }

        mPlayerProxy.setMute(mute)
    }

    override fun start() {
        mPlayerProxy.start()
        mState = State.START
    }

    override fun resume() {
        if (mState == State.PAUSE) {
            mPlayerProxy.resume()
            mState = State.RESUME
        } else {
            Log.e(TAG, "resume: failed, state: $mState")
        }

        enableAudioVisualizer(true)
    }

    override fun pause() {
        if (mState == State.START || mState == State.RESUME) {
            mPlayerProxy.pause()
            mState = State.PAUSE
        } else {
            Log.e(TAG, "pause: failed, state: $mState")
        }

        enableAudioVisualizer(false)
    }

    override fun stop() {
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

        mPlayerProxy.stop()
    }

    override fun release() {
        mPath = ""
        mState = State.RELEASE
        mPlayerProxy.release()
        mSurface?.release()
        mGlSurfaceView.queueEvent {
            mRenderManager.release()
            Log.i(TAG, "release: render res")
        }
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
                    mMediaPlayerStatusListener?.onFftAudioDataArrived(magnitudes)
                }
            }
        }, maxRate / 2, false, true)
    }

    private fun enableAudioVisualizer(enable: Boolean) {
        mVisualizer?.enabled = enable
    }

    override fun onVideoTrackPrepared(width: Int, height: Int) {
        Log.i(TAG, "onNative_videoTrackPrepared: $width, $height")
        mRenderManager.setVideoSize(width, height)
    }

    override fun onAudioTrackPrepared() {
        Log.i(TAG, "onNative_audioTrackPrepared: ")
        // audio track
        initAudioTrack()

        // audio visualizer
        initAudioVisualizer()
        enableAudioVisualizer(true)
    }

    override fun onVideoFrameArrived(
        width: Int,
        height: Int,
        format: Int,
        y: ByteArray?,
        u: ByteArray?,
        v: ByteArray?
    ) {
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

    override fun onAudioFrameArrived(buffer: ByteArray?, size: Int, flush: Boolean) {
        buffer?.apply {
            if (flush) {
                mAudioTrack?.flush()
                Log.w(TAG, "onNative_audioFrameArrived: flush audio track")
            }
            val code = mAudioTrack?.write(buffer, 0, size, AudioTrack.WRITE_NON_BLOCKING)
            Log.d(TAG, "onNative_audioFrameArrived, size: $size, code: $code")
        }
    }

    override fun onPlayProgress(timestamp: Double) {
        Log.d(TAG, "onNative_playProgress: ${timestamp}ms")
        mMediaPlayerStatusListener?.onProgress(timestamp / 1000)
    }

    override fun onPlayComplete() {
        Log.d(TAG, "onNative_playComplete: ")
        mState = MyPlayer.State.PAUSE
        mIsPlayComplete = true
        enableAudioVisualizer(false)
        mMediaPlayerStatusListener?.onComplete()
    }


}