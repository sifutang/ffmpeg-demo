package com.xyq.libpng

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PngReader {

    companion object {
        private const val TAG = "PNG_Helper"
    }

    init {
        System.loadLibrary("png_helper")
    }

    fun load(path: String): PngData {
        val pngData = PngData()
        read(path, pngData)
        Log.i(TAG, "read: $pngData")
        return pngData
    }

    private external fun read(path: String, data: PngData)

    inner class PngData {
        var width = 0
        var height = 0
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
            return "PngData(width=$width, height=$height, colorType=$colorType, bitDepth=$bitDepth, hasBuffer=${buffer != null})"
        }
    }
}