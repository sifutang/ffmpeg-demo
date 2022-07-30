package com.xyq.ffmpegdemo.render

interface IDrawer {

    fun getTextureSize(): Int

    fun setTextures(ids: IntArray)

    fun draw()

    fun release()

    fun setVideoSize(w: Int, h: Int) {}

    fun setWorldSize(w: Int, h: Int) {}
}