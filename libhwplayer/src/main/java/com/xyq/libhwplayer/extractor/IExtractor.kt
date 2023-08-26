package com.xyq.libhwplayer.extractor

import android.media.MediaFormat
import java.nio.ByteBuffer

interface IExtractor {

    fun getFormat(): MediaFormat?

    fun readBuffer(byteBuffer: ByteBuffer): Int

    fun getCurrentTimestamp(): Long

    fun getSampleFlags(): Int

    fun seek(pos: Long): Long

    fun setStartPos(pos: Long)

    fun release()
}