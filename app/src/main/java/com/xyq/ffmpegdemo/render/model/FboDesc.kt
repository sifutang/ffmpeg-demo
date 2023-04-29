package com.xyq.ffmpegdemo.render.model

import android.opengl.GLES20
import com.xyq.ffmpegdemo.render.utils.OpenGLTools

/**
 * RGBA format
 */
class FboDesc(
    private var mWidth: Int = -1,
    private var mHeight: Int = -1
) {

    private var mFboId: Int = -1
    private var mFboTextureId: Int = -1

    init {
        mFboId = OpenGLTools.createFBO()
        mFboTextureId = OpenGLTools.createFBOTexture(mFboId, mWidth, mHeight)
    }

    fun updateSize(width: Int, height: Int) {
        if (mWidth != width || mHeight != height) {
            mWidth = width
            mHeight = height
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mFboTextureId)
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
                0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_NONE)
        }
    }

    fun isValid(): Boolean {
        return mFboId >= 0 && mFboTextureId >= 0 && mWidth >= 0 && mHeight >= 0
    }

    fun bind() {
        OpenGLTools.bindFBO(mFboId)
    }

    fun unBind() {
        OpenGLTools.unbindFBO()
    }

    fun getTextureId(): Int {
        return mFboTextureId
    }

    fun release() {
        OpenGLTools.deleteFBO(mFboId, mFboTextureId)
    }
}