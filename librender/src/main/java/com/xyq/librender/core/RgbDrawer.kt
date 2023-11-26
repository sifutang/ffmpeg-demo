package com.xyq.librender.core

import android.content.Context
import android.opengl.GLES30
import com.xyq.librender.R
import com.xyq.librender.model.RenderData

open class RgbDrawer(context: Context): BaseDrawer(context) {

    private var mTexHandler = -1

    override fun getVertexShader(): Int {
        return R.raw.vertex_normal
    }

    override fun getFragmentShader(): Int {
        return R.raw.fragment_rgba
    }

    override fun getTextureSize(): Int {
        return 1
    }

    override fun onInitParam() {
        mTexHandler = GLES30.glGetUniformLocation(mProgram, "sampler")
    }

    override fun uploadData(textures: IntArray, data: RenderData?) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        data?.let {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGB, it.w, it.h,
                0, GLES30.GL_RGB, GLES30.GL_UNSIGNED_BYTE, it.y)
        }
        GLES30.glUniform1i(mTexHandler, 0)
    }
}