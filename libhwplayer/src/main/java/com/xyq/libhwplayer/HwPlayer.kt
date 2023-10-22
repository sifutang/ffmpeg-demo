package com.xyq.libhwplayer

import android.util.Log
import android.view.Surface
import com.xyq.libbase.player.IPlayer
import com.xyq.libbase.player.IPlayerListener
import com.xyq.libhwplayer.decoder.AudioDecoder
import com.xyq.libhwplayer.decoder.VideoDecoder

/**
 * use MediaCodec
 */
class HwPlayer: IPlayer {

    companion object {
        private const val TAG = "HwPlayer"
    }

    private var mVideoDecoder: VideoDecoder? = null
    private var mAudioDecoder: AudioDecoder? = null

    override fun init() {
        Log.i(TAG, "init: ")
        mVideoDecoder = VideoDecoder()
        mAudioDecoder = AudioDecoder()
    }

    override fun setPlayerListener(listener: IPlayerListener) {
        mVideoDecoder?.setPlayerListener(listener)
        mAudioDecoder?.setPlayerListener(listener)
    }

    override fun prepare(path: String, surface: Surface?) {
        Log.i(TAG, "prepare: $path")
        mVideoDecoder?.prepare(path, surface)
        mAudioDecoder?.prepare(path)
    }

    override fun start() {
        Log.i(TAG, "start: ")
        mVideoDecoder?.start()
        mAudioDecoder?.start()
    }

    override fun resume() {
        Log.i(TAG, "resume: ")
    }

    override fun pause() {
        Log.i(TAG, "pause: ")
    }

    override fun stop() {
        Log.i(TAG, "stop: ")
        mVideoDecoder?.stop()
        mAudioDecoder?.stop()
    }

    override fun release() {
        Log.i(TAG, "release: ")
        mVideoDecoder?.release()
        mAudioDecoder?.release()
    }

    override fun seek(position: Double): Boolean {
        Log.i(TAG, "seek: ")
        return true
    }

    override fun setMute(mute: Boolean) {
        Log.i(TAG, "setMute: ")
    }

    override fun getRotate(): Int {
        return mVideoDecoder!!.getRotate()
    }

    override fun getDuration(): Double {
        return mVideoDecoder!!.getDuration()
    }

    override fun getMediaInfo(): String? {
        return null
    }
}