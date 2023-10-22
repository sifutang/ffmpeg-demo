package com.xyq.libhwplayer.utils

import java.io.File
import java.io.RandomAccessFile

object MimeTypeHelper {

    enum class MimeType {
        UNKNOWN,
        PNG,
        JPEG
    }

    fun getFileMimeType(path: String): MimeType {
        val file = File(path)
        if (!file.exists()) {
            return MimeType.UNKNOWN
        }

        val size = 8
        val sign = readBytesFromFile(file, 0, size)
        if (sign == null || sign.size != size) {
            return MimeType.UNKNOWN
        }

        // png check
        val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        if (pngSignature.contentEquals(sign)) {
            return MimeType.PNG
        }

        // jpeg check
        val jpegSignature = byteArrayOf(0XFF.toByte(), 0XD8.toByte())
        val jpegReadHeader = byteArrayOf(sign[0], sign[1])
        if (jpegSignature.contentEquals(jpegReadHeader)) {
            return MimeType.JPEG
        }

        return MimeType.UNKNOWN
    }

    private fun readBytesFromFile(file: File, offset: Long, len: Int): ByteArray? {
        val fileSize = file.length()
        if (offset < 0 || offset >= fileSize) {
            return null
        }

        val availableLen = fileSize - offset
        val readLen = if (len > availableLen) availableLen.toInt() else len

        val raf = RandomAccessFile(file, "r")
        raf.seek(offset)

        val bytes = ByteArray(readLen)
        raf.read(bytes)
        raf.close()

        return bytes
    }
}