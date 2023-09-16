package com.xyq.libmediapicker.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.xyq.libmediapicker.PickerConfig
import com.xyq.libmediapicker.R
import com.xyq.libmediapicker.entity.Media
import com.xyq.libmediapicker.utils.ScreenUtils

class MediaGridAdapter(
    private val context: Context,
    private var medias: ArrayList<Media>,
    private var selectMedias: ArrayList<Media>?,
    private val maxSelect: Int
): RecyclerView.Adapter<MediaGridAdapter.MyViewHolder>() {

    private var lastSelectItemPair: Pair<Int, MyViewHolder>? = null

    init {
        if (selectMedias == null) {
            selectMedias = ArrayList()
        }
    }

    interface OnRecyclerViewItemClickListener {
        fun onItemClick(view: View?, data: Media?, selectMedias: ArrayList<Media>?)
    }

    private var listener: OnRecyclerViewItemClickListener? = null

    fun setOnItemClickListener(listener: OnRecyclerViewItemClickListener) {
        this.listener = listener
    }

    fun getSelectMedias(): ArrayList<Media>? {
        return selectMedias
    }

    fun updateSelectAdapter(select: ArrayList<Media>) {
        selectMedias = select
        notifyDataSetChanged()
    }

    fun updateAdapter(list: ArrayList<Media>) {
        medias = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        return MyViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.media_view_item, parent, false), getItemWidth())
    }

    override fun getItemCount(): Int {
        return medias.size
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val media = medias[position]
        val uri = Uri.parse("file://${media.path}")
        Glide.with(context).load(uri).into(holder.mediaImage)

        if (media.isVideo()) {
            holder.mediaInfoLayout.visibility = View.VISIBLE
            holder.infoTv.text = media.getDurationDesc()
        } else {
            holder.mediaInfoLayout.visibility = if (media.isGif()) View.VISIBLE else View.INVISIBLE
            holder.infoTv.text = if (media.isGif()) context.getString(R.string.mime_type_gif) else ""
        }

        val isSelect = isSelect(media)
        holder.maskView.visibility = if (isSelect >= 0) View.VISIBLE else View.INVISIBLE
        holder.checkImage.setImageDrawable(
            if (isSelect >= 0) ContextCompat.getDrawable(context, R.drawable.btn_selected) else ContextCompat.getDrawable(context, R.drawable.btn_unselected)
        )
        holder.mediaImage.setOnClickListener { view ->
            lastSelectItemPair?.let {
                if (maxSelect == 1) { // 只能选择一个的时候允许连续切换进行选择，自动取消上一个
                    val preMedia = medias[lastSelectItemPair!!.first]
                    if (preMedia.path != media.path) {
                        val preHolder = lastSelectItemPair!!.second
                        addOrRemoveMedia(preMedia, preHolder)
                    }
                }
            }

            val selectIndex = isSelect(media)
            selectMedias?.let {
                if (it.size >= maxSelect && selectIndex < 0) { // new media and arrived max limit
                    return@setOnClickListener
                }
            }
            addOrRemoveMedia(media, holder)
            listener?.onItemClick(view, media, selectMedias)

            lastSelectItemPair = if (selectMedias?.isNotEmpty() == true) {
                Pair(position, holder)
            } else {
                null
            }
        }
    }

    private fun addOrRemoveMedia(media: Media, holder: MyViewHolder) {
        val selectIndex = isSelect(media)
        if (selectIndex >= 0) {
            holder.maskView.visibility = View.INVISIBLE
            holder.checkImage.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.btn_unselected))
        } else {
            holder.maskView.visibility = View.VISIBLE
            holder.checkImage.setImageDrawable(ContextCompat.getDrawable(context, R.drawable.btn_selected))
        }

        if (selectIndex < 0) {
            selectMedias?.add(media)
        } else {
            selectMedias?.let {
                if (it.isNotEmpty()) {
                    it.removeAt(selectIndex)
                }
            }
        }
    }

    private fun isSelect(media: Media): Int {
        if (selectMedias == null || selectMedias!!.isEmpty()) {
            return -1
        }
        var idx = -1
        for ((index, data) in selectMedias!!.withIndex()) {
            if (media.path == data.path) {
                idx = index
                break
            }
        }
        return idx
    }

    private fun getItemWidth(): Int {
        return (ScreenUtils.getScreenWidth(context) / PickerConfig.GridSpanCount) - PickerConfig.GridSpanCount
    }

    class MyViewHolder(view: View, itemWidth: Int) : RecyclerView.ViewHolder(view) {
        var mediaImage: ImageView
        var checkImage: ImageView
        var maskView: View
        var infoTv: TextView
        var mediaInfoLayout: RelativeLayout

        init {
            mediaImage = view.findViewById<View>(R.id.media_image) as ImageView
            checkImage = view.findViewById<View>(R.id.check_image) as ImageView
            maskView = view.findViewById(R.id.mask_view)
            mediaInfoLayout = view.findViewById<View>(R.id.media_info) as RelativeLayout
            infoTv = view.findViewById<View>(R.id.info_tv) as TextView
            itemView.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemWidth)
        }
    }
}