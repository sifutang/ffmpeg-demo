package com.xyq.ffmpegdemo.viewmodel

import android.graphics.Bitmap
import android.graphics.Matrix
import java.nio.ByteBuffer

class VideoThumbnailModel(private val width: Int, private val height: Int, private val rotate: Int, val index: Int, private val buffer: ByteBuffer?) {

    private var mBitmap: Bitmap? = null

    fun isValid(): Boolean {
        return index >= 0 && getBitmap() != null
    }

    fun getBitmap(): Bitmap? {
        if (mBitmap == null && width > 0 && height > 0) {
            buffer?.let {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap!!.copyPixelsFromBuffer(it)
                mBitmap = if (rotate != 0) {
                    val matrix = Matrix()
                    matrix.postRotate(rotate.toFloat())
                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                } else {
                    bitmap
                }
            }
        }

        return mBitmap
    }

    override fun toString(): String {
        return "VideoThumbnailModel(width=$width, height=$height, rotate=$rotate, index=$index)"
    }

}