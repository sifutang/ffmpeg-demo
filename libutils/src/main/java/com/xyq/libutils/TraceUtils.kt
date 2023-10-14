package com.xyq.libutils

import android.os.Build
import android.os.Trace

object TraceUtils {

    private val DEBUG = BuildConfig.DEBUG

    fun beginSection(sectionName: String) {
        if (DEBUG) {
            Trace.beginSection(sectionName)
        }
    }

    fun endSection() {
        if (DEBUG) {
            Trace.endSection()
        }
    }

    fun beginAsyncSection(methodName: String, cookie: Int) {
        if (DEBUG) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Trace.beginAsyncSection(methodName, cookie)
            }
        }
    }

    fun endAsyncSection(methodName: String, cookie: Int) {
        if (DEBUG) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Trace.endAsyncSection(methodName, cookie)
            }
        }
    }

}