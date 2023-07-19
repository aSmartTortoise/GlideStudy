package com.wyj.glide.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.wyj.glide.net.OnProgressChangeListener
import com.wyj.glide.net.ProgressInterceptor

class ProgressImageViewTarget<T>(private val url: String, imageView: ImageView) :
    ImageViewTarget<T>(imageView) {

    override fun onLoadStarted(placeholder: Drawable?) {
        super.onLoadStarted(placeholder)
        if (placeholder is ProgressPlaceHolderDrawable) {
            ProgressInterceptor.addListener(url, object : OnProgressChangeListener {
                override fun onProgress(progress: Int) {
                    placeholder.setProgress(progress)
                }
            })
        }
    }

    override fun onResourceReady(resource: T, transition: Transition<in T>?) {
        super.onResourceReady(resource, transition)
        ProgressInterceptor.removeListener(url)
    }

    override fun onLoadFailed(errorDrawable: Drawable?) {
        super.onLoadFailed(errorDrawable)
        ProgressInterceptor.removeListener(url)
    }

    override fun onLoadCleared(placeholder: Drawable?) {
        super.onLoadCleared(placeholder)
        ProgressInterceptor.removeListener(url)
    }


    override fun setResource(resource: T?) {
        if (resource is Bitmap) {
            view.setImageBitmap(resource)
        } else if (resource is Drawable) {
            view.setImageDrawable(resource)
        }
    }
}