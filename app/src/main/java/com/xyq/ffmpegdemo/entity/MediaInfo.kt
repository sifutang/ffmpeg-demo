package com.xyq.ffmpegdemo.entity

import org.json.JSONObject
import java.lang.Exception

class MediaInfo(json: String?) {

    var path = ""

    // video
    var hasVideo = false
    var useHw = false
    var videoCodecName = ""
    var dar = ""
    var width = 0
    var height = 0
    var duration = 0.0
    var rotate = 0


    // audio
    var hasAudio = false
    var audioCodecName = ""
    var channel = 0
    var sampleFmt = ""
    var sampleRate = 0


    init {
        json?.let {
            val obj = JSONObject(it)
            try {
                path = obj.getString("path")

                hasVideo = obj.has("video")
                if (hasVideo) {
                    val video = JSONObject(obj.getString("video"))
                    videoCodecName = video.getString("codec_name")
                    useHw = video.getBoolean("use_hw")
                    dar = video.getString("dar")
                    width = video.getInt("width")
                    height = video.getInt("height")
                    duration = video.getDouble("duration")
                    rotate = video.getInt("rotate")
                }

                hasAudio = obj.has("audio")
                if (hasAudio) {
                    val audio = JSONObject(obj.getString("audio"))
                    audioCodecName = audio.getString("codec_name")
                    channel = audio.getInt("channel")
                    sampleFmt = audio.getString("sample_fmt")
                    sampleRate = audio.getInt("sample_rate")
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun toString(): String {
        return "MediaInfo(path='$path', hasVideo=$hasVideo, useHw=$useHw, videoCodecName='$videoCodecName', dar='$dar', width=$width, height=$height, duration=$duration, rotate=$rotate, hasAudio=$hasAudio, audioCodecName='$audioCodecName', channel=$channel, sampleFmt='$sampleFmt', sampleRate=$sampleRate)"
    }
}