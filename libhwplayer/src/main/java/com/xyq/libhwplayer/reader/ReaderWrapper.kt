package com.xyq.libhwplayer.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Build
import android.util.Log
import com.xyq.libhwplayer.utils.MimeTypeHelper
import com.xyq.libjpeg.JpegReader
import com.xyq.libpng.PngReader
import com.xyq.libutils.FileUtils
import java.nio.ByteBuffer

class ReaderWrapper {

    inner class BufferData {
        var width = 0
        var height = 0
        var rotate = 0
        var data: ByteBuffer? = null

        fun isValid(): Boolean {
            return width > 0 && height > 0 && data != null
        }
    }

    companion object {
        private const val TAG = "ReaderWrapper"
    }

    fun load(path: String): BufferData? {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        getColorSpace(path)?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it != ColorSpace.get(ColorSpace.Named.SRGB)) {
                options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                Log.i(TAG, "prepare: inPreferredColorSpace s-rgb, origin color space: ${it.name}")
            }
        }

        val bufferData = BufferData()
        val bitmap = BitmapFactory.decodeFile(path, options)
        if (bitmap != null) {
            bufferData.width = bitmap.width
            bufferData.height = bitmap.height
            bufferData.rotate = FileUtils.getImageOrientation(path)
            bufferData.data = bitmapToByteBuffer(bitmap)
            bitmap.recycle()
            return bufferData
        } else {
            Log.e(TAG, "load: System decodeFile failed")
        }

        val mimeType = MimeTypeHelper.getFileMimeType(path)
        if (mimeType == MimeTypeHelper.MimeType.JPEG) {
            val data = JpegReader().load(path)
            if (data.isValid()) {
                bufferData.width = data.width
                bufferData.height = data.height
                bufferData.data = data.buffer
                bufferData.rotate = data.rotate
            } else {
                Log.e(TAG, "prepare: use JpegReader decode failed")
            }
        } else if (mimeType == MimeTypeHelper.MimeType.PNG) {
            val data = PngReader().load(path)
            if (data.isValid()) {
                bufferData.width = data.width
                bufferData.height = data.height
                bufferData.rotate = data.rotate
                bufferData.data = data.buffer
            } else {
                Log.e(TAG, "prepare: use PngReader decode failed")
            }
        }
        return if (bufferData.isValid()) bufferData else null
    }

    private fun getColorSpace(path: String): ColorSpace? {
        var colorSpace: ColorSpace? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            colorSpace = options.outColorSpace
        }
        return colorSpace
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bytes = bitmap.byteCount
        val buffer = ByteBuffer.allocateDirect(bytes)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return buffer
    }
}