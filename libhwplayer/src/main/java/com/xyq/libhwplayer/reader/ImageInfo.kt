package com.xyq.libhwplayer.reader

import java.nio.ByteBuffer

class ImageInfo {
    var width = 0
    var height = 0
    var rotate = 0
    var data: ByteBuffer? = null

    fun isValid(): Boolean {
        return width > 0 && height > 0 && data != null
    }
}