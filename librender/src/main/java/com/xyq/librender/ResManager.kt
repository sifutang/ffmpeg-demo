package com.xyq.librender

import android.content.Context
import com.xyq.libutils.FileUtils

object ResManager {

    object ShaderCache {
        private val mVertexShaderCache = HashMap<Int, String>()
        private val mFragmentShaderCache = HashMap<Int, String>()

        fun findVertexShader(resId: Int, context: Context): String {
            return findShader(resId, context, mVertexShaderCache)
        }

        fun findFragmentShader(resId: Int, context: Context): String {
            return findShader(resId, context, mFragmentShaderCache)
        }

        private fun findShader(resId: Int, context: Context, cache: HashMap<Int, String>): String {
            if (cache.contains(resId)) {
                return cache[resId]!!
            }

            val shader = FileUtils.readTextFileFromResource(context, resId)
            cache[resId] = shader
            return shader
        }
    }







}