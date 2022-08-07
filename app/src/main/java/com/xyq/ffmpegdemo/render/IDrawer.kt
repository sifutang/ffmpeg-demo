package com.xyq.ffmpegdemo.render

interface IDrawer {

    fun init()

    fun draw()

    fun release()

    fun setVideoSize(w: Int, h: Int)

    fun setWorldSize(w: Int, h: Int)

    fun setFilterProgress(value: Float) {}
}