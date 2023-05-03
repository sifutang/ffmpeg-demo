package com.xyq.ffmpegdemo.player

/**
 * prepare -> start -> stop -> release
 */
interface IMediaPlayer {

    fun prepare(path: String)

    fun start()

    fun stop()

    fun resume()

    fun pause()

    fun release()

    fun setListener(listener: IMediaPlayerStatusListener?)

    fun seek(position: Double): Boolean

    fun setMute(mute: Boolean)

    fun getDuration(): Double

    fun isPlayComplete(): Boolean
}