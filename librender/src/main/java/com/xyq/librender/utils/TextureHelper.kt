package com.xyq.librender.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log

class TextureHelper {

    companion object {
        private const val TAG = "TextureHelper"
        private const val DEBUG = true

        fun loadTexture(context: Context, resId: Int): Int {
            val textureObjectIds = OpenGLTools.createTextureIds(1)
            if (textureObjectIds[0] == 0) {
                if (DEBUG) {
                    Log.w(TAG, "loadTexture: Could not generate a new OpenGL texture object.")
                }
                return 0
            }

            val options = BitmapFactory.Options()
            options.inScaled = false
            val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
            if (bitmap == null) {
                if (DEBUG) {
                    Log.w(TAG, "loadTexture: Resource Id $resId could not be decoded")
                }

                OpenGLTools.deleteTextureIds(textureObjectIds)
                return 0
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureObjectIds[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)

            bitmap.recycle()
            return textureObjectIds[0]
        }

        fun loadTexture(bitmap: Bitmap): Int {
            val textureObjectIds = OpenGLTools.createTextureIds(1)
            if (textureObjectIds[0] == 0) {
                if (DEBUG) {
                    Log.w(TAG, "loadTexture: Could not generate a new OpenGL texture object.")
                }
                return -1
            }

            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureObjectIds[0])
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, GLES30.GL_NONE)

            return textureObjectIds[0]
        }
    }
}