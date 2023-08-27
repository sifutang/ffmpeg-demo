package com.xyq.librender

import android.content.Context
import android.graphics.Bitmap
import com.xyq.librender.model.RenderData
import com.xyq.librender.utils.OpenGLTools
import com.xyq.librender.utils.TextureHelper
import com.xyq.librender.core.IDrawer
import com.xyq.librender.core.NV12Drawer
import com.xyq.librender.core.OesDrawer
import com.xyq.librender.core.RgbaDrawer
import com.xyq.librender.core.YuvDrawer
import com.xyq.librender.filter.IFilter
import com.xyq.librender.model.Pipeline

class RenderManager(private val mContext: Context) {

    enum class RenderFormat {
        YUV420,
        NV12,
        RGBA,
        OES
    }

    private val mRenderCache = HashMap<RenderFormat, IDrawer>()

    private var mCanvasWidth = -1
    private var mCanvasHeight = -1

    private var mVideoWidth = -1
    private var mVideoHeight = -1

    private var mVideoRotate = 0

    private var mVideoDrawer: IDrawer? = null
    private var mDisplayDrawer: IDrawer? = null

    private var mWaterMarkTextureId = -1
    private val mFilterProcessor = FilterProcessor()

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
        mFilterProcessor.release()
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

    fun setCanvasSize(width: Int, height: Int) {
        mCanvasWidth = width
        mCanvasHeight = height
    }

    fun addFilter(filter: IFilter) {
        mFilterProcessor.addFilter(filter)
    }

    fun removeFilter(filter: IFilter) {
        mFilterProcessor.removeFilter(filter)
    }

    fun draw() {
        if (mVideoDrawer == null) return

        // step1: draw video
        val rotate = mVideoRotate
        var videoWidth = mVideoWidth
        var videoHeight = mVideoHeight
        if (rotate == 90 || rotate == 270) {
            videoWidth = mVideoHeight
            videoHeight = mVideoWidth
        }
        mVideoDrawer!!.setRotate(rotate) // 视频旋转处理
        mVideoDrawer!!.setVideoSize(videoWidth, videoHeight)
        mVideoDrawer!!.setCanvasSize(mCanvasWidth, mCanvasHeight)
        val videoOutputId = mVideoDrawer!!.drawToFbo()

        // step2: draw filter
        val pipeline = Pipeline(videoOutputId, mCanvasWidth, mCanvasHeight, videoWidth, videoHeight)
        var texId = mFilterProcessor.process(pipeline)
        if (texId < 0) {
            texId = videoOutputId
        }

        // step3: draw to screen
        mDisplayDrawer?.setRotate(0)
        mDisplayDrawer?.setVideoSize(videoWidth, videoHeight)
        mDisplayDrawer?.setCanvasSize(mCanvasWidth, mCanvasHeight)
        mDisplayDrawer?.draw(texId)
    }

}