package com.xyq.ffmpegdemo.adapter

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.xyq.ffmpegdemo.R
import com.xyq.ffmpegdemo.entity.Thumbnail
import com.xyq.libmediapicker.utils.ScreenUtils

class ThumbnailAdapter(
    private val context: Context,
    private var thumbnails: ArrayList<Thumbnail>
): RecyclerView.Adapter<ThumbnailAdapter.MyViewHolder>() {

    companion object {
        private const val TAG = "ThumbnailAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val width = ScreenUtils.getScreenWidth(context) / 5
        val height = ScreenUtils.dp2px(context, 60f)
        return MyViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.thumbnail_item, parent, false), width, height)
    }

    override fun getItemCount(): Int {
        return thumbnails.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val model = thumbnails[position]
        holder.ivThumb.setImageBitmap(model.bitmap)
    }

    fun addData(thumbnail: Thumbnail) {
        Log.i(TAG, "addData: ${thumbnail.index}, size: ${thumbnails.size}")
        if (thumbnails.size <= thumbnail.index) {
            thumbnails.add(thumbnail)
        } else {
            thumbnails[thumbnail.index] = thumbnail
        }
        notifyItemChanged(thumbnail.index)
    }

    class MyViewHolder(view: View, w: Int, h: Int) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView

        init {
            ivThumb = view.findViewById(R.id.iv_thumbnail)
            ivThumb.layoutParams = FrameLayout.LayoutParams(w, h)
        }
    }
}