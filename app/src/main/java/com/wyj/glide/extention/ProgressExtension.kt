package com.wyj.glide.extention

import android.content.Context
import android.graphics.Color
import com.bumptech.glide.annotation.GlideExtension
import com.bumptech.glide.annotation.GlideOption
import com.bumptech.glide.request.BaseRequestOptions
import com.wyj.glide.ui.ProgressPlaceHolderDrawable

@GlideExtension
object ProgressExtension {

    @GlideOption
    @JvmStatic
    fun progress(requestOptions: BaseRequestOptions<*>, context: Context): BaseRequestOptions<*> {
        val progressPlaceHolderDrawable = ProgressPlaceHolderDrawable(
            context,
            requestOptions.placeholderDrawable,
            requestOptions.placeholderId
        )
        progressPlaceHolderDrawable.setTint(Color.GRAY)
        return requestOptions.placeholder(progressPlaceHolderDrawable)
    }
}