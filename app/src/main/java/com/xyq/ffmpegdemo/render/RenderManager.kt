package com.xyq.ffmpegdemo.render

import android.content.Context
import android.graphics.Bitmap
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

    private var mVideoRotate = 0

    private var mGreyIdentity = 0.0f

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
                OesDrawer(context)
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
        mVideoDrawer?.pushData(data)
    }

    fun setVideoRotate(rotate: Int) {
        mVideoRotate = rotate
    }

    fun setVideoSize(width: Int, height: Int) {
        mVideoWidth = width
        mVideoHeight = height
    }

    fun setSurfaceSize(width: Int, height: Int) {
        mSurfaceWidth = width
        mSurfaceHeight = height
    }

    fun setGreyFilterProgress(value: Float) {
        mGreyIdentity = if (value < 0) {
            0.0f
        } else if (value > 1.0f) {
            1.0f
        } else {
            value
        }
    }

    fun draw() {
        if (mVideoDrawer == null) return

        val rotate = mVideoRotate
        var videoWidth = mVideoWidth
        var videoHeight = mVideoHeight
        if (rotate == 90 || rotate == 270) {
            videoWidth = mVideoHeight
            videoHeight = mVideoWidth
        }
        mVideoDrawer!!.setRotate(rotate) // 视频旋转处理
        mVideoDrawer!!.setVideoSize(videoWidth, videoHeight)
        mVideoDrawer!!.setWorldSize(mSurfaceWidth, mSurfaceHeight)

        // draw video
        val videoOutputId = mVideoDrawer!!.drawToFbo()

        // draw filter
        if (mGreyFilter == null) {
            mGreyFilter = GreyFilter(mContext)
            mGreyFilter!!.init(false)
        }
        mGreyFilter!!.setVideoSize(videoWidth, videoHeight)
        mGreyFilter!!.setWorldSize(mSurfaceWidth, mSurfaceHeight)
        mGreyFilter!!.setProgress(mGreyIdentity)
        val greyOutputId = mGreyFilter!!.drawToFbo(videoOutputId)

        // draw to screen
        mDisplayDrawer?.setRotate(0)
        mDisplayDrawer?.setVideoSize(videoWidth, videoHeight)
        mDisplayDrawer?.setWorldSize(mSurfaceWidth, mSurfaceHeight)
        mDisplayDrawer?.draw(greyOutputId)
    }

}