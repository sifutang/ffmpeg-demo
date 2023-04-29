package com.xyq.ffmpegdemo.render.model

import java.nio.ByteBuffer

data class RenderData(val w: Int, val h: Int, val y: ByteBuffer?, val u: ByteBuffer?, val v: ByteBuffer?)
