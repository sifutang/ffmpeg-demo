package com.xyq.libutils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlin.math.abs

object CommonUtils {

    fun getScreenWidth(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val outMetrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(outMetrics)
        return outMetrics.widthPixels
    }

    fun clamp(lower: Float, upper: Float, cur: Float): Float {
        return if (cur < lower) {
            0.0f
        } else if (cur > upper) {
            1.0f
        } else {
            cur
        }
    }

    fun getTimeDesc(timeS: Int): String {
        val hour = timeS / 3600
        val minute = (timeS - hour * 3600) / 60
        val second = timeS - hour * 3600 - minute * 60
        return "%02d:%02d:%02d".format(hour, minute, second) // 00:00:00
    }

    fun generateTextBitmap(text: String, textSize: Float, context: Context): Bitmap {
        val scaledDensity = context.resources.displayMetrics.scaledDensity
        val textPx = (scaledDensity * textSize + 0.5).toInt()

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DEV_KERN_TEXT_FLAG)
        textPaint.textSize = textPx.toFloat()
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.color = Color.WHITE

        val textWidth = textPaint.measureText(text)
        val fontMetrics = textPaint.fontMetrics
        val textHeight = fontMetrics.bottom - fontMetrics.top
        val bitmap = Bitmap.createBitmap(textWidth.toInt(), textHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
//        canvas.drawColor(Color.RED)

        canvas.translate((bitmap.width / 2).toFloat(), (bitmap.height / 2).toFloat())
//        canvas.drawLine((-bitmap.width / 2).toFloat(), 0f, (bitmap.height / 2).toFloat(), 0f, textPaint)
//        canvas.drawLine(0f, (-bitmap.height / 2).toFloat(), 0f, (bitmap.height / 2).toFloat(), textPaint)

        val baseLineY = abs(textPaint.ascent() + textPaint.descent()) / 2
        canvas.drawText(text, -textWidth / 2, baseLineY, textPaint)

        canvas.save()
        canvas.restore()

        return bitmap
    }
}