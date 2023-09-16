package com.xyq.libmediapicker.adapter

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingDecoration(private val space: Int,
                        private val spanCount: Int): RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State) {
        outRect.left = space
        outRect.bottom = space
        val position = parent.getChildLayoutPosition(view)
        if (position % spanCount == 0) {
            outRect.left = 0
        }
    }
}