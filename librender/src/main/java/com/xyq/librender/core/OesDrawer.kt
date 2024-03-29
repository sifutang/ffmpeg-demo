package com.xyq.librender.core

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.util.Log
import com.xyq.librender.R
import com.xyq.librender.model.RenderData

class OesDrawer(context: Context): BaseDrawer(context) {

    companion object {
        private const val TAG = "CameraDrawer"
    }

    private var mLock = Object()
    private var mTextureHandler = -1
    private var mSurfaceTexture: SurfaceTexture? = null

    override fun getVertexShader(): Int {
        return R.raw.vertex_normal
    }

    override fun getFragmentShader(): Int {
        return R.raw.fragment_oes
    }

    override fun getTextureSize(): Int {
        return 1
    }

    override fun getTextureType(): Int {
        return GLES11Ext.GL_TEXTURE_EXTERNAL_OES
    }

    override fun onInitParam() {
        mTextureHandler = GLES30.glGetUniformLocation(mProgram, "samplerOES")

        synchronized(mLock) {
            mSurfaceTexture = SurfaceTexture(mTextures!![0])
            mLock.notify()
        }
        Log.i(TAG, "onInit: ")
    }

    override fun uploadData(textures: IntArray, data: RenderData?) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES30.glUniform1i(mTextureHandler, 0)
        mSurfaceTexture!!.updateTexImage()
    }

    override fun onRelease() {
        mSurfaceTexture?.release()
        Log.i(TAG, "onRelease")
    }

    fun getSurfaceTexture(): SurfaceTexture {
        Log.i(TAG, "getSurfaceTexture: ")
        while (mSurfaceTexture == null) {
            synchronized(mLock) {
                mLock.wait()
            }
        }
        Log.i(TAG, "getSurfaceTexture: end")
        return mSurfaceTexture!!
    }
}