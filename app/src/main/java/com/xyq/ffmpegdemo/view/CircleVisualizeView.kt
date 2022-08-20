package com.xyq.ffmpegdemo.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class CircleVisualizeView: AudioVisualizeView {

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun drawChild(canvas: Canvas?) {
        if (canvas == null) return

        val radius = width * 0.12f * 1f
        mStrokeWidth = ((Math.PI * 2 * radius - (mSpectrumCount - 1) * mItemMargin) / mSpectrumCount * 1.0f).toFloat()
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = 2f

        canvas.drawCircle(mCenterX, mCenterY, radius, mPaint)

        mPaint.strokeWidth = mStrokeWidth
        mPaint.style = Paint.Style.FILL
        mPath.moveTo(0f, mCenterY)

        for (i in 0 until mSpectrumCount) {
            val angel = 360.0 / mSpectrumCount * 1.0 * (i + 1)
            val startX = mCenterX + (radius + mStrokeWidth / 2) * sin(Math.toRadians(angel))
            val startY = mCenterY + (radius + mStrokeWidth / 2) * cos(Math.toRadians(angel))
            val stopX = mCenterX + (radius + mStrokeWidth / 2 + mAudioData!![i]) * sin(Math.toRadians(angel))
            val stopY = mCenterY + (radius + mStrokeWidth / 2 + mAudioData!![i]) * cos(Math.toRadians(angel))
            mPaint.color = Color.rgb(Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
            canvas.drawLine(startX.toFloat(), startY.toFloat(), stopX.toFloat(), stopY.toFloat(), mPaint)
        }
    }
}