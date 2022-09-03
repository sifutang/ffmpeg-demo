package com.xyq.ffmpegdemo

import android.Manifest
import android.content.pm.PackageManager
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
    private var hasPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.seekBar.isEnabled = false

        hasPermission = checkPermission()

        mPlayer = FFPlayer(applicationContext, binding.glSurfaceView, binding.audioVisualizeView)
    }

    override fun onResume() {
        super.onResume()
        if (hasPermission) {
            startPlay()
        }
    }

    override fun onPause() {
        super.onPause()
        stopPlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        mPlayer.release()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            Log.i(TAG, "onRequestPermissionsResult:")
        }
    }

    private fun checkPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                ), 1000
            )
            return false
        }
        return true
    }

    private fun startPlay() {
        binding.seekBar.isEnabled = true
        binding.seekBar.progress = 0
        thread {
            Log.i(TAG, "startPlay: start")
//            val testVideo = "oceans.mp4"
            val testVideo = "av_sync_test.mp4"
            val path = cacheDir.absolutePath + "/$testVideo"
            FileUtils.copyFile2Path(assets.open(testVideo), path)

            mPlayer.prepare(path)
            val duration = mPlayer.getDuration()
            Log.i(TAG, "startPlay: duration: $duration")

            mPlayer.setListener(object : FFPlayer.FFPlayerListener {

                override fun onProgress(timestamp: Double) {
                    runOnUiThread {
                        // seek bar: [0, 100]
                        val curTimeS = timestamp / 1000
                        binding.seekBar.progress = ((curTimeS / duration) * 100).toInt()
                    }
                }

            })
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