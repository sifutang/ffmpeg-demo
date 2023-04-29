package com.xyq.ffmpegdemo.render.core

import android.content.Context
import android.opengl.GLES20
import com.xyq.ffmpegdemo.R
import com.xyq.ffmpegdemo.render.model.RenderData

class NV12Drawer(context: Context): BaseDrawer(context) {

    private var mYTextureHandler = -1
    private var mUVTextureHandler = -1

    override fun getVertexShader(): Int {
        return R.raw.vertex_camera
    }

    override fun getFragmentShader(): Int {
        return R.raw.fragment_nv12
    }

    override fun getTextureSize(): Int {
        return 2
    }

    override fun onInitParam() {
        mYTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerY")
        mUVTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerUV")
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

        // uv texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1])
        data?.let {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, it.w / 2, it.h / 2,
                0, GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, it.u)
        }
        GLES20.glUniform1i(mUVTextureHandler, 1)
    }
}