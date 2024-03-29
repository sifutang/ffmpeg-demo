package com.xyq.librender.test

import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.xyq.librender.R
import com.xyq.librender.RenderManager
import com.xyq.librender.filter.RadiusCornerFilter
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
    private var mRenderFormat = RenderManager.RenderFormat.NV12

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_render_test)
        Log.i(TAG, "onCreate: ")

        mGLSurfaceView = findViewById(R.id.gl_surface_view)
        mGLSurfaceView.setEGLContextClientVersion(3)
        mGLSurfaceView.setRenderer(this)
        mGLSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        mRenderManager = RenderManager(applicationContext)
        mRenderManager.setVideoSize(TEST_YUV_FILE_WIDTH, TEST_YUV_FILE_HEIGHT)

        val radiusCornerFilter = RadiusCornerFilter(applicationContext).apply {
            setVal(RadiusCornerFilter.VAL_RADIUS, 20f)
        }
        mRenderManager.addFilter(radiusCornerFilter)
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
        mRenderManager.draw(false)
    }

    fun setRenderFormat(format: RenderManager.RenderFormat) {
        mRenderFormat = format
        Log.i(TAG, "setRenderFormat: $format")
    }

    fun draw() {
        mRenderManager.pushVideoData(mRenderFormat, prepareData(mRenderFormat))
        mGLSurfaceView.requestRender()
        Log.i(TAG, "draw: ")
    }

    private fun prepareData(format: RenderManager.RenderFormat): RenderData {
        var file = ""
        if (format == RenderManager.RenderFormat.NV12) {
            file = "yuv_test_nv12.yuv"
        } else if (format == RenderManager.RenderFormat.YUV420) {
            file = "yuv_test_i420.yuv"
        }
        if (file.isEmpty()) {
            return RenderData(TEST_YUV_FILE_WIDTH, TEST_YUV_FILE_HEIGHT, null, null, null)
        }

        val buffer = FileUtils.read(file, this)
        var renderData: RenderData? = null
        if (format == RenderManager.RenderFormat.NV12) {
            val yBuffer = ByteBuffer.allocateDirect(TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT)
                .order(ByteOrder.nativeOrder())
            yBuffer.put(buffer, 0, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT)
            yBuffer.position(0)

            val uvBuffer = ByteBuffer.allocateDirect(TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 2)
                .order(ByteOrder.nativeOrder())
            uvBuffer.put(buffer, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 2)
            uvBuffer.position(0)

            renderData = RenderData(TEST_YUV_FILE_WIDTH, TEST_YUV_FILE_HEIGHT, yBuffer, uvBuffer, null)
        } else if (format == RenderManager.RenderFormat.YUV420) {
            val yBuffer = ByteBuffer.allocateDirect(TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT)
                .order(ByteOrder.nativeOrder())
            yBuffer.put(buffer, 0, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT)
            yBuffer.position(0)

            val uBuffer = ByteBuffer.allocateDirect(TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 4)
                .order(ByteOrder.nativeOrder())
            uBuffer.put(buffer, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 4)
            uBuffer.position(0)

            val vBuffer = ByteBuffer.allocateDirect(TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 4)
                .order(ByteOrder.nativeOrder())
            vBuffer.put(buffer, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT * 5 / 4, TEST_YUV_FILE_WIDTH * TEST_YUV_FILE_HEIGHT / 4)
            vBuffer.position(0)

            renderData = RenderData(TEST_YUV_FILE_WIDTH, TEST_YUV_FILE_HEIGHT, yBuffer, uBuffer, vBuffer)
        }

        return renderData!!
    }
}
