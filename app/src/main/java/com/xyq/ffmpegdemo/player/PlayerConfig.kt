package com.xyq.ffmpegdemo.player

class PlayerConfig {

    enum class DecodeConfig {
        USE_HW_DECODER,    // media codec
        USE_FF_HW_DECODER, // media codec via ffmpeg
        USE_FF_SW_DECODER  // ffmpeg software decoder
    }

    var decodeConfig = DecodeConfig.USE_FF_HW_DECODER
}