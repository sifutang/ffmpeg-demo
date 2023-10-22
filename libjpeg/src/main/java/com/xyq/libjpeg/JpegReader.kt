package com.xyq.libjpeg

import android.util.Log
import com.xyq.libutils.FileUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JpegReader {

    companion object {
        private const val TAG = "JPEG_Helper"
    }

    init {
        System.loadLibrary("jpeg_helper")
    }

    fun load(path: String): ImageData {
        val start = System.currentTimeMillis()
        val data = ImageData()
        read(path, data)
        data.rotate = FileUtils.getImageOrientation(path)
        Log.i(TAG, "read: $data, consume: ${System.currentTimeMillis() - start}ms")
        return data
    }

    private external fun read(path: String, data: ImageData)

    inner class ImageData {
        var width = 0
        var height = 0
        var rotate = 0
        var colorType = 0
        var bitDepth = 0
        var buffer: ByteBuffer? = null

        private fun allocateFrameFromNative(w: Int, h: Int): ByteBuffer {
            width = w
            height = h
            buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN)
            return buffer!!
        }

        fun isValid(): Boolean {
            return width > 0 && height > 0 && buffer != null
        }

        override fun toString(): String {
            return "ImageData(width=$width, height=$height, colorType=$colorType, bitDepth=$bitDepth, hasBuffer=${buffer != null})"
        }
    }

}