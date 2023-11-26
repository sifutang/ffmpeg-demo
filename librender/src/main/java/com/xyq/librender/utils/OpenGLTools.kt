package com.xyq.librender.utils

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer

object OpenGLTools {

    private const val TAG = "OpenGLTools"

    fun createTextureIds(size: Int): IntArray {
        val texture = IntArray(size)
        GLES30.glGenTextures(size, texture, 0)
        return texture
    }

    fun deleteTextureIds(texIds: IntArray) {
        GLES30.glDeleteTextures(texIds.size, texIds, 0)
    }

    fun createFBO(): Int {
        val fbs = IntArray(1)
        GLES30.glGenFramebuffers(1, fbs, 0)
        return fbs[0]
    }

    fun createFBOTexture(fboId: Int, width: Int, height: Int): Int {
        // create texture and config param
        val textures = createTextureIds(1)
        val textureId = textures[0]
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR.toFloat())
        GLES30.glTexParameterf(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR.toFloat())
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)

        // bind fbo
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height,
            0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, textureId, 0)
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "createFBOTexture failed")
        }

        // unbind
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_NONE)

        return textureId
    }

    fun bindFBO(fb: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb)
    }

    fun unbindFBO() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_NONE)
    }

    fun deleteFBO(fb: Int, texture: Int) {
        val texArr = IntArray(1)
        texArr[0] = texture
        GLES30.glDeleteTextures(1, texArr, 0)

        val fbArr = IntArray(1)
        fbArr[0] = fb
        GLES30.glDeleteFramebuffers(1, fbArr, 0)
    }

    fun readPixel(texId: Int, width: Int, height: Int): ByteBuffer {
        val buffer = ByteBuffer.allocate(width * height * 4)
        val outFrame = IntArray(1)
        val preFrame = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_FRAMEBUFFER_BINDING, preFrame, 0)

        GLES30.glGenFramebuffers(1, outFrame, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outFrame[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, texId, 0)
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buffer)
        GLES30.glFinish()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, preFrame[0])
        GLES30.glDeleteFramebuffers(1, outFrame, 0)

        return buffer
    }

    fun getAttribLocation(program: Int, key: String): Int {
        return GLES30.glGetAttribLocation(program, key)
    }

    fun getUniformLocation(program: Int, key: String): Int {
        return GLES30.glGetUniformLocation(program, key)
    }
}