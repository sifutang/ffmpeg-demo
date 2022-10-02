package com.xyq.ffmpegdemo.utils

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object FileUtils {

    private const val TAG = "FileUtils"

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

    fun getImageAbsolutePath(context: Context, uri: Uri?): String? {
        if (uri == null) return ""
        if (DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val splitArr = docId.split(":")
                val type = splitArr[0]
                if ("primary" == type.lowercase()) {
                    return "${Environment.getExternalStorageDirectory()} + / + ${splitArr[1]}"
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), id.toLong())
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val splitArr = docId.split(":")
                val type = splitArr[0]
                var contentUri: Uri? = null
                when (type) {
                    "image" -> {
                        contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    "video" -> {
                        contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    "audio" -> {
                        contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    }
                }
                val selection = MediaStore.Images.Media._ID + "=?"
                val selectionArgs = arrayOf(splitArr[1])
                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content" == uri.scheme) {
            return if (isGooglePhotosUri(uri)) {
                uri.lastPathSegment
            } else {
                getDataColumn(context, uri, null, null)
            }
        } else if ("file" == uri.scheme) {
            return uri.path
        }
        return ""
    }

    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String {
        var cursor: Cursor? = null
        val column = MediaStore.Images.Media.DATA
        val projection = arrayOf(column)
        var path = ""
        try {
            cursor = context.contentResolver.query(uri!!, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndexOrThrow(column)
                path = cursor.getString(index)
            }
        } finally {
            cursor?.close()
        }
        return path
    }

    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }

    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }

    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }

    private fun isGooglePhotosUri(uri: Uri): Boolean {
        return "com.google.android.apps.photos.content" == uri.authority
    }
}