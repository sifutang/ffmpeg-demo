package com.xyq.librender.model

import android.util.Size

data class Pipeline(val texId: Int,
                    val canvasSize: Size,
                    val frameSize: Size,
                    val rotate: Int
)