package com.xyq.ffmpegdemo.player

import android.opengl.GLSurfaceView
import android.util.Log
import com.xyq.ffmpegdemo.PlayerListener
import com.xyq.ffmpegdemo.render.IDrawer
import com.xyq.ffmpegdemo.render.YuvDrawer

class FFPlayer {

    companion object {
        private const val TAG = "FFPlayer"
    }

    private var mNativePtr = -1L

    private var mVideoDrawer: IDrawer? = null
    private var mGlSurfaceView: GLSurfaceView? = null

    init {
        mNativePtr = nativeInit()
    }

    fun prepare(path: String, drawer: IDrawer, glSurfaceView: GLSurfaceView) {
        if (path.isEmpty()) {
            throw IllegalStateException("must first call setDataSource")
        }
        mVideoDrawer = drawer
        mGlSurfaceView = glSurfaceView

        registerPlayerListener(mNativePtr, listener)
        nativePrepare(mNativePtr, path)
    }

    fun start() {
        nativeStart(mNativePtr)
    }

    fun stop() {
        nativeStop(mNativePtr)
        registerPlayerListener(mNativePtr, null)
    }

    fun release() {
        nativeRelease(mNativePtr)
        mNativePtr = -1;
    }

    private val listener: PlayerListener = object : PlayerListener {
        override fun onPrepared(width: Int, height: Int) {
            Log.i(TAG, "onPrepared: $width, $height")
            mVideoDrawer?.setVideoSize(width, height)
        }

        override fun onFrameArrived(
            width: Int,
            height: Int,
            y: ByteArray?,
            u: ByteArray?,
            v: ByteArray?
        ) {
            Log.i(TAG, "onFrameArrived: $width, $height")
            mVideoDrawer?.let {
                (it as YuvDrawer).pushYuv(width, height, y, u, v)
            }
            mGlSurfaceView?.requestRender()
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

    private external fun nativePrepare(handle: Long, path: String): Boolean

    private external fun nativeStart(handle: Long)

    private external fun nativeStop(handle: Long)

    private external fun nativeRelease(handle: Long)
}