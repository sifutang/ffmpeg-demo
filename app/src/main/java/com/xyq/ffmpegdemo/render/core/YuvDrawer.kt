package com.xyq.ffmpegdemo.render.core

import android.content.Context
import android.opengl.GLES20
import com.xyq.ffmpegdemo.R
import com.xyq.ffmpegdemo.render.model.RenderData

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
        mYTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerY")
        mUTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerU")
        mVTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerV")
    }

    override fun uploadData(textures: IntArray, data: RenderData?) {
        // y texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        data?.let {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, it.w, it.h,
                0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, it.y)
        }

        GLES20.glUniform1i(mYTextureHandler, 0)

        // u texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        data?.let {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, it.w / 2, it.h / 2,
                0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, it.u)
        }
        GLES20.glUniform1i(mUTextureHandler, 1)

        // v texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[2])
        data?.let {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, it.w / 2, it.h / 2,
                0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, it.v)
        }
        GLES20.glUniform1i(mVTextureHandler, 2)
    }
}