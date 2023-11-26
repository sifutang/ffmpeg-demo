package com.xyq.librender

import android.content.Context
import android.util.Size
import com.xyq.librender.model.RenderData
import com.xyq.librender.core.IDrawer
import com.xyq.librender.core.NV12Drawer
import com.xyq.librender.core.OesDrawer
import com.xyq.librender.core.RgbDrawer
import com.xyq.librender.core.RgbaDrawer
import com.xyq.librender.core.YuvDrawer
import com.xyq.librender.filter.IFilter
import com.xyq.librender.model.FrameBuffer
import com.xyq.librender.model.Pipeline
import com.xyq.librender.utils.OpenGLTools

class RenderManager(private val mContext: Context) {

    enum class RenderFormat {
        YUV420, // i420
        NV12,
        RGBA, // R8G8B8A8
        OES,
        RGB, // R8G8B8
    }

    private val mRenderCache = HashMap<RenderFormat, IDrawer>()

    private var mCanvasSize = Size(-1, -1)
    private var mVideoSize = Size(-1, -1)
    private var mVideoRotate = 0

    private var mVideoDrawer: IDrawer? = null
    private var mDisplayDrawer: IDrawer? = null

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
            0x04 -> {
                RenderFormat.RGB
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
            RenderFormat.RGB -> {
                RgbDrawer(context)
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

    fun makeCurrent(format: RenderFormat?) {
        mVideoDrawer = if (format == null) {
            null
        } else {
            take(format, mContext)
        }
    }

    fun pushVideoData(format: RenderFormat, data: RenderData) {
        mVideoDrawer = take(format, mContext)
        mVideoDrawer?.pushData(data)
    }

    fun setVideoRotate(rotate: Int) {
        mVideoRotate = rotate
    }

    fun setVideoSize(width: Int, height: Int) {
        mVideoSize = Size(width, height)
    }

    fun setCanvasSize(width: Int, height: Int) {
        mCanvasSize = Size(width, height)
    }

    fun addFilter(filter: IFilter) {
        mFilterProcessor.addFilter(filter)
    }

    fun removeFilter(filter: IFilter) {
        mFilterProcessor.removeFilter(filter)
    }

    fun draw(readPixel: Boolean): FrameBuffer? {
        if (mVideoDrawer == null) {
            return null
        }

        // step1: draw video
        val rotate = mVideoRotate
        var videoSize = mVideoSize
        if (rotate == 90 || rotate == 270) {
            videoSize = Size(mVideoSize.height, mVideoSize.width)
        }
        mVideoDrawer!!.setRotate(rotate) // 视频旋转处理
        mVideoDrawer!!.setFrameSize(videoSize)
        mVideoDrawer!!.setCanvasSize(mCanvasSize)
        val videoOutputId = mVideoDrawer!!.drawToTex()

        // step2: draw filter
        val pipeline = Pipeline(videoOutputId, mCanvasSize, videoSize, 0)
        var texId = mFilterProcessor.process(pipeline)
        if (texId < 0) {
            texId = videoOutputId
        }

        // step3: draw to screen
        mDisplayDrawer?.setRotate(0)
        mDisplayDrawer?.setFrameSize(videoSize)
        mDisplayDrawer?.setCanvasSize(mCanvasSize)
        mDisplayDrawer?.draw(texId)

        // step4: read pixel if need
        var frameBuffer: FrameBuffer? = null
        if (readPixel) {
            val buf = OpenGLTools.readPixel(texId, videoSize.width, videoSize.height)
            frameBuffer = FrameBuffer(videoSize.width, videoSize.height, buf)
        }
        return frameBuffer
    }

}