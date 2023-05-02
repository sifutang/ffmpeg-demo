package com.xyq.ffmpegdemo.render

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.xyq.ffmpegdemo.render.core.*
import com.xyq.ffmpegdemo.render.filter.GreyFilter
import com.xyq.ffmpegdemo.render.model.RenderData
import com.xyq.ffmpegdemo.render.utils.OpenGLTools
import com.xyq.ffmpegdemo.render.utils.TextureHelper

class RenderManager(private val mContext: Context) {

    companion object {
        private const val TAG = "RenderManager"
    }

    enum class RenderFormat {
        YUV420,
        NV12,
        RGBA,
        OES
    }

    private val mRenderCache = HashMap<RenderFormat, IDrawer>()

    private var mSurfaceWidth = -1
    private var mSurfaceHeight = -1

    private var mVideoWidth = -1
    private var mVideoHeight = -1

    private var mDisplayRotate = 0

    private var mVideoDrawer: IDrawer? = null
    private var mDisplayDrawer: IDrawer? = null
    private var mGreyFilter: GreyFilter? = null

    private var mWaterMarkTextureId = -1

    fun convert(format: Int): RenderFormat {
        return when (format) {
            0x00 -> {
                RenderFormat.YUV420
            }
            0x01 -> {
                RenderFormat.NV12
            }
            0x02 -> {
                RenderFormat.RGBA
            }
            0x03 -> {
                RenderFormat.OES
            }
            else -> {
                RenderFormat.RGBA
            }
        }
    }

    @Synchronized
    fun take(format: RenderFormat, context: Context): IDrawer {
        if (mRenderCache.contains(format)) {
            return mRenderCache[format]!!
        }
        val drawer = when (format) {
            RenderFormat.YUV420 -> {
                YuvDrawer(context)
            }
            RenderFormat.NV12 -> {
                NV12Drawer(context)
            }
            RenderFormat.RGBA -> {
                RgbaDrawer(context)
            }
            RenderFormat.OES -> {
                CameraDrawer(context)
            }
        }
        drawer.init(true)
        mRenderCache[format] = drawer
        return drawer
    }

    /**
     * need run gl thread
     */
    @Synchronized
    fun release(format: RenderFormat) {
        val render = mRenderCache.remove(format)
        render?.release()
    }

    /**
     * need run gl thread
     */
    @Synchronized
    fun release() {
        mRenderCache.keys.forEach {
            mRenderCache[it]?.release()
        }
        mRenderCache.clear()

        mGreyFilter?.release()
    }

    fun init() {
        mDisplayDrawer = take(RenderFormat.RGBA, mContext)
    }

    fun setWaterMark(bitmap: Bitmap) {
        if (mWaterMarkTextureId > 0) {
            val textures = IntArray(1)
            textures[0] = mWaterMarkTextureId
            OpenGLTools.deleteTextureIds(textures)
        }

        mWaterMarkTextureId = TextureHelper.loadTexture(bitmap)
    }

    fun makeCurrent(format: RenderFormat) {
        mVideoDrawer = take(format, mContext)
    }

    fun pushVideoData(format: RenderFormat, data: RenderData) {
        mVideoDrawer = take(format, mContext)
        mVideoDrawer?.setVideoSize(mVideoWidth, mVideoHeight)
        mVideoDrawer?.setWorldSize(mSurfaceWidth, mSurfaceHeight)
        mVideoDrawer?.pushData(data)
    }

    fun setDisplayRotate(rotate: Int) {
        mDisplayRotate = rotate
    }

    fun setVideoSize(width: Int, height: Int) {
        mVideoWidth = width
        mVideoHeight = height
        mVideoDrawer?.setVideoSize(width, height)
    }

    fun setSurfaceSize(width: Int, height: Int) {
        mSurfaceWidth = width
        mSurfaceHeight = height
        mVideoDrawer?.setWorldSize(width, height)
    }

    fun setGreyFilterProgress(value: Float) {
        mGreyFilter?.setProgress(value)
    }

    fun draw() {
        // TODO: add render chain
        mVideoDrawer?.let {
            // draw video
            val videoOutputId = it.drawToFbo()
            if (videoOutputId < 0) {
                Log.e(TAG, "onDrawFrame: err")
                return
            }

            // draw filter
            if (mGreyFilter == null) {
                mGreyFilter = GreyFilter(mContext)
                mGreyFilter!!.init(false)
            }
            mGreyFilter!!.setProgress(0.5f) // for demo
            mGreyFilter!!.setVideoSize(mVideoWidth, mVideoHeight)
            mGreyFilter!!.setWorldSize(mSurfaceWidth, mSurfaceHeight)
            val greyOutputId = mGreyFilter!!.drawToFbo(videoOutputId)

            mDisplayDrawer!!.setRotate(mDisplayRotate)
            mDisplayDrawer!!.setVideoSize(mVideoWidth, mVideoHeight)
            mDisplayDrawer!!.setWorldSize(mSurfaceWidth, mSurfaceHeight)

            // draw water mark
//            val waterMarkOutputId = mDisplayDrawer!!.drawToFbo(mWaterMarkTextureId)

            // draw to screen
            mDisplayDrawer?.draw(greyOutputId)
        }
    }

}