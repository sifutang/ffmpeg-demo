package com.xyq.libhwplayer.utils

import java.io.File
import java.io.RandomAccessFile

object MimeTypeHelper {

    fun isPngFile(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) {
            return false
        }

        val pngSignatureLen = 8
        val sign = readBytesFromFile(file, 0, pngSignatureLen)
        if (sign == null || sign.size != pngSignatureLen) {
            return false
        }

        val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        return pngSignature.contentEquals(sign)
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