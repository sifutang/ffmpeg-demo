package com.xyq.ffmpegdemo.render

import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.xyq.ffmpegdemo.utils.OpenGLTools
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class NV12Drawer: IDrawer {

    companion object {
        private const val TAG = "NV12Drawer"
    }

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

    private var mWorldWidth = -1
    private var mWorldHeight = -1
    private var mVideoWidth = -1
    private var mVideoHeight = -1
    private var mWidthRatio = 1f
    private var mHeightRatio = 1f

    private var mMatrix: FloatArray? = null

    private var mProgram = -1

    private var mVertexPosHandler = -1

    private var mTexturePosHandler = -1

    private var mFilterProgressHandler = -1

    private var mYTextureHandler = -1
    private var mUVTextureHandler = -1

    private var mVertexMatrixHandler = -1

    private lateinit var mVertexBuffer: FloatBuffer

    private lateinit var mTextureBuffer: FloatBuffer

    init {
        initPos()
    }

    private fun initPos() {
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

    private fun getTextureSize(): Int {
        return mYuvTextures.size
    }

    private var mInitRunnable: Runnable? = null
    override fun init(async: Boolean) {
        mInitRunnable = Runnable {
            val size = getTextureSize()
            mYuvTextures = OpenGLTools.createTextureIds(size)
            for (texture in mYuvTextures) {
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            }
        }
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

    override fun draw() {
        if (mYuvTextures[0] == -1) {
            mInitRunnable?.run()
        }
        if (mYuvTextures[0] != -1) {
            initDefMatrix()
            createGLProgram()
            synchronized(this) {
                doDraw()
            }
        }
    }

    override fun release() {
        GLES20.glDisableVertexAttribArray(mVertexPosHandler)
        GLES20.glDisableVertexAttribArray(mTexturePosHandler)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        if (mYuvTextures[0] != -1) {
            GLES20.glDeleteTextures(mYuvTextures.size, mYuvTextures, 0)
        }
        GLES20.glDeleteProgram(mProgram)
    }

    private var mFrameWidth = 0
    private var mFrameHeight = 0
    private var mYuvTextures = intArrayOf(-1, -1)
    private var mYBuffer: ByteBuffer? = null
    private var mUVBuffer: ByteBuffer? = null

    fun pushNv21(width: Int, height: Int, y: ByteArray?, uv: ByteArray?) {
        synchronized(this) {
            mFrameWidth = width
            mFrameHeight = height
            mYBuffer = ByteBuffer.wrap(y!!)
            mUVBuffer = ByteBuffer.wrap(uv!!)
        }
    }

    private var mFilterProgress = 0f
    override fun setFilterProgress(value: Float) {
        mFilterProgress = value
        Log.i(TAG, "setFilterProgress: $value")
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

    private fun createGLProgram() {
        if (mProgram == -1) {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, getVertexShader())
            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, getFragmentShader())

            mProgram = GLES20.glCreateProgram()
            GLES20.glAttachShader(mProgram, vertexShader)
            GLES20.glAttachShader(mProgram, fragmentShader)
            GLES20.glLinkProgram(mProgram)

            mVertexPosHandler = GLES20.glGetAttribLocation(mProgram, "aPosition")
            mTexturePosHandler = GLES20.glGetAttribLocation(mProgram, "aCoordinate")

            mFilterProgressHandler = GLES20.glGetAttribLocation(mProgram, "progress")

            mYTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerY")
            mUVTextureHandler = GLES20.glGetUniformLocation(mProgram, "samplerUV")

            mVertexMatrixHandler = GLES20.glGetUniformLocation(mProgram, "uMatrix")
        }

        GLES20.glUseProgram(mProgram)
    }

    private fun getVertexShader(): String {
        return "attribute vec4 aPosition;" +
                "uniform mat4 uMatrix;" +
                "attribute vec2 aCoordinate;" +
                "attribute float progress;" +
                "varying vec2 vCoordinate;" +
                "varying float vProgress;" +
                "void main() {" +
                "  gl_Position = aPosition * uMatrix;" +
                "  vCoordinate = aCoordinate;" +
                "  vProgress = progress;" +
                "}"
    }

    private fun getFragmentShader(): String {
        return "precision mediump float;" +
                "varying vec2 vCoordinate;" +
                "varying float vProgress;" +
                "uniform sampler2D samplerY;" +
                "uniform sampler2D samplerUV;" +
                "void main() {" +
                "    float y,u,v;" +
                "    y = texture2D(samplerY, vCoordinate).r;" +
                "    u = texture2D(samplerUV, vCoordinate).r - 0.5;" +
                "    v = texture2D(samplerUV, vCoordinate).a - 0.5;" +
                "    vec3 rgb;" +
                "    rgb.r = y + 1.403 * v;" +
                "    rgb.g = y - 0.344 * u - 0.714 * v;" +
                "    rgb.b = y + 1.770 * u;" +
                "    if (vCoordinate.x > vProgress) {" +
                "        gl_FragColor = vec4(rgb, 1);" +
                "    } else {" +
                "        float h = dot(rgb, vec3(0.3, 0.59, 0.21));" +
                "        gl_FragColor = vec4(h, h, h, 1);" +
                "    }" +
                "}"
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun doDraw() {
        if (mMatrix == null || mYBuffer == null) return

        GLES20.glEnableVertexAttribArray(mVertexPosHandler)
        GLES20.glEnableVertexAttribArray(mTexturePosHandler)

        GLES20.glUniformMatrix4fv(mVertexMatrixHandler, 1, false, mMatrix, 0)
        GLES20.glVertexAttrib1f(mFilterProgressHandler, mFilterProgress)

        // x,y -> size is 2
        GLES20.glVertexAttribPointer(mVertexPosHandler, 2, GLES20.GL_FLOAT, false, 0, mVertexBuffer)
        GLES20.glVertexAttribPointer(mTexturePosHandler, 2, GLES20.GL_FLOAT, false, 0, mTextureBuffer)

        // y texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[0])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, mFrameWidth, mFrameHeight,
            0, GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mYBuffer)
        GLES20.glUniform1i(mYTextureHandler, 0)

        // uv texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mYuvTextures[1])
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, mFrameWidth / 2, mFrameHeight / 2,
            0, GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, mUVBuffer)
        GLES20.glUniform1i(mUVTextureHandler, 1)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }
}