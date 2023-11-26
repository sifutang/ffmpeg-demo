package com.xyq.ffmpegdemo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.xyq.ffmpegdemo.adapter.ThumbnailAdapter
import com.xyq.ffmpegdemo.databinding.ActivityMainBinding
import com.xyq.ffmpegdemo.entity.Thumbnail
import com.xyq.ffmpegdemo.player.IMediaPlayer
import com.xyq.ffmpegdemo.player.IMediaPlayerStatusListener
import com.xyq.ffmpegdemo.player.MyPlayer
import com.xyq.ffmpegdemo.player.PlayerConfig
import com.xyq.ffmpegdemo.viewmodel.PlayViewModel
import com.xyq.ffmpegdemo.viewmodel.VideoThumbnailViewModel
import com.xyq.libffplayer.utils.FFMpegUtils
import com.xyq.libmediapicker.MediaPickerActivity
import com.xyq.libmediapicker.PickerConfig
import com.xyq.libmediapicker.entity.Media
import com.xyq.librender.filter.GreyFilter
import com.xyq.libutils.CommonUtils
import com.xyq.libutils.FileUtils
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var mBinding: ActivityMainBinding
    private lateinit var mPlayer: IMediaPlayer
    private lateinit var mMediaFilePath: String
    private var mIsVideo = true

    private var mVideoPathForThumbnail = ""
    private var mHasPermission = false
    private var mIsSeeking = false
    private var mIsExporting = false
    private var mDuration = -1.0
    private var mThumbnailAdapter: ThumbnailAdapter? = null

    private lateinit var mVideoThumbnailViewModel: VideoThumbnailViewModel
    private lateinit var mPlayViewModel: PlayViewModel

    private var mExecutors = Executors.newFixedThreadPool(2)

    private val mLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == PickerConfig.RESULT_CODE) {
                Log.i(TAG, "select media file done")
                val select = result.data?.getParcelableArrayListExtra<Media>(PickerConfig.EXTRA_RESULT)
                if (select.isNullOrEmpty()) return@registerForActivityResult
                val media = select[0]
                mMediaFilePath = media.path
                mIsVideo = media.isVideo() or media.isGif()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "onCreate: ")
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        mPlayer = MyPlayer(applicationContext, mBinding.glSurfaceView)

        initViews()
        initViewModels()

        mHasPermission = checkPermission()

        // preload video thumbnail
        mMediaFilePath = getDemoVideoPath()
        fetchVideoThumbnail(mMediaFilePath)

        val text = CommonUtils.generateTextBitmap("雪月清的随笔", 16f, applicationContext)
        mBinding.ivWatermark.setImageBitmap(text)
    }

    override fun onResume() {
        Log.i(TAG, "onResume: filepath: $mMediaFilePath, isVideo: $mIsVideo")
        super.onResume()
        if (mHasPermission) {
            checkMediaFileValid(mMediaFilePath, mIsVideo)
            startPlay(mMediaFilePath, mIsVideo)
        }
    }

    override fun onPause() {
        Log.i(TAG, "onPause: ")
        super.onPause()
        stopPlay()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy: ")
        mIsExporting = false
        super.onDestroy()
        mPlayer.release()
        mExecutors.shutdown()
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

        val layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mBinding.videoThumbnails.layoutManager = layoutManager
        mThumbnailAdapter = ThumbnailAdapter(this, ArrayList())
        mBinding.videoThumbnails.adapter = mThumbnailAdapter

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
            val start = mPlayViewModel.isPlaying()
            mPlayViewModel.updatePlayState(!start)
        }

        mBinding.btnAudio.setOnClickListener {
            val isMute = mPlayViewModel.isMute()
            mPlayViewModel.updateMuteState(!isMute)
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
        mVideoThumbnailViewModel.getData().observe(this) { model ->
            Log.i(TAG, "Receive VideoThumbnailViewModel: $model")
            if (model.isValid()) {
                model.getBitmap()?.let {
                    mThumbnailAdapter?.addData(Thumbnail(it, model.index))
                }
            }
        }

        mPlayViewModel = PlayViewModel(mPlayer as MyPlayer)
        mPlayViewModel.getPlayState().observe(this) {
            mBinding.btnPlayer.background = ResourcesCompat.getDrawable(resources, it, null)
        }
        mPlayViewModel.getMuteState().observe(this) {
            mBinding.btnAudio.background = ResourcesCompat.getDrawable(resources, it, null)
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

        mPlayViewModel.updateMuteState(false)
        mPlayViewModel.updatePlayState(true)

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
                        mPlayViewModel.updatePlayState(false)
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

        val width = CommonUtils.getScreenWidth(this) / 5
        mExecutors.submit {
            mVideoThumbnailViewModel.loadThumbnail(path, width, 0, 5, false) {
                runOnUiThread {
                    mVideoThumbnailViewModel.getData().value = it
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
        if (FileUtils.fileExists(path)) {
            mMediaFilePath = path
            mIsVideo = isVideo
        } else {
            Log.e(TAG, "checkMediaFileValid: $path is not exists")
            mMediaFilePath = getDemoVideoPath()
            mIsVideo = true
            runOnUiThread {
                Toast.makeText(this, "文件不存在! 使用默认视频", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportGif() {
        val start = System.currentTimeMillis()
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val gifPath = "${dir}/Camera/export_${System.currentTimeMillis()}.gif"

        Log.i(TAG, "exportGif start: $gifPath")
        FFMpegUtils.exportGif(mMediaFilePath, gifPath)
        val consume = System.currentTimeMillis() - start
        Log.i(TAG, "exportGif end, consume: ${consume}ms")

        runOnUiThread {
            mIsExporting = false
            Toast.makeText(this, "export gif consume: ${consume}ms", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportImage() {
        val start = System.currentTimeMillis()
        (mPlayer as MyPlayer).getCurrentImage { frameBuffer ->
            if (frameBuffer != null) {
                val displayName = "export_image_${System.currentTimeMillis()}"
                FileUtils.saveBitmapToLocal(contentResolver, frameBuffer.toBitmap(), displayName)
            } else {
                Log.e(TAG, "exportImage: failed")
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