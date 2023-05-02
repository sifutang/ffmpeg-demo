package com.xyq.ffmpegdemo.render.core

import android.util.Size
import com.xyq.ffmpegdemo.render.model.RenderData

interface IDrawer {

    fun init(async: Boolean)

    fun pushData(data: RenderData)

    /**
     * pushData传数据，然后绘制
     */
    fun draw()

    /**
     * 外部传入纹理做输入，然后绘制
     */
    fun draw(input: Int)

    /**
     * 内部生成FBO，绘制到FBO上的纹理
     * 返回纹理id
     */
    fun drawToFbo(): Int

    /**
     * 外部传入纹理做输入，然后绘制到FBO
     */
    fun drawToFbo(input: Int): Int

    fun release()

    fun setVideoSize(w: Int, h: Int)

    fun getVideoSize(): Size

    fun setWorldSize(w: Int, h: Int)

    fun setRotate(rotate: Int)
}