package com.wyj.glide.net

interface OnProgressChangeListener {
    fun onProgress(progress: Int)
    fun onGetContentLength(contentLength: Long) {

    }

    fun onGetCurrentSize(currentSize: Long) {}
}