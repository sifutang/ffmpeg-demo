package com.xyq.ffmpegdemo.player

import android.content.Context
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Surface
import com.xyq.ffmpegdemo.PlayerListener
import com.xyq.ffmpegdemo.render.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class FFPlayer(private val mContext: Context,
               private val mGlSurfaceView: GLSurfaceView): GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "FFPlayer"
    }

    private var mNativePtr = -1L

    private var mSurfaceWidth = -1
    private var mSurfaceHeight = -1

    private var mVideoWidth = -1
    private var mVideoHeight = -1

    private var mAudioTrack: AudioTrack? = null
    private var mDrawer: IDrawer? = null

    private var mSurface: Surface? = null

    init {
        mNativePtr = nativeInit()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: ")
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        mDrawer?.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: $width, $height")
        mSurfaceWidth = width
        mSurfaceHeight = height
        mDrawer?.setWorldSize(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        mDrawer?.draw()
    }

    fun setFilterProgress(value: Float) {
        if (mDrawer is CameraDrawer) {
            (mDrawer as CameraDrawer).setFilterProgress(value)
        }
    }

    fun init() {
        // 默认ffmpeg使用硬解
        mDrawer = CameraDrawer(mContext)

        mGlSurfaceView.setEGLContextClientVersion(2)
        mGlSurfaceView.setRenderer(this)
        mGlSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
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
        registerPlayerListener(mNativePtr, listener)
        nativePrepare(mNativePtr, path, mSurface)
    }

    fun start() {
        nativeStart(mNativePtr)
    }

    fun stop() {
        Log.e(TAG, "stop: ", )
        val audioTrack = mAudioTrack
        mAudioTrack = null
        audioTrack?.stop()
        audioTrack?.release()

        nativeStop(mNativePtr)
        registerPlayerListener(mNativePtr, null)
    }

    fun release() {
        nativeRelease(mNativePtr)
        mNativePtr = -1
        mSurface?.release()
        mDrawer?.release()
    }

    private val listener: PlayerListener = object : PlayerListener {
        override fun onPrepared(width: Int, height: Int) {
            Log.i(TAG, "onPrepared: $width, $height")
            mVideoWidth = width
            mVideoHeight = height
            mDrawer?.setVideoSize(mVideoWidth, mVideoHeight)

            // audio
            val bufferSize = AudioTrack.getMinBufferSize(44100, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            Log.e(TAG, "onPrepared: audio buffer size: $bufferSize")
            mAudioTrack = AudioTrack(
                AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_MUSIC).build(),
                AudioFormat.Builder().setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .build(),
                bufferSize, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            mAudioTrack!!.play()
        }

        override fun onFrameArrived(
            width: Int,
            height: Int,
            y: ByteArray?,
            u: ByteArray?,
            v: ByteArray?
        ) {
            // audio
            if (width != -1 && height == -1 && y != null && u == null && v == null) {
                mAudioTrack?.write(y, 0, width)
                Log.i(TAG, "onFrameArrived: audio arrived, size: $width")
                return
            }

            // video
            Log.i(TAG, "onFrameArrived: $width, $height, v is null: ${v == null}")
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
                mDrawer!!.init()
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

        override fun onError(code: Int, msg: String) {
            Log.e(TAG, "onError: $code, err: $msg")
        }

        override fun onCompleted() {
            Log.i(TAG, "onCompleted: ")
        }
    }

    private external fun nativeInit(): Long

    private external fun registerPlayerListener(handle: Long, listener: PlayerListener?)

    private external fun nativePrepare(handle: Long, path: String, surface: Surface?): Boolean

    private external fun nativeStart(handle: Long)

    private external fun nativeStop(handle: Long)

    private external fun nativeRelease(handle: Long)
}