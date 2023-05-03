package com.xyq.ffmpegdemo.player

interface IMediaPlayerStatusListener {

    fun onProgress(timestamp: Double)

    fun onComplete()

    fun onFftAudioDataArrived(data: FloatArray)

}