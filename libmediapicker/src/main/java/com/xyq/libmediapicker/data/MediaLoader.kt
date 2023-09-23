package com.xyq.libmediapicker.data

import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.loader.app.LoaderManager
import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import com.xyq.libmediapicker.R
import com.xyq.libmediapicker.entity.Folder
import com.xyq.libmediapicker.entity.Media

class MediaLoader(private val context: Context,
                  private val loaderCallback: MediaDataCallback): BaseLoader(), LoaderManager.LoaderCallbacks<Cursor> {

    companion object {
        private const val TAG = "MediaLoader"
    }

    override fun doCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.PARENT
        )
        val selection = (MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
                + " OR "
                + MediaStore.Files.FileColumns.MEDIA_TYPE + "="
                + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
        val uri = MediaStore.Files.getContentUri("external")
        val order = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
        return CursorLoader(context, uri, projection, selection, null, order)
    }

    override fun doLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        // The Loader will release the data once it knows the application is no longer using it.
        // For example, if the data is a android.database.Cursor from a android.content.CursorLoader, you should not call close() on it yourself
        try {
            val folders = ArrayList<Folder>()
            val allFolder = Folder(context.resources.getString(R.string.all_dir_name))
            folders.add(allFolder)
            val allVideoDir = Folder(context.resources.getString(R.string.video_dir_name))
            folders.add(allVideoDir)

            data?.let {
                it.moveToPosition(-1) // back to start point
                while (it.moveToNext()) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA))
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME))
                    val dateTime: Long = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED))
                    val mediaType: Int = it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE))
                    val duration: Long = it.getLong(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DURATION))
                    val id: Int = it.getInt(it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID))
                    if (path.isNullOrEmpty()) {
                        Log.i(TAG, "doLoadFinished: path is null")
                        continue
                    }
                    val dirName = getParent(path)
                    val media = Media(path, name, dateTime, mediaType, duration, id, dirName)
                    Log.i(TAG, "doLoadFinished: $media")
                    allFolder.addMedias(media)
                    if (media.isVideo()) {
                        allVideoDir.addMedias(media)
                    }

                    val index = hasDir(folders, dirName)
                    if (index != -1) {
                        folders[index].addMedias(media)
                    } else {
                        val folder = Folder(dirName)
                        folder.addMedias(media)
                        folders.add(folder)
                    }
                }
                Log.i(TAG, "doLoadFinished: count: ${it.count}, folder first: ${folders[0].getMedias().size}")
            }
            loaderCallback.onMediaDataArrived(folders)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}