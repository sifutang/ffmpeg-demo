package com.xyq.librender.core

import android.content.Context
import android.opengl.GLES30
import com.xyq.librender.R
import com.xyq.librender.model.RenderData

class YuvDrawer(context: Context): BaseDrawer(context) {

    private var mYTextureHandler = -1
    private var mUTextureHandler = -1
    private var mVTextureHandler = -1

    override fun getVertexShader(): Int {
        return R.raw.vertex_normal
    }

    override fun getFragmentShader(): Int {
        return R.raw.fragment_yuv
    }

    override fun getTextureSize(): Int {
        return 3
    }

    override fun onInitParam() {
        mYTextureHandler = GLES30.glGetUniformLocation(mProgram, "samplerY")
        mUTextureHandler = GLES30.glGetUniformLocation(mProgram, "samplerU")
        mVTextureHandler = GLES30.glGetUniformLocation(mProgram, "samplerV")
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

        // u texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[1])
        data?.let {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, it.w / 2, it.h / 2,
                0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, it.u)
        }
        GLES30.glUniform1i(mUTextureHandler, 1)

        // v texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textures[2])
        data?.let {
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_LUMINANCE, it.w / 2, it.h / 2,
                0, GLES30.GL_LUMINANCE, GLES30.GL_UNSIGNED_BYTE, it.v)
        }
        GLES30.glUniform1i(mVTextureHandler, 2)
    }
}