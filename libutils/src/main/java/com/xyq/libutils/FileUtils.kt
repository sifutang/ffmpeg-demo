package com.xyq.libutils

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {

    private const val TAG = "FileUtils"

    fun fileExists(path: String): Boolean {
        return File(path).exists()
    }

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

    fun saveBitmapToLocal(contentResolver: ContentResolver, bitmap: Bitmap, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            try {
                val outputStream = contentResolver.openOutputStream(it)
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addToMediaStore(contentResolver: ContentResolver, path: String, mimeType: String) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATA, path)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        }
        contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    fun getImageOrientation(imagePath: String): Int {
        var orientation = 0
        try {
            val exif = ExifInterface(imagePath)
            orientation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                ExifInterface.ORIENTATION_NORMAL -> 0
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return orientation
    }
}