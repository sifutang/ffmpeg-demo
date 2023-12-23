package com.xyq.libhwplayer

import android.util.Log
import android.view.Surface
import com.xyq.libbase.player.IPlayer
import com.xyq.libbase.player.IPlayerListener
import com.xyq.libhwplayer.reader.ImageInfo
import com.xyq.libhwplayer.reader.ReaderWrapper
import java.nio.ByteBuffer

class ImagePlayer: IPlayer {

    private var mListener: IPlayerListener? = null
    private var mRotate = 0
    private var mRgbaBuffer: ByteBuffer? = null
    private var mWidth = 0
    private var mHeight = 0
    private var mExtraLoader: ImageLoader? = null

    companion object {
        private const val TAG = "ImagePlayer"
        private const val FORMAT_RGBA = 0x02
    }

    interface ImageLoader {
        fun load(path: String): ImageInfo
    }

    fun setExtraLoader(loader: ImageLoader) {
        mExtraLoader = loader
    }

    override fun init() {
        Log.i(TAG, "init: ")
    }

    override fun setPlayerListener(listener: IPlayerListener) {
        mListener = listener
    }

    override fun prepare(path: String, surface: Surface?) {
        val reader = ReaderWrapper()
        var imageInfo = reader.load(path)
        if (imageInfo == null) {
            Log.w(TAG, "prepare: failed, try use extra loader: ${mExtraLoader != null}")
            imageInfo = mExtraLoader?.load(path)
        }
        if (imageInfo == null || !imageInfo.isValid()) {
            Log.e(TAG, "prepare: failed")
            return
        }

        mWidth = imageInfo.width
        mHeight = imageInfo.height
        mRotate = imageInfo.rotate
        mRgbaBuffer = imageInfo.data
        mListener?.onVideoTrackPrepared(mWidth, mHeight, -1.0)
    }

    override fun start() {
        Log.i(TAG, "start: ")
        mRgbaBuffer?.let {
            it.rewind()
            mListener?.onVideoFrameArrived(mWidth, mHeight, FORMAT_RGBA, it.array(), null, null)
            mListener?.onPlayComplete()
        }
    }

    override fun resume() {
        Log.i(TAG, "resume: ")
    }

    override fun pause() {
        Log.i(TAG, "pause: ")
    }

    override fun stop() {
        Log.i(TAG, "stop: ")
    }

    override fun release() {
        Log.i(TAG, "release: ")
    }

    override fun seek(position: Double): Boolean {
        Log.i(TAG, "seek: $position")
        return true
    }

    override fun setMute(mute: Boolean) {
        Log.i(TAG, "setMute: ")
    }

    override fun getRotate(): Int {
        return mRotate
    }

    override fun getDuration(): Double {
        return 0.0
    }

    override fun getMediaInfo(): String? {
        return null
    }
}