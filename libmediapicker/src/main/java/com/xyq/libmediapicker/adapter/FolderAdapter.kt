package com.xyq.libmediapicker.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.xyq.libmediapicker.R
import com.xyq.libmediapicker.entity.Folder
import com.xyq.libmediapicker.entity.Media

class FolderAdapter(private var folders: ArrayList<Folder>,
                    private val context: Context): BaseAdapter() {

    private var inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
    private var lastSelected = 0

    fun updateAdapter(list: ArrayList<Folder>) {
        folders = list
        notifyDataSetChanged()
    }

    fun setSelectIndex(index: Int) {
        if (lastSelected != index) {
            lastSelected = index
            notifyDataSetChanged()
        }
    }

    fun getSelectMedias(): ArrayList<Media> {
        return folders[lastSelected].getMedias()
    }

    override fun getCount(): Int {
        return folders.size
    }

    override fun getItem(position: Int): Folder {
        return folders[position]
    }

    override fun getItemId(position: Int): Long {
        return 0
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        val holder: ViewHolder = if (view == null) {
            view = inflater.inflate(R.layout.folders_view_item, parent, false)
            ViewHolder(view)
        } else {
            view.tag as ViewHolder
        }

        val folder = getItem(position)
        val media: Media?
        if (folder.getMedias().size > 0) {
            media = folder.getMedias()[0]
            Glide.with(context)
                .load(media.getFileUri())
                .into(holder.cover)
        } else {
            holder.cover.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.default_image))
        }

        holder.name.text = folder.name
        holder.size.text = "${folder.getMedias().size}"
        holder.indicator.visibility = if (lastSelected == position) View.VISIBLE else View.INVISIBLE

        return view!!
    }

    internal class ViewHolder(view: View) {
        var cover: ImageView
        var indicator: ImageView
        var name: TextView
        var path: TextView
        var size: TextView

        init {
            cover = view.findViewById<View>(R.id.cover) as ImageView
            name = view.findViewById<View>(R.id.name) as TextView
            path = view.findViewById<View>(R.id.path) as TextView
            size = view.findViewById<View>(R.id.size) as TextView
            indicator = view.findViewById<View>(R.id.indicator) as ImageView
            view.tag = this
        }
    }
}