package com.xyq.ffmpegdemo

interface PlayerListener {

    fun onPrepared(width: Int, height: Int)

    fun onFrameArrived(width: Int, height: Int, y: ByteArray?, u: ByteArray?, v: ByteArray?)

    fun onError(code: Int, msg: String)

    fun onCompleted()
}