package com.xyq.libmediapicker.entity

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.MediaStore

class Media(val path: String,
            private val name: String,
            private val time: Long,
            private val mediaType: Int,
            private val duration: Long, // ms
            private val id: Int,
            private val parentDir: String): Parcelable {

    private var extension: String = if (name.isNotEmpty() && name.indexOf(".") != -1) {
        name.substring(name.lastIndexOf("."), name.length)
    } else {
        "unknown"
    }

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readLong(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readInt(),
        parcel.readString()!!
    ) {
        extension = parcel.readString()!!
    }

    fun isGif(): Boolean {
        return ".gif" == extension.lowercase()
    }

    fun isVideo(): Boolean {
        return mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
    }

    fun getFileUri(): Uri {
        return Uri.parse("file://${path}")
    }

    fun getDurationDesc(): String {
        val second = duration / 1000
        val m = (second / 60).toInt()
        val h = (second / 3600).toInt()
        val s = (second - h * 3600 - m * 60).toInt()
        return "%02d:%02d:%02d".format(h, m, s) // 00:00:00
    }

    override fun toString(): String {
        return "Media(path='$path', name='$name', time=$time, mediaType=$mediaType, duration=$duration, id=$id, parentDir='$parentDir', extension='$extension')"
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(path)
        dest.writeString(name)
        dest.writeLong(time)
        dest.writeInt(mediaType)
        dest.writeLong(duration)
        dest.writeInt(id)
        dest.writeString(parentDir)
        dest.writeString(extension)
    }

    companion object CREATOR : Parcelable.Creator<Media> {
        override fun createFromParcel(parcel: Parcel): Media {
            return Media(parcel)
        }

        override fun newArray(size: Int): Array<Media?> {
            return arrayOfNulls(size)
        }
    }
}