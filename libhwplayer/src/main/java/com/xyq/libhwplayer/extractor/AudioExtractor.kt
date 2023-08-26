package com.xyq.libhwplayer.extractor

import android.media.MediaFormat

class AudioExtractor(path: String): VideoExtractor(path) {

    override fun getFormat(): MediaFormat? {
        return mMediaExtractor.getAudioFormat()
    }

}