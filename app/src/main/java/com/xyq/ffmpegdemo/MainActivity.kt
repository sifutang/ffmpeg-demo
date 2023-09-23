package com.xyq.ffmpegdemo

import android.Manifest
import android.app.ActionBar.LayoutParams
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.*
import android.provider.MediaStore
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.BitmapCompat
import androidx.lifecycle.ViewModelProvider
import com.xyq.ffmpegdemo.databinding.ActivityMainBinding
import com.xyq.ffmpegdemo.model.ButtonItemModel
import com.xyq.ffmpegdemo.model.ButtonItemViewModel
import com.xyq.ffmpegdemo.model.VideoThumbnailViewModel
import com.xyq.ffmpegdemo.player.IMediaPlayer
import com.xyq.ffmpegdemo.player.IMediaPlayerStatusListener
import com.xyq.ffmpegdemo.player.MyPlayer
import com.xyq.ffmpegdemo.player.PlayerConfig
import com.xyq.libffplayer.utils.FFMpegUtils
import com.xyq.libmediapicker.MediaPickerActivity
import com.xyq.libmediapicker.PickerConfig
import com.xyq.libmediapicker.entity.Media
import com.xyq.librender.filter.GreyFilter
import com.xyq.libutils.CommonUtils
import com.xyq.libutils.FileUtils
import com.xyq.libutils.TraceUtils
import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val VIDEO_THUMBNAIL_SIZE = 5
    }

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mPlayer: IMediaPlayer
    private lateinit var mMediaFilePath: String
    private var mIsVideo = true

    private var mVideoPathForThumbnail = ""
    private var mThumbnailViews = ArrayList<ImageView>()

    private var mHasPermission = false
    private var mIsSeeking = false
    private var mIsExporting = false
    private var mDuration = -1.0

    private lateinit var mVideoThumbnailViewModel: VideoThumbnailViewModel
    private lateinit var mBtnViewModel: ButtonItemViewModel

    private var mExecutors = Executors.newFixedThreadPool(2)

    private val mLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == PickerConfig.RESULT_CODE) {
                val select = result.data?.getParcelableArrayListExtra<Media>(PickerConfig.EXTRA_RESULT)
                if (select.isNullOrEmpty()) return@registerForActivityResult
                val media = select[0]
                mMediaFilePath = media.path
                mIsVideo = media.isVideo() or media.isGif()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TraceUtils.beginSection("MainActivity#onCreate")
        Log.i(TAG, "onCreate: ")
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        initViews()
        initViewModels()

        mHasPermission = checkPermission()
        mPlayer = MyPlayer(applicationContext, mBinding.glSurfaceView)

        // preload video thumbnail
        mMediaFilePath = getDemoVideoPath()
        fetchVideoThumbnail(mMediaFilePath)

        val text = CommonUtils.generateTextBitmap("雪月清的随笔", 16f, applicationContext)
        mBinding.ivWatermark.setImageBitmap(text)

        TraceUtils.endSection()
    }

    override fun onResume() {
        TraceUtils.beginSection("MainActivity#onResume")
        Log.i(TAG, "onResume: filepath: $mMediaFilePath, isVideo: $mIsVideo")
        super.onResume()
        if (mHasPermission) {
            checkMediaFileValid(mMediaFilePath, mIsVideo)
            startPlay(mMediaFilePath, mIsVideo)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1000 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mHasPermission = true
            Log.i(TAG, "onRequestPermissionsResult:")
        }
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

        mBinding.btnImport.setOnClickListener {
            val intent = Intent(this, MediaPickerActivity::class.java)
            intent.putExtra(PickerConfig.SELECT_MODE, PickerConfig.PICKER_IMAGE_VIDEO)
            intent.putExtra(PickerConfig.MAX_SELECT_COUNT, 1)
            mLauncher.launch(intent)
        }

        mBinding.btnExport.setOnClickListener {
            if (mIsExporting) {
                Log.e(TAG, "exportGif doing")
                return@setOnClickListener
            }
            mIsExporting = true

            mExecutors.submit {
                if (mIsVideo) {
                    exportGif()
                } else {
                    exportImage()
                }
            }
        }
    }

    private fun initViewModels() {
        Log.i(TAG, "initViewModels: ")
        // video thumbnail
        mVideoThumbnailViewModel = ViewModelProvider(this)[VideoThumbnailViewModel::class.java]
        mVideoThumbnailViewModel.mLiveData.observe(this) {
            Log.i(TAG, "Receive VideoThumbnailViewModel: $it")
            if (it.index < mThumbnailViews.size) {
                if (it.isValid()) {
                    mThumbnailViews[it.index].visibility = View.VISIBLE
                    mThumbnailViews[it.index].setImageBitmap(it.getBitmap())
                } else {
                    mThumbnailViews[it.index].visibility = View.INVISIBLE
                }
            }
        }

        mBtnViewModel = ViewModelProvider(this)[ButtonItemViewModel::class.java]
        // play btn
        mBtnViewModel.mPlayLiveData.observe(this) {
            mBinding.btnPlayer.background = ResourcesCompat.getDrawable(resources, it.backgroundResId, null)
        }

        // audio mute btn
        mBtnViewModel.mMuteLiveData.observe(this) {
            mBinding.btnAudio.background = ResourcesCompat.getDrawable(resources, it.backgroundResId, null)
        }
    }

    private fun showOrHideVideoModeView(show: Boolean) {
        val visible = if (show) View.VISIBLE else View.INVISIBLE
        mBinding.audioVisualizeView.visibility = visible
        mBinding.tvTimer.visibility = visible
        mBinding.videoThumbnails.visibility = visible
        mBinding.btnPlayer.visibility = visible
        mBinding.btnAudio.visibility = visible
        mBinding.seekBar.visibility = visible
    }

    private fun startPlay(path: String, isVideo: Boolean) {
        showOrHideVideoModeView(isVideo)
        if (isVideo) {
            fetchVideoThumbnail(path)
        }

        mBinding.seekBar.isEnabled = true
        mBinding.seekBar.progress = 0

        mBtnViewModel.mPlayLiveData.value = ButtonItemModel(R.drawable.play_resume, false)
        mBtnViewModel.mMuteLiveData.value = ButtonItemModel(R.drawable.audio_enable, false)

        mExecutors.submit {
            Log.i(TAG, "startPlay: start")
            val config = PlayerConfig().apply {
                decodeConfig = PlayerConfig.DecodeConfig.USE_FF_HW_DECODER
            }
            mPlayer.prepare(path, config, isVideo)
            val defaultGreyVal = if (isVideo) 0.5f else 0.0f
            (mPlayer as MyPlayer).updateFilterEffect(GreyFilter.VAL_PROGRESS, defaultGreyVal)
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
        val videoPath = cacheDir.absolutePath + "/$path"
        FileUtils.copyFile2Path(assets.open(path), videoPath)
        return videoPath
    }

    private fun checkMediaFileValid(path: String, isVideo: Boolean) {
        if (File(path).exists()) {
            mMediaFilePath = path
            mIsVideo = isVideo
        } else {
            mMediaFilePath = getDemoVideoPath()
            mIsVideo = true
            runOnUiThread {
                Toast.makeText(this, "文件不存在! 使用默认视频", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportGif() {
        TraceUtils.beginSection("delete pre-gif")
        val gifPath = "/storage/emulated/0/DCIM/Camera/export.gif"
        FileUtils.deleteFile(gifPath)
        TraceUtils.endSection()

        TraceUtils.beginSection("exportGif")
        val start = System.currentTimeMillis()
        Log.i(TAG, "exportGif start: $gifPath")
        FFMpegUtils.exportGif(mMediaFilePath, gifPath)
        val consume = System.currentTimeMillis() - start
        Log.i(TAG, "exportGif end, consume: ${consume}ms")
        TraceUtils.endSection()

        runOnUiThread {
            mIsExporting = false
            Toast.makeText(this, "export gif consume: ${consume}ms", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportImage() {
        val start = System.currentTimeMillis()
        (mPlayer as MyPlayer).getCurrentImage { frameBuffer ->
            if (frameBuffer == null) {
                Log.e(TAG, "exportImage: failed")
            } else {
                val displayName = "export_image_${System.currentTimeMillis()}"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    try {
                        val outputStream = contentResolver.openOutputStream(it)
                        frameBuffer.toBitmap().compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                        outputStream?.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            val consume = System.currentTimeMillis() - start
            runOnUiThread {
                mIsExporting = false
                Toast.makeText(this, "export image consume: ${consume}ms", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_MOVE) {
            (mPlayer as MyPlayer).updateFilterEffect(GreyFilter.VAL_PROGRESS, event.x / mBinding.glSurfaceView.width)
        }
        return super.onTouchEvent(event)
    }
}