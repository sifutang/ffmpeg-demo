package com.xyq.ffmpegdemo.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.xyq.ffmpegdemo.utils.OpenGLTools
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class SimpleRender: GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "SimpleRender"
    }

    private val mDrawers = mutableListOf<IDrawer>()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        Log.i(TAG, "onSurfaceCreated: ")
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        for (drawer in mDrawers) {
            val textureIds = OpenGLTools.createTextureIds(drawer.getTextureSize())
            drawer.setTextures(textureIds)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        Log.i(TAG, "onSurfaceChanged: $width, $height")
        GLES20.glViewport(0, 0, width, height)
        for (drawer in mDrawers) {
            drawer.setWorldSize(width, height)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        mDrawers.forEach {
            it.draw()
        }
    }

    fun addDrawer(drawer: IDrawer) {
        mDrawers.add(drawer)
    }

    fun release() {
        mDrawers.forEach {
            it.release()
        }
    }
}