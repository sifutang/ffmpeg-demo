package com.xyq.libffplayer.utils

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FFMpegUtils {

    init {
        System.loadLibrary("ffplayer")
    }

    interface VideoFrameArrivedInterface {
        /**
         * @param duration
         * 给定视频时长，返回待抽帧的pts arr，单位为s
         */
        fun onFetchStart(duration: Double): DoubleArray

        /**
         * 每抽帧一次回调一次
         */
        fun onProgress(frame: ByteBuffer, timestamps: Double, width: Int, height: Int, rotate: Int, index: Int): Boolean

        /**
         * 抽帧动作结束
         */
        fun onFetchEnd()
    }

    fun getVideoFrames(path: String,
                       width: Int,
                       height: Int,
                       precise: Boolean,
                       cb: VideoFrameArrivedInterface
    ) {
        if (path == "") return
        getVideoFramesCore(path, width, height, precise, cb)
    }

    /**
     * 将输入视频的关键帧导出为gif
     */
    fun exportGif(videoPath: String, output: String): Boolean {
        return nativeExportGif(videoPath, output)
    }

    private external fun getVideoFramesCore(path: String,
                                            width: Int,
                                            height: Int,
                                            precise: Boolean,
                                            cb: VideoFrameArrivedInterface
    )

    private fun allocateFrame(width: Int, height: Int): ByteBuffer {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
    }

    private external fun nativeExportGif(videoPath: String, output: String): Boolean

}