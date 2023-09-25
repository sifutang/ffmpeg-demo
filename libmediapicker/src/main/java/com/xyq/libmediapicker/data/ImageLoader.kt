package com.xyq.libmediapicker.data

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader

import com.xyq.libmediapicker.R
import com.xyq.libmediapicker.entity.Folder
import com.xyq.libmediapicker.entity.Media
import java.io.File

class ImageLoader(private val context: Context,
                  private val loaderCallback: MediaDataCallback): BaseLoader() {

    companion object {
        private const val TAG = "ImageLoader"
    }

    override fun doCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns._ID
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
        val uri = MediaStore.Files.getContentUri("external")
        val order = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        return CursorLoader(context, uri, projection, selection, null, order)
    }

    override fun doLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        try {
            val folders = ArrayList<Folder>()
            val allFolder = Folder(context.resources.getString(R.string.all_image))
            folders.add(allFolder)

            data?.let {
                it.moveToPosition(-1)
                while (it.moveToNext()) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                    val dateTime: Long = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
                    val mediaType: Int = it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                    val duration: Long = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION))
                    val id: Int = it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    if (path.isNullOrEmpty() || !File(path).exists()) {
                        continue
                    }
                    val dirName = getParent(path)
                    val media = Media(path, name, dateTime, mediaType, duration, id, dirName)
                    Log.i(TAG, "doLoadFinished: $media")
                    allFolder.addMedias(media)

                    val index = hasDir(folders, dirName)
                    if (index != -1) {
                        folders[index].addMedias(media)
                    } else {
                        val folder = Folder(dirName)
                        folder.addMedias(media)
                        folders.add(folder)
                    }
                }
                Log.i(TAG, "doLoadFinished: count: ${it.count}")
            }
            loaderCallback.onMediaDataArrived(folders)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}