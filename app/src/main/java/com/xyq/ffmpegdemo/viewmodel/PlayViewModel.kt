package com.xyq.ffmpegdemo.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xyq.ffmpegdemo.R
import com.xyq.ffmpegdemo.player.MyPlayer

class PlayViewModel(private val mPlayer: MyPlayer): ViewModel() {

    private val mMuteLiveData = MutableLiveData<Int>()
    private val mPlayLiveData = MutableLiveData<Int>()

    private var mMute = false
    private var mPlaying = false

    init {
        updateMuteState(false)
    }

    fun getMuteState() = mMuteLiveData

    fun getPlayState() = mPlayLiveData

    fun updateMuteState(mute: Boolean) {
        mMute = mute
        if (mMute) {
            mMuteLiveData.value = R.drawable.audio_disable
        } else {
            mMuteLiveData.value = R.drawable.audio_enable
        }
        mPlayer.setMute(mute)
    }

    fun isMute(): Boolean {
        return mMute
    }

    fun updatePlayState(start: Boolean) {
        mPlaying = start
        if (start) {
            mPlayLiveData.value = R.drawable.play_resume
            if (mPlayer.isPlayComplete()) {
                mPlayer.seek(0.0)
            }
            mPlayer.resume()
        } else {
            mPlayLiveData.value = R.drawable.play_pause
            mPlayer.pause()
        }
    }

    fun isPlaying(): Boolean {
        return mPlaying
    }

}