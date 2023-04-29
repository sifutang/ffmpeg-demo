package com.xyq.ffmpegdemo.model

import android.graphics.Bitmap
import java.nio.ByteBuffer

class VideoThumbnailModel(val width: Int, val height: Int, val index: Int, val buffer: ByteBuffer?) {

    var bitmap: Bitmap? = null

    init {
        buffer?.let {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap!!.copyPixelsFromBuffer(it)
        }
    }

    fun isValid(): Boolean {
        return index >= 0 && bitmap != null
    }

    override fun toString(): String {
        return "VideoThumbnailModel(width=$width, height=$height, index=$index, buffer=$buffer, bitmap=$bitmap)"
    }

}