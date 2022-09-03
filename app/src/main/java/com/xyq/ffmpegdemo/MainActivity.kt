package com.xyq.ffmpegdemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.widget.SeekBar
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

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mPlayer: FFPlayer
    private var mHasPermission = false
    private var mIsSeeking = false
    private var mDuration = -1.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initViews()

        mHasPermission = checkPermission()
        mPlayer = FFPlayer(applicationContext, mBinding.glSurfaceView, mBinding.audioVisualizeView)
    }

    override fun onResume() {
        super.onResume()
        if (mHasPermission) {
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
            mHasPermission = true
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

    private fun initViews() {
        mBinding.seekBar.isEnabled = false
        mBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && mDuration > 0) {
                    val timestamp = progress / 100f * mDuration
                    mBinding.tvTimer.text = calculateTime(timestamp.toInt())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                Log.e(TAG, "onStartTrackingTouch: ")
                mIsSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                seekBar?.let {
                    val timestamp = seekBar.progress / 100f * mDuration
                    Log.e(TAG, "onStopTrackingTouch: progress: ${seekBar.progress}, timestamp: $timestamp")
                    mPlayer.seek(timestamp)
                }
                mIsSeeking = false
            }
        })
    }

    private fun startPlay() {
        mBinding.seekBar.isEnabled = true
        mBinding.seekBar.progress = 0
        thread {
            Log.i(TAG, "startPlay: start")
            val testVideo = "oceans.mp4"
//            val testVideo = "av_sync_test.mp4"
            val path = cacheDir.absolutePath + "/$testVideo"
            FileUtils.copyFile2Path(assets.open(testVideo), path)

            mPlayer.prepare(path)
            mDuration = mPlayer.getDuration()
            Log.i(TAG, "startPlay: duration: $mDuration")

            mPlayer.setListener(object : FFPlayer.FFPlayerListener {

                override fun onProgress(timestamp: Double) {
                    runOnUiThread {
                        // seek bar: [0, 100]
                        if (!mIsSeeking) {
                            mBinding.seekBar.progress = ((timestamp / mDuration) * 100).toInt()
                            mBinding.tvTimer.text = calculateTime(timestamp.toInt())
                        }
                    }
                }

            })
            mPlayer.start()
        }
    }

    private fun calculateTime(timeS: Int): String {
        val hour = timeS / 3600
        val minute = timeS / 60
        val second = timeS - hour * 3600 - minute * 60
        return "${alignment(hour)}:${alignment(minute)}:${alignment(second)}"
    }

    private fun alignment(time: Int): String {
        return if (time > 9) "$time" else "0$time"
    }

    private fun stopPlay() {
        mPlayer.stop()
        Log.i(TAG, "stopPlay: done")
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            mPlayer.setFilterProgress(event.x / mBinding.glSurfaceView.width)
        }
        return super.onTouchEvent(event)
    }
}