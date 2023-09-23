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
import java.nio.ByteBuffer


class ImagePlayer: IPlayer {

    private var mBitmap: Bitmap? = null
    private var mListener: IPlayerListener? = null
    private var mRotate = 0

    companion object {
        private const val TAG = "ImagePlayer"
    }

    override fun init() {
        Log.i(TAG, "init: ")
    }

    override fun setPlayerListener(listener: IPlayerListener) {
        mListener = listener
    }

    override fun prepare(path: String, surface: Surface?) {
        if (mBitmap?.isRecycled == false) {
            mBitmap?.recycle()
        }
        val options = BitmapFactory.Options()
        options.inPreferredConfig = Bitmap.Config.ARGB_8888
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            options.inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        }
        val bitmap = BitmapFactory.decodeFile(path, options) ?: throw RuntimeException("bitmap is null")
        mBitmap = bitmap

        mListener?.onVideoTrackPrepared(bitmap.width, bitmap.height)
        mRotate = getImageOrientation(path)
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
        val rgbaBuffer = bitmapToByteBuffer(mBitmap!!)
        mListener?.onVideoFrameArrived(mBitmap!!.width, mBitmap!!.height, 0x02, rgbaBuffer.array(), null, null) // 0x02 is RGBA
        mListener?.onPlayComplete()
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
        if (mBitmap?.isRecycled == false) {
            mBitmap?.recycle()
        }
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