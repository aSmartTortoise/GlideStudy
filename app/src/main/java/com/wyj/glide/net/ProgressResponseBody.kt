package com.wyj.glide.net

import android.util.Log
import okhttp3.MediaType
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Okio

class ProgressResponseBody(private val originalResponseBody: ResponseBody, url: String)
    : ResponseBody() {

    private var listener = ProgressInterceptor.getListener(url)
    private val bufferedSource = Okio.buffer(object : ForwardingSource(originalResponseBody.source()) {
        private var totalBytesRead = 0L
        private var currentProgress = 0
        override fun read(sink: Buffer, byteCount: Long): Long {
            return super.read(sink, byteCount).apply {
                if (this == -1L) {
                    totalBytesRead = contentLength()
                } else {
                    totalBytesRead += this
                }
                val progress = (100 * totalBytesRead / contentLength()).toInt()
                Log.d("ProgressResponseBody", "read: progress:$progress")
                if (progress != currentProgress) {
                    currentProgress = progress
                    listener?.onProgress(currentProgress)
                }
                if (totalBytesRead == contentLength()) {
                    listener = null
                }
            }
        }
    })

    override fun contentType() = originalResponseBody.contentType()

    override fun contentLength() = originalResponseBody.contentLength()

    override fun source(): BufferedSource = bufferedSource
}