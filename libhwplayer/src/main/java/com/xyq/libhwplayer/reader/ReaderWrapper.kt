package com.xyq.libhwplayer.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.util.Log
import com.xyq.libhwplayer.utils.MimeTypeHelper
import com.xyq.libjpeg.JpegReader
import com.xyq.libpng.PngReader
import com.xyq.libutils.FileUtils
import java.nio.ByteBuffer

class ReaderWrapper {

    companion object {
        private const val TAG = "ReaderWrapper"
    }

    fun load(path: String): ImageInfo? {
        var imageInfo = decodeByHW(path)

        if (imageInfo == null) {
            imageInfo = decodeBySW(path)
        }

        return imageInfo
    }

    private fun decodeByHW(path: String): ImageInfo? {
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        getColorSpace(path)?.let {
            if (it != ColorSpace.get(ColorSpace.Named.SRGB)) {
                options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                Log.i(TAG, "prepare: inPreferredColorSpace s-rgb, origin color space: ${it.name}")
            }
        }

        var imageInfo: ImageInfo? = null
        val bitmap = BitmapFactory.decodeFile(path, options)
        if (bitmap != null) {
            imageInfo = ImageInfo()
            imageInfo.width = bitmap.width
            imageInfo.height = bitmap.height
            imageInfo.rotate = FileUtils.getImageOrientation(path)
            imageInfo.data = bitmapToByteBuffer(bitmap)
            bitmap.recycle()
        } else {
            Log.e(TAG, "load: System decodeFile failed")
        }
        return imageInfo
    }

    private fun decodeBySW(path: String): ImageInfo? {
        var imageInfo: ImageInfo? = null
        val mimeType = MimeTypeHelper.getFileMimeType(path)
        if (mimeType == MimeTypeHelper.MimeType.JPEG) {
            val data = JpegReader().load(path)
            if (data.isValid()) {
                imageInfo = ImageInfo()
                imageInfo.width = data.width
                imageInfo.height = data.height
                imageInfo.data = data.buffer
                imageInfo.rotate = data.rotate
            } else {
                Log.e(TAG, "prepare: use JpegReader decode failed")
            }
        } else if (mimeType == MimeTypeHelper.MimeType.PNG) {
            val data = PngReader().load(path)
            if (data.isValid()) {
                imageInfo = ImageInfo()
                imageInfo.width = data.width
                imageInfo.height = data.height
                imageInfo.rotate = data.rotate
                imageInfo.data = data.buffer
            } else {
                Log.e(TAG, "prepare: use PngReader decode failed")
            }
        }
        return imageInfo
    }

    private fun getColorSpace(path: String): ColorSpace? {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, options)
        return options.outColorSpace
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bytes = bitmap.byteCount
        val buffer = ByteBuffer.allocateDirect(bytes)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return buffer
    }
}