package com.xyq.ffmpegdemo.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.xyq.ffmpegdemo.R

abstract class AudioVisualizeView: View {

    protected var mSpectrumCount = 60

    protected var mItemMargin = 0f

    protected var mColor: Int = Color.WHITE

    protected var mStrokeWidth = 5f

    protected var mAudioData: FloatArray? = null

    protected var mPaint = Paint()
    protected var mPath = Path()

    protected var mCenterX = 0f
    protected var mCenterY = 0f


    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        val typeArray = context?.theme?.obtainStyledAttributes(attrs, R.styleable.AudioVisualizeView, defStyleAttr, 0)
        if (typeArray != null) {
            mColor = typeArray.getColor(R.styleable.AudioVisualizeView_visualize_color, Color.WHITE)
            mSpectrumCount = typeArray.getInteger(R.styleable.AudioVisualizeView_visualize_count, 60)
            mItemMargin = typeArray.getDimension(R.styleable.AudioVisualizeView_visualize_item_margin, 12f)
        }
        typeArray?.recycle()

        mPaint.strokeWidth = mStrokeWidth
        mPaint.color = mColor
        mPaint.strokeCap = Paint.Cap.ROUND
        mPaint.isAntiAlias = true
        mPaint.maskFilter = BlurMaskFilter(5f, BlurMaskFilter.Blur.SOLID)
    }

    fun setFftAudioData(audio: FloatArray) {
        mAudioData = audio
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val wSpecMode = MeasureSpec.getMode(widthMeasureSpec)
        val wSpecSize = MeasureSpec.getSize(widthMeasureSpec)
        val finallyWidth = if (wSpecMode == MeasureSpec.EXACTLY) {
            wSpecSize
        } else {
            200
        }

        val hSpecMode = MeasureSpec.getMode(heightMeasureSpec)
        val hSpecSize = MeasureSpec.getSize(heightMeasureSpec)
        val finallyHeight = if (hSpecMode == MeasureSpec.EXACTLY) {
            hSpecSize
        } else {
            200
        }
        setMeasuredDimension(finallyWidth, finallyHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        mCenterX = width / 2f
        mCenterY = height / 2f
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (mAudioData == null) {
            return
        }

        drawChild(canvas)
    }

    protected abstract fun drawChild(canvas: Canvas?)

}