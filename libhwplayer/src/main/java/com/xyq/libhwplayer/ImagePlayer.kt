package com.xyq.libhwplayer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import android.util.Log
import android.view.Surface
import com.xyq.libbase.player.IPlayer
import com.xyq.libbase.player.IPlayerListener
import com.xyq.libhwplayer.utils.MimeTypeHelper
import com.xyq.libpng.PngReader
import java.nio.ByteBuffer

class ImagePlayer: IPlayer {

    private var mListener: IPlayerListener? = null
    private var mRotate = 0
    private var mRgbaBuffer: ByteBuffer? = null
    private var mWidth = 0
    private var mHeight = 0

    companion object {
        private const val TAG = "ImagePlayer"
        private const val FORMAT_RGBA = 0x02
    }

    override fun init() {
        Log.i(TAG, "init: ")
    }

    override fun setPlayerListener(listener: IPlayerListener) {
        mListener = listener
    }

    override fun prepare(path: String, surface: Surface?) {
        var colorSpace: ColorSpace? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeFile(path, options)
            colorSpace = options.outColorSpace
            colorSpace?.let {
                Log.i(TAG, "prepare: get color space: ${it.name}")
            }
        }

        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        colorSpace?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && it != ColorSpace.get(ColorSpace.Named.SRGB)) {
                options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
                Log.i(TAG, "prepare: inPreferredColorSpace s-rgb")
            }
        }

        val bitmap = BitmapFactory.decodeFile(path, options)
        if (bitmap != null) {
            mWidth = bitmap.width
            mHeight = bitmap.height
            mRotate = getImageOrientation(path)
            mRgbaBuffer = bitmapToByteBuffer(bitmap)
            mListener?.onVideoTrackPrepared(mWidth, mHeight, -1.0)
            bitmap.recycle()
            return
        }

        Log.e(TAG, "prepare: decodeFile failed")
        if (MimeTypeHelper.isPngFile(path)) {
            val pngData = PngReader().load(path)
            if (pngData.isValid()) {
                mWidth = pngData.width
                mHeight = pngData.height
                mRotate = 0
                mRgbaBuffer = pngData.buffer
                mListener?.onVideoTrackPrepared(mWidth, mHeight, -1.0)
            } else {
                Log.e(TAG, "prepare: use Custom PngReader decode failed")
            }
        }
    }

    private fun getImageOrientation(imagePath: String): Int {
        var orientation = 0
        try {
            val exif = ExifInterface(imagePath)
            orientation = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
                ExifInterface.ORIENTATION_NORMAL -> 0
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return orientation
    }

    override fun start() {
        Log.i(TAG, "start: ")
        mRgbaBuffer?.let {
            it.rewind()
            mListener?.onVideoFrameArrived(mWidth, mHeight, FORMAT_RGBA, it.array(), null, null)
            mListener?.onPlayComplete()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val bytes = bitmap.byteCount
        val buffer = ByteBuffer.allocateDirect(bytes)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()
        return buffer
    }

    override fun resume() {
        Log.i(TAG, "resume: ")
    }

    override fun pause() {
        Log.i(TAG, "pause: ")
    }

    override fun stop() {
        Log.i(TAG, "stop: ")
    }

    override fun release() {
        Log.i(TAG, "release: ")
    }

    override fun seek(position: Double): Boolean {
        Log.i(TAG, "seek: $position")
        return true
    }

    override fun setMute(mute: Boolean) {
        Log.i(TAG, "setMute: ")
    }

    override fun getRotate(): Int {
        return mRotate
    }

    override fun getDuration(): Double {
        return 0.0
    }
}