package com.xyq.libmediapicker.entity

import android.os.Parcel
import android.os.Parcelable

class Folder(val name: String): Parcelable {

    var count = 0

    private var medias: ArrayList<Media> = ArrayList()

    constructor(parcel: Parcel) : this(parcel.readString()!!) {
        count = parcel.readInt()
        medias = parcel.createTypedArrayList(Media.CREATOR as Parcelable.Creator<Media>)!!
    }

    fun addMedias(media: Media) {
        this.medias.add(media)
    }

    fun getMedias(): ArrayList<Media> {
        return this.medias
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeInt(count)
        parcel.writeTypedList(medias)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Folder> {
        override fun createFromParcel(parcel: Parcel): Folder {
            return Folder(parcel)
        }

        override fun newArray(size: Int): Array<Folder?> {
            return arrayOfNulls(size)
        }
    }
}