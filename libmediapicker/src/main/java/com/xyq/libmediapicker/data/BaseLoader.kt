package com.xyq.libmediapicker.data

import android.database.Cursor
import android.os.Bundle
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import com.xyq.libmediapicker.entity.Folder

abstract class BaseLoader: LoaderManager.LoaderCallbacks<Cursor> {

    abstract fun doCreateLoader(id: Int, args: Bundle?): Loader<Cursor>
    abstract fun doLoadFinished(loader: Loader<Cursor>, data: Cursor?)

    private fun doLoaderReset(loader: Loader<Cursor>) {}

    fun getParent(path: String): String {
        val sp = path.split("/")
        return sp[sp.size - 2]
    }

    fun hasDir(folders: ArrayList<Folder>, dirName: String): Int {
        for ((index, folder) in folders.withIndex()) {
            if (folder.name == dirName) {
                return index
            }
        }
        return -1
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> {
        return doCreateLoader(id, args)
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        doLoaderReset(loader)
    }

    override fun onLoadFinished(loader: Loader<Cursor>, data: Cursor?) {
        doLoadFinished(loader, data)
    }
}