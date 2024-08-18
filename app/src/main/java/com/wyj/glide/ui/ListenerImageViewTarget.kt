package com.wyj.glide.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.widget.ImageView
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.wyj.glide.net.OnProgressChangeListener
import com.wyj.glide.net.ProgressInterceptor

class ListenerImageViewTarget<T : Any>(
    private val url: String,
    imageView: ImageView,
    val onLoadStart: (() -> Unit)?,
    val onLoadFailed: (() -> Unit)?,
    val onResourceReady: (() -> Unit)?
) :
    ImageViewTarget<T>(imageView) {

    override fun onLoadStarted(placeholder: Drawable?) {
        super.onLoadStarted(placeholder)
        onLoadStart?.invoke()
    }

    override fun onResourceReady(resource: T, transition: Transition<in T>?) {
        super.onResourceReady(resource, transition)
        onResourceReady?.invoke()
        ProgressInterceptor.removeListener(url)
    }

    override fun onLoadFailed(errorDrawable: Drawable?) {
        super.onLoadFailed(errorDrawable)
        onLoadFailed?.invoke()
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