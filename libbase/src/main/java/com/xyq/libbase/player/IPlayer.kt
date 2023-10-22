package com.xyq.libbase.player

import android.view.Surface

interface IPlayer {

    fun init()

    fun setPlayerListener(listener: IPlayerListener)

    fun prepare(path: String, surface: Surface?)

    fun start()

    fun resume()

    fun pause()

    fun stop()

    fun release()

    fun seek(position: Double): Boolean

    fun setMute(mute: Boolean)

    fun getRotate(): Int

    /**
     * ms
     */
    fun getDuration(): Double

    fun getMediaInfo(): String?
}