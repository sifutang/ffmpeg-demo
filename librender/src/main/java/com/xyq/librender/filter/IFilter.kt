package com.xyq.librender.filter

import com.xyq.librender.model.Pipeline

interface IFilter {

    /**
     * filter process, need run gl thread
     */
    fun process(pipeline: Pipeline): Int

    /**
     * release filter res, need run gl thread
     */
    fun release()

    fun setNext(filter: IFilter)

    fun getNextFilter(): IFilter?

    fun setVal(key: String, value: Float)
}