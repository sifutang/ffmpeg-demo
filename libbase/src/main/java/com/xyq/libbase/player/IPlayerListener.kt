package com.xyq.libbase.player

interface IPlayerListener {

    fun onVideoTrackPrepared(width: Int, height: Int)

    fun onAudioTrackPrepared()

    fun onVideoFrameArrived(width: Int, height: Int, format: Int, y: ByteArray?, u: ByteArray?, v: ByteArray?)

    fun onAudioFrameArrived(buffer: ByteArray?, size: Int, flush: Boolean)

    fun onPlayProgress(timestamp: Double)

    fun onPlayComplete()
}