package com.xyq.ffmpegdemo.render

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import com.xyq.ffmpegdemo.R
import com.xyq.ffmpegdemo.utils.FileUtils
import com.xyq.ffmpegdemo.utils.OpenGLTools
import java.nio.ByteBuffer

class YuvDrawer(private val mContext: Context): BaseDrawer() {

    companion object {
        private const val TAG = "VideoDrawer"
    }

    private var mFilterProgressHandler = -1

    private var mYTextureHandler = -1
    private var mUTextureHandler = -1
    private var mVTextureHandler = -1

    private var mFrameWidth = 0
    private var mFrameHeight = 0

    private var mYBuffer: ByteBuffer? = null
    private var mUBuffer: ByteBuffer? = null
    private var mVBuffer: ByteBuffer? = null

    private var mFilterProgress = 0f

    fun pushYuv(width: Int, height: Int, y: ByteArray?, u: ByteArray?, v: ByteArray?) {
        synchronized(this) {
            mFrameWidth = width
            mFrameHeight = height
            mYBuffer = ByteBuffer.wrap(y!!)
            mUBuffer = ByteBuffer.wrap(u!!)
            mVBuffer = ByteBuffer.wrap(v!!)
        }
    }

    override fun setFilterProgress(value: Float) {
        mFilterProgress = value
        Log.i(TAG, "setFilterProgress: $value")
    }

    override fun getVertexShader(): String {
        return FileUtils.readTextFileFromResource(mContext, R.raw.vertex_yuv)
    }

    override fun getFragmentShader(): String {
        return FileUtils.readTextFileFromResource(mContext, R.raw.fragment_yuv)
    }

    override fun onInit() {
        mTextures = OpenGLTools.createTextureIds(3)
        for (texture in mTextures!!) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            mYTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerY")
            mUTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerU")
            mVTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerV")

            mFilterProgressHandler = GLES20.glGetAttribLocation(mProgram, "progress")
        }
    }

    override fun onDraw() {
        synchronized(this) {
            GLES20.glVertexAttrib1f(mFilterProgressHandler, mFilterProgress)

            // y texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures!![0])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mFrameWidth, mFrameHeight,
                0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mYBuffer)
            GLES20.glUniform1i(mYTextureHandler, 0)

            // u texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures!![1])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mFrameWidth / 2, mFrameHeight / 2,
                0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mUBuffer)
            GLES20.glUniform1i(mUTextureHandler, 1)

            // v texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE2)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextures!![2])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mFrameWidth / 2, mFrameHeight / 2,
                0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mVBuffer)
            GLES20.glUniform1i(mVTextureHandler, 2)
        }
    }

    override fun onRelease() {
        mTextures?.apply {
            GLES20.glDeleteTextures(3, mTextures, 0)
        }
        mTextures = null
    }
}