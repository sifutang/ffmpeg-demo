package com.xyq.libffplayer

import android.view.Surface
import com.xyq.libbase.player.IPlayer
import com.xyq.libbase.player.IPlayerListener

class FFPlayer: IPlayer {

    private var mNativePtr = -1L
    private var mListener: IPlayerListener? = null

    init {
        System.loadLibrary("ffplayer")
    }

    override fun init() {
        mNativePtr = nativeInit()
    }

    override fun setPlayerListener(listener: IPlayerListener) {
        mListener = listener
    }

    override fun prepare(path: String, surface: Surface?) {
        nativePrepare(mNativePtr, path, surface)
    }

    override fun start() {
        nativeStart(mNativePtr)
    }

    override fun resume() {
        nativeResume(mNativePtr)
    }

    override fun pause() {
        nativePause(mNativePtr)
    }

    override fun stop() {
        nativeStop(mNativePtr)
    }

    override fun release() {
        nativeRelease(mNativePtr)
        mNativePtr = -1
    }

    override fun seek(position: Double): Boolean {
        return nativeSeek(mNativePtr, position)
    }

    override fun setMute(mute: Boolean) {
        nativeSetMute(mNativePtr, mute)
    }

    override fun getRotate(): Int {
        return nativeGetRotate(mNativePtr)
    }

    override fun getDuration(): Double {
        return nativeGetDuration(mNativePtr)
    }

    private fun onNative_videoTrackPrepared(width: Int, height: Int, displayRatio: Double) {
        mListener?.onVideoTrackPrepared(width, height, displayRatio)
    }

    private fun onNative_videoFrameArrived(width: Int, height: Int, format: Int, y: ByteArray?, u: ByteArray?, v: ByteArray?) {
        mListener?.onVideoFrameArrived(width, height, format, y, u, v)
    }

    private fun onNative_audioTrackPrepared() {
        mListener?.onAudioTrackPrepared()
    }

    /**
     * buffer: audio sample
     * size: audio size
     * timestamp: ms
     */
    private fun onNative_audioFrameArrived(buffer: ByteArray?, size: Int, flush: Boolean) {
        mListener?.onAudioFrameArrived(buffer, size, flush)
    }

    private fun onNative_playProgress(timestamp: Double) {
        mListener?.onPlayProgress(timestamp)
    }

    private fun onNative_playComplete() {
        mListener?.onPlayComplete()
    }

    private external fun nativeInit(): Long

    private external fun nativeSeek(handle: Long, position: Double): Boolean

    private external fun nativeSetMute(handle: Long, mute: Boolean)

    private external fun nativePrepare(handle: Long, path: String, surface: Surface?): Boolean

    private external fun nativeStart(handle: Long)

    private external fun nativeResume(handle: Long)

    private external fun nativePause(handle: Long)

    private external fun nativeStop(handle: Long)

    private external fun nativeRelease(handle: Long)

    private external fun nativeGetDuration(handle: Long): Double

    private external fun nativeGetRotate(handle: Long): Int
}