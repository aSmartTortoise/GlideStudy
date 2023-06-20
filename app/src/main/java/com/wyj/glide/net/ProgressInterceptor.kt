package com.wyj.glide.net

import okhttp3.Interceptor
import okhttp3.Response

class ProgressInterceptor : Interceptor {
    companion object {
        private val LISTENERS = hashMapOf<String, OnProgressChangeListener>()
        fun addListener(url: String, listener: OnProgressChangeListener) {
            LISTENERS[url] = listener
        }
        fun removeListener(url: String) {
            LISTENERS.remove(url)
        }
        fun getListener(url: String): OnProgressChangeListener? {
            return LISTENERS[url]
        }
    }
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        val response = chain.proceed(request)
        val url = request.url().toString()
        val responseBody = response.body() ?: return response
        return response.newBuilder().body(ProgressResponseBody(responseBody, url)).build()
    }
}