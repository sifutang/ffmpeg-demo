package com.xyq.ffmpegdemo

import android.os.*
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.app.AppCompatActivity
import com.xyq.ffmpegdemo.databinding.ActivityMainBinding
import com.xyq.ffmpegdemo.player.FFPlayer
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
    private lateinit var mPlayer: FFPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mPlayer = FFPlayer(applicationContext, binding.glSurfaceView)
        mPlayer.init()
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
        mPlayer.release()
    }

    private fun startPlay() {
        thread {
            Log.i(TAG, "startPlay: start")
            val path = cacheDir.absolutePath + "/oceans.mp4"
            FileUtils.copyFile2Path(assets.open("oceans.mp4"), path)
            mPlayer.prepare(path)
            mPlayer.start()
        }
    }

    private fun stopPlay() {
        mPlayer.stop()
        Log.i(TAG, "stopPlay: done")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            mPlayer.setFilterProgress(event.x / binding.glSurfaceView.width)
        }
        return super.onTouchEvent(event)
    }
}