package com.xyq.ffmpegdemo

import android.Manifest
import android.app.ActionBar.LayoutParams
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import android.view.MotionEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import com.xyq.ffmpegdemo.databinding.ActivityMainBinding
import com.xyq.ffmpegdemo.model.ButtonItemModel
import com.xyq.ffmpegdemo.model.ButtonItemViewModel
import com.xyq.ffmpegdemo.model.VideoThumbnailViewModel
import com.xyq.ffmpegdemo.player.FFPlayer
import com.xyq.ffmpegdemo.player.IMediaPlayer
import com.xyq.ffmpegdemo.player.IMediaPlayerStatusListener
import com.xyq.ffmpegdemo.utils.CommonUtils
import com.xyq.ffmpegdemo.utils.FFMpegUtils
import com.xyq.ffmpegdemo.utils.FileUtils
import com.xyq.ffmpegdemo.utils.TraceUtils
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val VIDEO_THUMBNAIL_SIZE = 5
        init {
            System.loadLibrary("ffmpegdemo")
        }
    }

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mPlayer: IMediaPlayer
    private lateinit var mVideoPath: String

    private var mVideoPathForThumbnail = ""
    private var mThumbnailViews = ArrayList<ImageView>()

    private var mHasPermission = false
    private var mIsSeeking = false
    private var mIsExporting = false
    private var mDuration = -1.0

    private var mExecutors = Executors.newFixedThreadPool(2)

    private lateinit var mVideoThumbnailViewModel: VideoThumbnailViewModel
    private lateinit var mBtnViewModel: ButtonItemViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TraceUtils.beginSection("MainActivity#onCreate")
        Log.i(TAG, "onCreate: ")
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        initViews()
        initViewModels()

        val text = CommonUtils.generateTextBitmap("雪月清的随笔", 16f, applicationContext)
        mBinding.imageView.setImageBitmap(text)

        mHasPermission = checkPermission()

        mPlayer = FFPlayer(applicationContext, mBinding.glSurfaceView)

        // preload video thumbnail
        mVideoPath = getDemoVideoPath()
        fetchVideoThumbnail(mVideoPath)
        TraceUtils.endSection()
    }

    override fun onResume() {
        TraceUtils.beginSection("MainActivity#onResume")
        Log.i(TAG, "onResume: ")
        super.onResume()
        if (mHasPermission) {
            startPlay(mVideoPath)
        }
        TraceUtils.endSection()
    }

    override fun onPause() {
        TraceUtils.beginSection("MainActivity#onPause")
        Log.i(TAG, "onPause: ")
        super.onPause()
        stopPlay()
        TraceUtils.endSection()
    }

    override fun onDestroy() {
        TraceUtils.beginSection("MainActivity#onDestroy")
        Log.i(TAG, "onDestroy: ")
        mIsExporting = false
        super.onDestroy()
        mPlayer.release()
        mExecutors.shutdown()
        TraceUtils.endSection()
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
        Log.i(TAG, "checkPermission: ")
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
        Log.i(TAG, "initViews: ")
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
                    mBinding.tvTimer.text = CommonUtils.getTimeDesc(timestamp.toInt())
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

        mBinding.btnPlayer.setOnClickListener {
            mBtnViewModel.mPlayLiveData.value?.let {
                if (!it.isSelected) {
                    mBtnViewModel.mPlayLiveData.value = ButtonItemModel(R.drawable.play_pause, true)
                    mPlayer.pause()
                } else {
                    mBtnViewModel.mPlayLiveData.value = ButtonItemModel(R.drawable.play_resume, false)
                    if (mPlayer.isPlayComplete()) {
                        mPlayer.seek(0.0)
                    }
                    mPlayer.resume()
                }
            }
        }

        mBinding.btnAudio.setOnClickListener {
            mBtnViewModel.mMuteLiveData.value?.let {
                if (!it.isSelected) {
                    mBtnViewModel.mMuteLiveData.value = ButtonItemModel(R.drawable.audio_disable, true)
                    mPlayer.setMute(true)
                } else {
                    mBtnViewModel.mMuteLiveData.value = ButtonItemModel(R.drawable.audio_enable, false)
                    mPlayer.setMute(false)
                }
            }
        }

        mBinding.btnSelectFile.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "video/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            try {
                startActivityForResult(Intent.createChooser(intent, "选择播放文件"), 2000)
            } catch (e: ActivityNotFoundException) {
                e.printStackTrace()
            }
        }

        mBinding.btnExportGif.setOnClickListener {
            if (mIsExporting) {
                Log.e(TAG, "exportGif doing")
                return@setOnClickListener
            }
            mIsExporting = true

            mExecutors.submit {
                TraceUtils.beginSection("delete pre-gif")
                val gifPath = "/storage/emulated/0/DCIM/Camera/export.gif"
                FileUtils.deleteFile(gifPath)
                TraceUtils.endSection()

                TraceUtils.beginSection("exportGif")
                val start = System.currentTimeMillis()
                Log.i(TAG, "exportGif start: $gifPath")
                FFMpegUtils.exportGif(mVideoPath, gifPath)
                val consume = System.currentTimeMillis() - start
                Log.i(TAG, "exportGif end, consume: ${consume}ms")
                TraceUtils.endSection()

                runOnUiThread {
                    mIsExporting = false
                    Toast.makeText(this, "export gif consume: ${consume}ms", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun initViewModels() {
        Log.i(TAG, "initViewModels: ")
        // video thumbnail
        mVideoThumbnailViewModel = ViewModelProvider(this).get(VideoThumbnailViewModel::class.java)
        mVideoThumbnailViewModel.mLiveData.observe(this) {
            Log.i(TAG, "Receive VideoThumbnailViewModel: $it")
            if (it.isValid() && it.index < mThumbnailViews.size) {
                mThumbnailViews[it.index].setImageBitmap(it.getBitmap())
            }
        }

        mBtnViewModel = ViewModelProvider(this).get(ButtonItemViewModel::class.java)
        // play btn
        mBtnViewModel.mPlayLiveData.observe(this) {
            mBinding.btnPlayer.background = ResourcesCompat.getDrawable(resources, it.backgroundResId, null)
        }

        // audio mute btn
        mBtnViewModel.mMuteLiveData.observe(this) {
            mBinding.btnAudio.background = ResourcesCompat.getDrawable(resources, it.backgroundResId, null)
        }
    }

    private fun startPlay(path: String) {
        fetchVideoThumbnail(path)

        mBinding.seekBar.isEnabled = true
        mBinding.seekBar.progress = 0

        mBtnViewModel.mPlayLiveData.value = ButtonItemModel(R.drawable.play_resume, false)
        mBtnViewModel.mMuteLiveData.value = ButtonItemModel(R.drawable.audio_enable, false)

        mExecutors.submit {
            Log.i(TAG, "startPlay: start")
            mPlayer.prepare(path)
            mDuration = mPlayer.getDuration()
            Log.i(TAG, "startPlay: duration: $mDuration")

            mPlayer.setListener(object : IMediaPlayerStatusListener {

                override fun onProgress(timestamp: Double) {
                    runOnUiThread {
                        // seek bar: [0, 100]
                        if (!mIsSeeking) {
                            mBinding.seekBar.progress = ((timestamp / mDuration) * 100).toInt()
                            mBinding.tvTimer.text = CommonUtils.getTimeDesc(timestamp.toInt())
                        }
                    }
                }

                override fun onComplete() {
                    runOnUiThread {
                        mBinding.seekBar.progress = 100
                        mBtnViewModel.mPlayLiveData.value = ButtonItemModel(R.drawable.play_pause, true)
                    }
                }

                override fun onFftAudioDataArrived(data: FloatArray) {
                    mBinding.audioVisualizeView.setFftAudioData(data)
                }

            })

            mPlayer.start()
        }
    }

    private fun fetchVideoThumbnail(path: String) {
        if (mVideoPathForThumbnail == path) {
            Log.i(TAG, "fetchVideoThumbnail: has fetch")
            return
        }
        mVideoPathForThumbnail = path

        var width = mThumbnailViews[0].width
        width = if (width <= 0) 300 else width

        mExecutors.submit {
            mVideoThumbnailViewModel.loadThumbnail(path, width, 0, mThumbnailViews.size, false) {
                TraceUtils.beginAsyncSection("fetchVideoThumbnail", it.index)
                runOnUiThread {
                    mVideoThumbnailViewModel.mLiveData.value = it
                    TraceUtils.endAsyncSection("fetchVideoThumbnail", it.index)
                }
                return@loadThumbnail true
            }
        }
    }

    private fun stopPlay() {
        mPlayer.stop()
        Log.i(TAG, "stopPlay: done")
    }

    private fun getDemoVideoPath(): String {
        val path = "oceans.mp4"
//        val path = "av_sync_test.mp4"
        val videoPath = cacheDir.absolutePath + "/$path"
        FileUtils.copyFile2Path(assets.open(path), videoPath)
        return videoPath
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            (mPlayer as FFPlayer).setFilterProgress(event.x / mBinding.glSurfaceView.width)
        }
        return super.onTouchEvent(event)
    }
}