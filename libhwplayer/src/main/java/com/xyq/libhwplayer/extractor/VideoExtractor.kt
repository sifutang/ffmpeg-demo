package com.xyq.libhwplayer.extractor

import android.media.MediaFormat
import java.nio.ByteBuffer

open class VideoExtractor(path: String): IExtractor {

    protected val mMediaExtractor = MyExtractor(path)

    override fun getFormat(): MediaFormat? {
        return mMediaExtractor.getVideoFormat()
    }

    override fun readBuffer(byteBuffer: ByteBuffer): Int {
        return mMediaExtractor.readBuffer(byteBuffer)
    }

    override fun getCurrentTimestamp(): Long {
        return mMediaExtractor.getCurrentTimestamp()
    }

    override fun getSampleFlags(): Int {
        return mMediaExtractor.getSampleFlags()
    }

    override fun seek(pos: Long): Long {
        return mMediaExtractor.seek(pos)
    }

    override fun setStartPos(pos: Long) {
        mMediaExtractor.setStartPos(pos)
    }

    override fun release() {
        mMediaExtractor.release()
    }
}