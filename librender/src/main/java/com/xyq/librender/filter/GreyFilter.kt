package com.xyq.librender.filter

import android.content.Context
import android.opengl.GLES20
import com.xyq.librender.R
import com.xyq.librender.core.RgbaDrawer
import com.xyq.librender.model.RenderData

class GreyFilter(context: Context): BaseFilter() {

    companion object {
        const val VAL_PROGRESS = "PROGRESS"
    }

    private var mFilterProgress = 0f

    init {
        mDrawer = object: RgbaDrawer(context) {

            private var progressHandler = -1

            override fun getFragmentShader(): Int {
                return R.raw.fragment_filter_grey
            }

            override fun onInitParam() {
                super.onInitParam()
                progressHandler = GLES20.glGetUniformLocation(mProgram, "progress")
            }

            override fun uploadData(textures: IntArray, data: RenderData?) {
                super.uploadData(textures, data)
                GLES20.glUniform1f(progressHandler, mFilterProgress)
            }
        }
    }

    override fun setVal(key: String, value: Float) {
        if (key == VAL_PROGRESS) {
            mFilterProgress = value
        }
    }

    override fun doProcess(tex: Int): Int {
        return mDrawer!!.drawToFbo(tex)
    }
}