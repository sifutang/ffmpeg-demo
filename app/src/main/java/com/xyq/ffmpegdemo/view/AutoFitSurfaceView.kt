package com.xyq.ffmpegdemo.view

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceView
import kotlin.math.roundToInt

class AutoFitSurfaceView : SurfaceView {
    private var aspectRatio = 0f

    constructor(context: Context?) : super(context) {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    fun setAspectRatio(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            aspectRatio = 1f * width / height
            holder.setFixedSize(width, height)
            requestLayout()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        if (aspectRatio == 0f) {
            setMeasuredDimension(w, h)
        } else {
            val newW: Int
            val newH: Int
            val actualRatio = if (w > h) aspectRatio else 1f / aspectRatio
            if (w < h * actualRatio) {
                newH = h
                newW = (h * actualRatio).roundToInt()
            } else {
                newW = w
                newH = (w / actualRatio).roundToInt()
            }
            Log.i(TAG, "onMeasure: set w = $newW, h = $newH")
            setMeasuredDimension(newW, newH)
        }
    }

    companion object {
        private const val TAG = "AutoFitSurfaceView"
    }
}