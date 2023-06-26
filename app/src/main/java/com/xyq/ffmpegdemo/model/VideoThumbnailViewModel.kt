package com.xyq.ffmpegdemo.model

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xyq.ffmpegdemo.utils.FFMpegUtils
import com.xyq.libutils.TraceUtils
import java.nio.ByteBuffer

class VideoThumbnailViewModel: ViewModel() {

    companion object {
        private const val TAG = "VideoThumbnailViewModel"
    }

    val mLiveData = MutableLiveData<VideoThumbnailModel>()

    private var mVideoPath = ""

    /**
     * @param path video path
     * @param width frame width, if <= 0, auto calculate
     * @param height frame height, if <= 0, auto calculate
     * @param size frame count
     * @param precise if false, frame is keyframe
     */
    fun loadThumbnail(path: String,
                      width: Int,
                      height: Int,
                      size: Int,
                      precise: Boolean,
                      onDataReceive: (VideoThumbnailModel) -> Boolean) {
        TraceUtils.beginSection("loadThumbnail")
        val start = System.currentTimeMillis()
        Log.i(TAG, "loadThumbnail: width: $width, height: $height, path: $path")
        mVideoPath = path
        FFMpegUtils.getVideoFrames(path, width, height, precise, object : FFMpegUtils.VideoFrameArrivedInterface {

            override fun onFetchStart(duration: Double): DoubleArray {
                val step = duration / size
                val ptsArr = DoubleArray(size)
                for (i in 0 until size) {
                    ptsArr[i] = i * step
                }
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