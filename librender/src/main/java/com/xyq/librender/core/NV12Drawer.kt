package com.xyq.librender.core

import android.content.Context
import android.opengl.GLES30
import com.xyq.librender.R
import com.xyq.librender.model.RenderData

class NV12Drawer(context: Context): BaseDrawer(context) {

    private var mYTextureHandler = -1
    private var mUVTextureHandler = -1

    override fun getVertexShader(): Int {
        return R.raw.vertex_normal
    }

    override fun getFragmentShader(): Int {
        return R.raw.fragment_nv12
    }

    override fun getTextureSize(): Int {
        return 2
    }

    override fun onInitParam() {
        mYTextureHandler = GLES30.glGetUniformLocation(mProgram, "samplerY")
        mUVTextureHandler = GLES30.glGetUniformLocation(mProgram, "samplerUV")
    }

    override fun uploadData(textures: IntArray, data: RenderData?) {
        // y texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[0])
        data?.let {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, it.w, it.h,
                0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, it.y)
        }
        GLES30.glUniform1i(mYTextureHandler, 0)

        // uv texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[1])
        data?.let {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE_ALPHA, it.w / 2, it.h / 2,
                0, GLES30.GL_LUMINANCE_ALPHA, GLES30.GL_UNSIGNED_BYTE, it.u)
        }
        GLES30.glUniform1i(mUVTextureHandler, 1)
    }
}