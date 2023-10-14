package com.xyq.librender.model

import java.nio.ByteBuffer

data class RenderData(val w: Int, val h: Int, val y: ByteBuffer?, val u: ByteBuffer?, val v: ByteBuffer?, val rotate: Int = 0)
