package com.xyq.ffmpegdemo.model

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xyq.ffmpegdemo.utils.FFMpegUtils
import com.xyq.ffmpegdemo.utils.TraceUtils
import java.nio.ByteBuffer

class VideoThumbnailViewModel: ViewModel() {

    companion object {
        private const val TAG = "VideoThumbnailViewModel"
    }

    val mLiveData = MutableLiveData<VideoThumbnailModel>()

    private var mVideoPath = ""

    fun loadThumbnail(path: String,
                      width: Int,
                      height: Int,
                      size: Int,
                      onDataReceive: (VideoThumbnailModel) -> Int) {
        TraceUtils.beginSection("loadThumbnail")
        val start = System.currentTimeMillis()
        Log.i(TAG, "loadThumbnail: width: $width, height: $height, path: $path")
        mVideoPath = path
        FFMpegUtils.getVideoFrames(path, width, height, object : FFMpegUtils.VideoFrameArrivedInterface {

            override fun onFetchStart(duration: Double): DoubleArray {
                val step = duration / size
                val ptsArr = DoubleArray(size)
                for (i in 0 until size) {
                    ptsArr[i] = i * step
                }
                ptsArr[0] = 1.0 // 演示视频前几帧都是黑帧
                Log.i(TAG, "onFetchStart: $duration, ptsArr: ${ptsArr.contentToString()}")
                return ptsArr
            }

            override fun onProgress(frame: ByteBuffer, timestamps: Double, width: Int, height: Int, rotate: Int, index: Int): Boolean {
                if (path != mVideoPath) {
                    return false
                }
                Log.i(TAG, "onProgress: timestamps: $timestamps, width: $width, height: $height, consume: ${System.currentTimeMillis() - start}")
                onDataReceive(VideoThumbnailModel(width, height, rotate, index, frame))
                return true
            }

            override fun onFetchEnd() {
                Log.i(TAG, "onFetchEnd: ")
            }
        })
        TraceUtils.endSection()
    }

}