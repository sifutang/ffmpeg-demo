package com.xyq.librender.test

import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.xyq.librender.R
import com.xyq.librender.RenderManager
import com.xyq.librender.model.RenderData
import com.xyq.libutils.FileUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class RenderTestActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    companion object {
        private const val TEST_YUV_FILE_WIDTH = 800
        private const val TEST_YUV_FILE_HEIGHT = 480

        private const val TAG = "RenderTestActivity"
    }

    private lateinit var mGLSurfaceView: GLSurfaceView
    private lateinit var mRenderManager: RenderManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_render_test)

        mGLSurfaceView = findViewById(R.id.gl_surface_view)
        mGLSurfaceView.setEGLContextClientVersion(2)
        mGLSurfaceView.setRenderer(this)

        mRenderManager = RenderManager(applicationContext)
        mRenderManager.makeCurrent(RenderManager.RenderFormat.NV12)

        mRenderManager.setVideoSize(TEST_YUV_FILE_WIDTH, TEST_YUV_FILE_HEIGHT)
        mRenderManager.pushVideoData(RenderManager.RenderFormat.NV12, prepareNV12Data())
    }

    private fun prepareNV12Data(): RenderData {
        val buffer = FileUtils.read("yuv_test_nv12.yuv", this)
        val yBuffer = ByteBuffer.allocateDirect(TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT)
            .order(ByteOrder.nativeOrder())
        yBuffer.put(buffer, 0, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT)
        yBuffer.position(0)

        val uvBuffer = ByteBuffer.allocateDirect(TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 2)
            .order(ByteOrder.nativeOrder())
        uvBuffer.put(buffer, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 2)
        uvBuffer.position(0)

        return RenderData(TEST_YUV_FILE_WIDTH, TEST_YUV_FILE_HEIGHT, yBuffer, uvBuffer, null)
    }

    override fun onDestroy() {
        super.onDestroy()
        mGLSurfaceView.queueEvent {
            mRenderManager.release()
            Log.i(TAG, "onDestroy: release render")
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        mRenderManager.init()
        Log.i(TAG, "onSurfaceCreated: ")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        mRenderManager.setCanvasSize(width, height)
        Log.i(TAG, "onSurfaceChanged: $width, $height")
    }

    override fun onDrawFrame(gl: GL10?) {
        mRenderManager.draw()
    }
}
