package com.xyq.ffmpegdemo.render.core

import android.content.Context
import android.opengl.GLES20
import com.xyq.ffmpegdemo.R
import com.xyq.ffmpegdemo.render.model.RenderData

open class RgbaDrawer(context: Context): BaseDrawer(context) {

    private var mRgbaTextureHandler = -1

    override fun getVertexShader(): Int {
        return R.raw.vertex_camera
    }

    override fun getFragmentShader(): Int {
        return R.raw.fragment_rgba
    }

    override fun getTextureSize(): Int {
        return 1
    }

    override fun onInitParam() {
        mRgbaTextureHandler = GLES20.glGetUniformLocation(mProgram, "sampler")
    }

    override fun uploadData(textures: IntArray, data: RenderData?) {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        data?.let {
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, it.w, it.h,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, it.y)
        }
        GLES20.glUniform1i(mRgbaTextureHandler, 0)
    }
}