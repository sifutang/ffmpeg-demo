package com.xyq.ffmpegdemo.model

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class ButtonItemViewModel: ViewModel() {

    val mPlayLiveData = MutableLiveData<ButtonItemModel>()

    val mMuteLiveData = MutableLiveData<ButtonItemModel>()

}