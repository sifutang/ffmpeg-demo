package com.xyq.ffmpegdemo

import android.opengl.GLSurfaceView
import android.os.*
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.xyq.ffmpegdemo.databinding.ActivityMainBinding
import com.xyq.ffmpegdemo.player.FFPlayer
import com.xyq.ffmpegdemo.render.SimpleRender
import com.xyq.ffmpegdemo.render.YuvDrawer
import com.xyq.ffmpegdemo.utils.FileUtils
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        // Used to load the 'ffmpegdemo' library on application startup.
        init {
            System.loadLibrary("ffmpegdemo")
        }
    }

    private lateinit var binding: ActivityMainBinding

    private val mRender = SimpleRender()
    private val mVideoDrawer = YuvDrawer()
    private var mPlayer: FFPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.glSurfaceView.setEGLContextClientVersion(2)
        binding.glSurfaceView.setRenderer(mRender)
        binding.glSurfaceView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY

        mRender.addDrawer(mVideoDrawer)
    }

    override fun onResume() {
        super.onResume()
        startPlay()
    }

    override fun onPause() {
        super.onPause()
        stopPlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        mRender.release()
    }


    private fun startPlay() {
        thread {
            Log.i(TAG, "startPlay: start")
            val path = cacheDir.absolutePath + "/oceans.mp4"
            FileUtils.copyFile2Path(assets.open("oceans.mp4"), path)
            mPlayer = FFPlayer()
            mPlayer?.prepare(path, mVideoDrawer, binding.glSurfaceView)
            mPlayer?.start()
        }
    }

    private fun stopPlay() {
        mPlayer?.stop()
        mPlayer?.release()
        Log.i(TAG, "stopPlay: done")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            mVideoDrawer.setFilterProgress(event.x / binding.glSurfaceView.width)
        }
        return super.onTouchEvent(event)
    }
}