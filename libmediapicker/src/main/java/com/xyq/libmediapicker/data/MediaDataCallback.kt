package com.xyq.libmediapicker.data

import com.xyq.libmediapicker.entity.Folder

interface MediaDataCallback {
    fun onMediaDataArrived(list: ArrayList<Folder>)
}