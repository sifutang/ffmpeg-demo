package com.xyq.ffmpegdemo.render.utils

import android.opengl.GLES20
import android.util.Log

object OpenGLTools {

    private const val TAG = "OpenGLTools"

    fun createTextureIds(size: Int): IntArray {
        val texture = IntArray(size)
        GLES20.glGenTextures(size, texture, 0)
        return texture
    }

    fun deleteTextureIds(texIds: IntArray) {
        GLES20.glDeleteTextures(texIds.size, texIds, 0)
    }

    fun createFBO(): Int {
        val fbs = IntArray(1)
        GLES20.glGenFramebuffers(1, fbs, 0)
        return fbs[0]
    }

    fun createFBOTexture(fboId: Int, width: Int, height: Int): Int {
        // create texture and config param
        val textures = createTextureIds(1)
        val textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_NONE)

        // bind fbo
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
            0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0)
        if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER) != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "createFBOTexture failed")
        }

        // unbind
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, GLES20.GL_NONE)
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE)

        return textureId
    }

    fun bindFBO(fb: Int) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb)
    }

    fun unbindFBO() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_NONE)
    }

    fun deleteFBO(fb: Int, texture: Int) {
        val texArr = IntArray(1)
        texArr[0] = texture
        GLES20.glDeleteTextures(1, texArr, 0)

        val fbArr = IntArray(1)
        fbArr[0] = fb
        GLES20.glDeleteFramebuffers(1, fbArr, 0)
    }
}