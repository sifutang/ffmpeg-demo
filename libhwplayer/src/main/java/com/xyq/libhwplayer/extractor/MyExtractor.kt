package com.xyq.libhwplayer.extractor

import android.media.MediaExtractor
import android.media.MediaFormat
import java.nio.ByteBuffer

class MyExtractor(path: String) {

    private var mExtractor: MediaExtractor? = null

    private var mAudioTrack = -1

    private var mVideoTrack = -1

    private var mCurSampleTime: Long = 0

    private var mSampleFlags: Int = MediaExtractor.SAMPLE_FLAG_SYNC

    private var mStartPos: Long = 0

    init {
        mExtractor = MediaExtractor()
        mExtractor?.setDataSource(path)
    }

    fun getVideoFormat(): MediaFormat? {
        for (i in 0 until mExtractor!!.trackCount) {
            val mediaFormat = mExtractor!!.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            if (mime!!.startsWith("video/")) {
                mVideoTrack = i
                break
            }
        }
        return if (mVideoTrack >= 0) mExtractor!!.getTrackFormat(mVideoTrack) else null
    }

    fun getAudioFormat(): MediaFormat? {
        for (i in 0 until mExtractor!!.trackCount) {
            val mediaFormat = mExtractor!!.getTrackFormat(i)
            val mime = mediaFormat.getString(MediaFormat.KEY_MIME)
            if (mime!!.startsWith("audio/")) {
                mAudioTrack = i
                break
            }
        }
        return if (mAudioTrack >= 0) mExtractor!!.getTrackFormat(mAudioTrack) else null
    }

    fun readBuffer(byteBuffer: ByteBuffer): Int {
        byteBuffer.clear()
        selectSourceTrack()
        val readSampleCount = mExtractor!!.readSampleData(byteBuffer, 0)
        if (readSampleCount < 0) {
            return -1
        }
        mCurSampleTime = mExtractor!!.sampleTime
        mSampleFlags = mExtractor!!.sampleFlags
        mExtractor!!.advance()
        return readSampleCount
    }

    private fun selectSourceTrack() {
        if (mVideoTrack >= 0) {
            mExtractor!!.selectTrack(mVideoTrack)
        } else if (mAudioTrack >= 0) {
            mExtractor!!.selectTrack(mAudioTrack)
        }
    }

    fun seek(pos: Long): Long {
        mExtractor!!.seekTo(pos, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        return mExtractor!!.sampleTime
    }

    fun release() {
        mExtractor?.release()
        mExtractor = null
    }

    fun getVideoTrack(): Int {
        return mVideoTrack
    }

    fun getAudioTrack(): Int {
        return mAudioTrack
    }

    fun setStartPos(pos: Long) {
        mStartPos = pos
    }

    fun getCurrentTimestamp(): Long {
        return mCurSampleTime
    }

    fun getSampleFlags(): Int {
        return mSampleFlags
    }
}