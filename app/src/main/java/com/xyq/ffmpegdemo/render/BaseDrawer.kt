package com.xyq.ffmpegdemo.render

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.xyq.ffmpegdemo.utils.ShaderHelper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

abstract class BaseDrawer : IDrawer {

    companion object {
        private const val TAG = "BaseDrawer"
    }

    protected var mProgram = -1

    protected var mTextures: IntArray? = null

    private val mVertexCoors = floatArrayOf(
        -1f, -1f,
        1f, -1f,
        -1f, 1f,
        1f, 1f
    )

    private val mTextureCoors = floatArrayOf(
        0f, 1f,
        1f, 1f,
        0f, 0f,
        1f, 0f
    )

    private var mInit = false
    private var mInitRunnable: Runnable? = null

    private var mWorldWidth = -1
    private var mWorldHeight = -1

    private var mVideoWidth = -1
    private var mVideoHeight = -1

    private var mWidthRatio = 1f
    private var mHeightRatio = 1f

    private var mMatrix: FloatArray? = null

    private var mVertexPosHandler = -1

    private var mTexturePosHandler = -1

    private var mVertexMatrixHandler = -1

    private var mVertexBuffer: FloatBuffer

    private var mTextureBuffer: FloatBuffer

    init {
        var byteBuffer = ByteBuffer.allocateDirect(mVertexCoors.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        mVertexBuffer = byteBuffer.asFloatBuffer()
        mVertexBuffer.put(mVertexCoors)
        mVertexBuffer.position(0)

        byteBuffer = ByteBuffer.allocateDirect(mTextureCoors.size * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        mTextureBuffer = byteBuffer.asFloatBuffer()
        mTextureBuffer.put(mTextureCoors)
        mTextureBuffer.position(0)
    }

    override fun setVideoSize(w: Int, h: Int) {
        mVideoWidth = w
        mVideoHeight = h
        Log.i(TAG, "setVideoSize: $w x $h")
    }

    override fun setWorldSize(w: Int, h: Int) {
        mWorldWidth = w
        mWorldHeight = h
        Log.i(TAG, "setWorldSize: $w x $h")
    }

    abstract fun getVertexShader(): String

    abstract fun getFragmentShader(): String

    abstract fun onInit()

    abstract fun onDraw()

    abstract fun onRelease()

    override fun init(async: Boolean) {
        mInitRunnable = Runnable {
            mProgram = ShaderHelper.buildProgram(getVertexShader(), getFragmentShader())

            mVertexPosHandler = GLES20.glGetAttribLocation(mProgram, "aPosition")
            mTexturePosHandler = GLES20.glGetAttribLocation(mProgram, "aCoordinate")

            mVertexMatrixHandler = GLES20.glGetUniformLocation(mProgram, "uMatrix")

            onInit()

            mInit = true
        }
        if (!async) {
            mInitRunnable?.run()
            mInitRunnable = null
        }
    }

    override fun draw() {
        mInitRunnable?.run()
        mInitRunnable = null

        if (mInit) {
            initDefMatrix()
            doDraw()
        }
    }

    override fun release() {
        GLES20.glDisableVertexAttribArray(mVertexPosHandler)
        GLES20.glDisableVertexAttribArray(mTexturePosHandler)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        onRelease()
        GLES20.glDeleteProgram(mProgram)
    }

    private fun initDefMatrix() {
        if (mMatrix != null) return
        if (mVideoWidth != -1 && mVideoHeight != -1 && mWorldWidth != -1 && mWorldHeight != -1) {
            mMatrix = FloatArray(16)
            val projMatrix = FloatArray(16)
            val originRatio = mVideoWidth / mVideoHeight.toFloat()
            val worldRatio = mWorldWidth / mWorldHeight.toFloat()
            if (originRatio > worldRatio) {
                val actualRatio = originRatio / worldRatio
                Matrix.orthoM(projMatrix, 0, -1f, 1f, -actualRatio, actualRatio, 3f, 5f)
                mHeightRatio = actualRatio
            } else {
                val actualRatio = worldRatio / originRatio
                Matrix.orthoM(projMatrix, 0, -actualRatio, actualRatio, -1f, 1f, 3f, 5f)
                mWidthRatio = actualRatio
            }
            val viewMatrix = FloatArray(16)
            Matrix.setLookAtM(viewMatrix, 0,
                0f, 0f, 5.0f,
                0f, 0f, 0f,
                0f, 1.0f, 0f
            )
            Matrix.multiplyMM(mMatrix, 0, projMatrix, 0, viewMatrix, 0)
        }
    }

    private fun doDraw() {
        if (mMatrix == null) return

        GLES20.glUseProgram(mProgram)

        GLES20.glEnableVertexAttribArray(mVertexPosHandler)
        GLES20.glEnableVertexAttribArray(mTexturePosHandler)

        GLES20.glUniformMatrix4fv(mVertexMatrixHandler, 1, false, mMatrix, 0)

        // x,y -> size is 2
        GLES20.glVertexAttribPointer(mVertexPosHandler, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer)
        GLES20.glVertexAttribPointer(mTexturePosHandler, 2, GLES20.GL_FLOAT, false, 0, mTextureBuffer)

        onDraw()

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
}