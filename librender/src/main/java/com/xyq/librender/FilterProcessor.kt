package com.xyq.librender

import com.xyq.librender.filter.IFilter
import com.xyq.librender.model.Pipeline

class FilterProcessor {

    private var mFirstFilter: IFilter? = null

    @Synchronized
    fun addFilter(filter: IFilter) {
        if (mFirstFilter == null) {
            mFirstFilter = filter
        } else {
            var curFilter = mFirstFilter
            while (curFilter?.getNextFilter() != null) {
                curFilter = curFilter.getNextFilter()
            }
            curFilter?.setNext(filter)
        }
    }

    /**
     * remove filter from chain, must release it by user
     */
    @Synchronized
    fun removeFilter(filter: IFilter) {
        if (mFirstFilter == null) {
            return
        }
        if (filter == mFirstFilter) {
            mFirstFilter = filter.getNextFilter()
            return
        }

        var curFilter = mFirstFilter
        while (curFilter?.getNextFilter() != null) {
            if (curFilter.getNextFilter() == filter) {
                val nextFilter = curFilter.getNextFilter()?.getNextFilter()
                nextFilter?.let {
                    curFilter?.setNext(it)
                }
            } else {
                curFilter = curFilter.getNextFilter()
            }
        }
    }

    @Synchronized
    fun process(pipeline: Pipeline): Int {
        return if (mFirstFilter == null) {
            -1
        } else {
            val texId = mFirstFilter!!.process(pipeline)
            texId
        }
    }

    @Synchronized
    fun release() {
        var curFilter = mFirstFilter
        curFilter?.release()

        while (curFilter?.getNextFilter() != null) {
            curFilter = curFilter.getNextFilter()
            curFilter?.release()
        }
    }
}