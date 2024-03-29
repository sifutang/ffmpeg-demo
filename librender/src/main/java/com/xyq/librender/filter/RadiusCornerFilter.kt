package com.xyq.librender.filter

import android.content.Context
import android.opengl.GLES30
import com.xyq.librender.R
import com.xyq.librender.core.RgbaDrawer
import com.xyq.librender.model.RenderData

class RadiusCornerFilter(context: Context): BaseFilter() {

    companion object {
        const val VAL_RADIUS = "PROGRESS"
    }

    private var mRadius = 0f

    init {
        mDrawer = object: RgbaDrawer(context) {

            private var radiusHandler = -1
            private var texSizeHandler = -1
            private var bgColorHandler = -1

            override fun getFragmentShader(): Int {
                return R.raw.fragment_filter_radius_corner
            }

            override fun onInitParam() {
                super.onInitParam()
                radiusHandler = GLES30.glGetUniformLocation(mProgram, "radius")
                texSizeHandler = GLES30.glGetUniformLocation(mProgram, "texSize")
                bgColorHandler = GLES30.glGetUniformLocation(mProgram, "bgColor")
            }

            override fun uploadData(textures: IntArray, data: RenderData?) {
                super.uploadData(textures, data)
                GLES30.glUniform1f(radiusHandler, mRadius)
                GLES30.glUniform2f(texSizeHandler, mFrameWidth.toFloat(), mFrameHeight.toFloat())
                GLES30.glUniform4f(bgColorHandler, mBackgroundColor[0], mBackgroundColor[1], mBackgroundColor[2], mBackgroundColor[3])
            }
        }
    }

    override fun doProcess(tex: Int): Int {
        return mDrawer!!.drawToTex(tex)
    }

    override fun setVal(key: String, value: Float) {
        if (key == VAL_RADIUS) {
            mRadius = value
        }
    }
}