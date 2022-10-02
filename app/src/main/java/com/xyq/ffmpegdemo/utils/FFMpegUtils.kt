package com.xyq.ffmpegdemo.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FFMpegUtils {

    interface VideoFrameArrivedInterface {
        /**
         * @param duration
         * 给定视频时长，返回待抽帧的pts arr，单位为s
         */
        fun onFetchStart(duration: Double): DoubleArray

        /**
         * 每抽帧一次回调一次
         */
        fun onProgress(frame: ByteBuffer, timestamps: Double, width: Int, height: Int, index: Int): Boolean

        /**
         * 抽帧动作结束
         */
        fun onFetchEnd()
    }

    fun getVideoFrames(path: String,
                       width: Int,
                       height: Int,
                       cb: VideoFrameArrivedInterface) {
        if (path == "") return
        getVideoFramesCore(path, width, height, cb)
    }

    private external fun getVideoFramesCore(path: String,
                                            width: Int,
                                            height: Int,
                                            cb: VideoFrameArrivedInterface)

    private fun allocateFrame(width: Int, height: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
    }

}