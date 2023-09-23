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

class VideoLoader(private val context: Context,
                  private val loaderCallback: MediaDataCallback): BaseLoader() {

    companion object {
        private const val TAG = "VideoLoader"
    }

    override fun doCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.PARENT,
        )
        val selection = MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
        val uri = MediaStore.Files.getContentUri("external")
        val order = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        return CursorLoader(context, uri, projection, selection, null, order)
    }

    override fun doLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        try {
            val folders = ArrayList<Folder>()
            val allFolder = Folder(context.resources.getString(R.string.all_video))
            folders.add(allFolder)

            data?.let {
                it.moveToPosition(-1)
                while (it.moveToNext()) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                    if (path.isNullOrEmpty()) {
                        continue
                    }
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                    val dateTime = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
                    val mediaType = it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                    val id = it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    val duration = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION))

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