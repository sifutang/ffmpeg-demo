package com.xyq.libutils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {

    private const val TAG = "FileUtils"

    fun deleteFile(path: String) {
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
    }

    fun copyFile2Path(inputStream: InputStream, path: String) {
        val file = File(path)
        if (!file.exists() || file.length().toInt() == 0) {
            val fos = FileOutputStream(file)
            var len: Int
            val buffer = ByteArray(1024)

            do {
                len = inputStream.read(buffer)
                if (len > 0) {
                    fos.write(buffer, 0, len)
                }
            } while (len != -1)

            fos.flush()
            inputStream.close()
            fos.close()
            Log.i(TAG, "copyFile2Path: success")
        } else {
            Log.w(TAG, "copyFile2Path: $path file exists")
        }
    }

    fun read(assetFileName: String, context: Context): ByteArray {
        val inputStream = context.assets.open(assetFileName)
        val length = inputStream.available()
        val buffer = ByteArray(length)
        inputStream.read(buffer)
        return buffer
    }

    fun readTextFileFromResource(context:Context, resId:Int): String {
        val inputStream = context.resources.openRawResource(resId)
        val result = StringBuilder()
        inputStream.bufferedReader().useLines { lines -> lines.forEach {
            result.append(it)
            result.append("\n")
        } }
        return result.toString()
    }
}