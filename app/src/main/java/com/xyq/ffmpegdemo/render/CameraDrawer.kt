package com.xyq.ffmpegdemo.render

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.util.Log
import com.xyq.ffmpegdemo.R
import com.xyq.ffmpegdemo.utils.FileUtils
import com.xyq.ffmpegdemo.utils.OpenGLTools

class CameraDrawer(private val mContext: Context): BaseDrawer() {

    companion object {
        private const val TAG = "CameraDrawer"
    }

    private var mTextureHandler = -1

    private var mFilterProgressHandler = -1

    private var mFilterProgress = 0f

    private var mLock = Object()
    private var mSurfaceTexture: SurfaceTexture? = null

    override fun getVertexShader(): String {
        return FileUtils.readTextFileFromResource(mContext, R.raw.vertex_camera)
    }

    override fun getFragmentShader(): String {
        return FileUtils.readTextFileFromResource(mContext, R.raw.fragment_camera)
    }

    override fun onInit() {
        mTextures = OpenGLTools.createTextureIds(1)

        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextures!![0])
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        mTextureHandler = GLES20.glGetUniformLocation(mProgram, "uTexture")
        mFilterProgressHandler = GLES20.glGetAttribLocation(mProgram, "progress")

        synchronized(mLock) {
            mSurfaceTexture = SurfaceTexture(mTextures!![0])
            mLock.notify()
        }
        Log.i(TAG, "onInit: ")
    }

    override fun onDraw() {
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextures!![0])
        GLES20.glUniform1i(mTextureHandler, 0)

        GLES20.glVertexAttrib1f(mFilterProgressHandler, mFilterProgress)

        mSurfaceTexture!!.updateTexImage()
    }

    override fun onRelease() {
        GLES20.glDeleteTextures(1, mTextures, 0)
        mSurfaceTexture?.release()
    }

    override fun setFilterProgress(value: Float) {
        mFilterProgress = value
        Log.i(TAG, "setFilterProgress: $value")
    }

    fun getSurfaceTexture(): SurfaceTexture {
        while (mSurfaceTexture == null) {
            synchronized(mLock) {
                mLock.wait()
            }
        }
        return mSurfaceTexture!!
    }
}