package com.xyq.librender.model

import android.graphics.Bitmap
import java.nio.ByteBuffer

data class FrameBuffer(val width: Int, val height: Int, val data: ByteBuffer) {

    fun toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(data)
        return bitmap
    }

}
