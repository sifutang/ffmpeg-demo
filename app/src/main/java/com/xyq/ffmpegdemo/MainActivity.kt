package com.xyq.ffmpegdemo

import android.Manifest
import android.app.ActionBar.LayoutParams
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import com.xyq.ffmpegdemo.databinding.ActivityMainBinding
import com.xyq.ffmpegdemo.player.FFPlayer
import com.xyq.ffmpegdemo.utils.FFMpegUtils
import com.xyq.ffmpegdemo.utils.FileUtils
import java.nio.ByteBuffer
import kotlin.collections.ArrayList
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val VIDEO_THUMBNAIL_SIZE = 5
        private val VIDEO_SOURCE_PATH_ARR = arrayOf("oceans.mp4", "av_sync_test.mp4")
        init {
            System.loadLibrary("ffmpegdemo")
        }
    }

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mPlayer: FFPlayer
    private lateinit var mVideoPath: String
    private var mThumbnailViews = ArrayList<ImageView>()

    private var mHasPermission = false
    private var mIsSeeking = false
    private var mDuration = -1.0
    private var mCurVideoIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initViews()

        mHasPermission = checkPermission()

        mVideoPath = getNextVideoPath()
        mPlayer = FFPlayer(applicationContext, mBinding.glSurfaceView, mBinding.audioVisualizeView)
    }

    override fun onResume() {
        Log.i(TAG, "onResume: ")
        super.onResume()
        if (mHasPermission) {
            fetchVideoThumbnail(mVideoPath)
            startPlay(mVideoPath)
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause: ")
        super.onPause()
        stopPlay()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: ")
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 2000 && resultCode == RESULT_OK) {
            val path = FileUtils.getImageAbsolutePath(this, data?.data)
            path?.let {
                mVideoPath = path
            }
            Log.e(TAG, "onActivityResult: url: ${data?.data}, path: $path")
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun checkPermission(): Boolean {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 1000
            )
            return false
        }
        return true
    }

    private fun initViews() {
        mThumbnailViews.clear()
        for (i in 1..VIDEO_THUMBNAIL_SIZE) {
            val imageView = ImageView(this)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            val layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            imageView.layoutParams = layoutParams
            mBinding.videoThumbnails.addView(imageView)
            mThumbnailViews.add(imageView)
        }


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

        mBinding.btnPlayer.tag = "play_resume"
        mBinding.btnPlayer.setOnClickListener {
            if (it.tag == "play_resume") {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.play_pause, null)
                it.tag = "play_pause"
                mPlayer.pause()
            } else if (it.tag == "play_pause") {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.play_resume, null)
                it.tag = "play_resume"
                mPlayer.resume()
            }
        }

        mBinding.btnAudio.tag = "audio_enable"
        mBinding.btnAudio.setOnClickListener {
            if (it.tag == "audio_enable") {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.audio_disable, null)
                it.tag = "audio_disable"
                mPlayer.setMute(true)
            } else if (it.tag == "audio_disable") {
                it.background = ResourcesCompat.getDrawable(resources, R.drawable.audio_enable, null)
                it.tag = "audio_enable"
                mPlayer.setMute(false)
            }
        }

        mBinding.btnSelectFile.setOnClickListener {
//            val intent = Intent(Intent.ACTION_GET_CONTENT)
//            intent.type = "video/*"
//            intent.addCategory(Intent.CATEGORY_OPENABLE)
//            try {
//                startActivityForResult(Intent.createChooser(intent, "选择播放文件"), 2000)
//            } catch (e: ActivityNotFoundException) {
//                e.printStackTrace()
//            }
            stopPlay()
            mVideoPath = getNextVideoPath()
            fetchVideoThumbnail(mVideoPath)
            startPlay(mVideoPath)
        }

        mBinding.btnGridFilter.tag = false
        mBinding.btnGridFilter.setOnClickListener {
            mPlayer.setFilter(FFPlayer.Filter.GRID, !(it.tag as Boolean))
            it.tag = !(it.tag as Boolean)
        }
    }

    private fun startPlay(path: String) {
        mBinding.seekBar.isEnabled = true
        mBinding.seekBar.progress = 0

        mBinding.btnPlayer.tag = "play_resume"
        mBinding.btnPlayer.background = ResourcesCompat.getDrawable(resources, R.drawable.play_resume, null)

        mBinding.btnAudio.tag = "audio_enable"
        mBinding.btnAudio.background = ResourcesCompat.getDrawable(resources, R.drawable.audio_enable, null)

        thread {
            Log.i(TAG, "startPlay: start")
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

                override fun onComplete() {
                    runOnUiThread {
                        mBinding.seekBar.progress = 100
                        mBinding.btnPlayer.tag = "play_pause"
                        mBinding.btnPlayer.background = ResourcesCompat.getDrawable(resources, R.drawable.play_pause, null)
                    }
                }

            })
            mPlayer.start()
        }
    }

    private fun fetchVideoThumbnail(path: String) {
        thread {
            val start = System.currentTimeMillis()
            FFMpegUtils.getVideoFrames(path, 0, 0, object : FFMpegUtils.VideoFrameArrivedInterface {

                override fun onFetchStart(duration: Double): DoubleArray {
                    val count = mThumbnailViews.size
                    val step = duration / count
                    val ptsArr = DoubleArray(count)
                    for (i in 0 until count) {
                        ptsArr[i] = i * step
                    }
                    ptsArr[0] = 1.0 // 演示视频前几帧都是黑屏，此处我们以第1s为起始
                    Log.e(TAG, "onFetchStart: $duration, ptsArr: ${ptsArr.contentToString()}")
                    return ptsArr
                }

                override fun onProgress(frame: ByteBuffer, timestamps: Double, width: Int, height: Int, index: Int): Boolean {
                    if (path != mVideoPath) {
                        return false
                    }

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.copyPixelsFromBuffer(frame)
                    Log.e(TAG, "onProgress: timestamps: $timestamps, width: $width, height: $height, consume: ${System.currentTimeMillis() - start}")
                    runOnUiThread {
                        mThumbnailViews[index].setImageBitmap(bitmap)
                    }
                    return true
                }

                override fun onFetchEnd() {
                    Log.e(TAG, "onFetchEnd: ")
                }
            })
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

    private fun getNextVideoPath(): String {
        mCurVideoIndex++
        mCurVideoIndex %= VIDEO_SOURCE_PATH_ARR.size
        val path = VIDEO_SOURCE_PATH_ARR[mCurVideoIndex]
        val videoPath = cacheDir.absolutePath + "/$path"
        FileUtils.copyFile2Path(assets.open(path), videoPath)
        return videoPath
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            mPlayer.setFilterProgress(event.x / mBinding.glSurfaceView.width)
        }
        return super.onTouchEvent(event)
    }
}