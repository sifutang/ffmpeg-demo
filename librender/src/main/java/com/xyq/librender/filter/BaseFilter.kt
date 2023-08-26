package com.xyq.librender.filter

import com.xyq.librender.core.RgbaDrawer
import com.xyq.librender.model.Pipeline

abstract class BaseFilter : IFilter {

    protected var mDrawer: RgbaDrawer? = null

    private var mNextFilter: IFilter? = null

    abstract fun doProcess(tex: Int): Int

    override fun process(pipeline: Pipeline): Int {
        if (!mDrawer!!.hasInit()) {
            mDrawer!!.init(false)
        }
        mDrawer!!.setCanvasSize(pipeline.canvasWidth, pipeline.canvasHeight)
        mDrawer!!.setVideoSize(pipeline.width, pipeline.height)
        val id = doProcess(pipeline.texId)
        return if (mNextFilter == null) {
            id
        } else {
            val texId = mNextFilter!!.process(pipeline.copy(texId = id))
            texId
        }
    }

    override fun setVal(key: String, value: Float) {
        // nothing now
    }

    override fun setNext(filter: IFilter) {
        mNextFilter = filter
    }

    override fun getNextFilter(): IFilter? {
        return mNextFilter
    }

    override fun release() {
        mDrawer?.release()
    }
}