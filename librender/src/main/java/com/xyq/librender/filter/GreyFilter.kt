package com.xyq.librender.filter

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.xyq.librender.R
import com.xyq.librender.core.RgbaDrawer
import com.xyq.librender.model.RenderData

class GreyFilter(context: Context): RgbaDrawer(context) {

    companion object {
        private const val TAG = "GreyFilter"
    }

    private var mFilterProgressHandler = -1
    private var mFilterProgress = 0f

    override fun getFragmentShader(): Int {
        return R.raw.fragment_grey_filter
    }

    override fun onInitParam() {
        super.onInitParam()
        mFilterProgressHandler = GLES20.glGetUniformLocation(mProgram, "progress")
    }

    override fun uploadData(textures: IntArray, data: RenderData?) {
        super.uploadData(textures, data)
        GLES20.glUniform1f(mFilterProgressHandler, mFilterProgress)
    }

    fun setProgress(value: Float) {
        if (mFilterProgress != value) {
            mFilterProgress = value
            Log.i(TAG, "setFilterProgress: $value")
        }
    }
}