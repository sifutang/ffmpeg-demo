package com.xyq.librender.core

import android.util.Size
import com.xyq.librender.model.RenderData

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
    fun drawToTex(): Int

    /**
     * 外部传入纹理做输入，然后绘制到FBO
     */
    fun drawToTex(from: Int): Int

    fun release()

    fun setFrameSize(size: Size)

    fun getFrameSize(): Size

    fun setCanvasSize(size: Size)

    fun setRotate(rotate: Int)

    fun setBackgroundColor(r: Float, g: Float, b: Float, a: Float);
}