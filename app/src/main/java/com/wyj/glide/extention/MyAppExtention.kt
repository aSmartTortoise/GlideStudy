package com.wyj.glide.extention

import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.annotation.GlideExtension
import com.bumptech.glide.annotation.GlideOption
import com.bumptech.glide.annotation.GlideType
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.BaseRequestOptions
import com.bumptech.glide.request.RequestOptions

/**
 *  https://muyangmin.github.io/glide-docs-cn/doc/generatedapi.html
 *
 *  GlideExtension注解用来标记扩展Glide api的类。
 *  GlideExtension有两种扩展方式：
 *  (1) GlideOption修饰方法，为RequestOptions添加一个自定义的选项。
 *  (2) GlideType修饰方法，为新的资源类型（Gif, svg等）添加支持。
 */
@GlideExtension
object MyAppExtension {
    private const val MINI_THUMB_SIZE = 100
    private val DECODE_TYPE_GIF = RequestOptions.decodeTypeOf(GifDrawable::class.java).lock()

    @GlideOption
    @JvmStatic
    fun miniThumb(options: BaseRequestOptions<*>, size: Int): BaseRequestOptions<*> {
        return options
            .fitCenter()
            .override(size)
    }

    @GlideType(GifDrawable::class)
    @JvmStatic
    fun asGifTest(requestBuilder: RequestBuilder<GifDrawable>): RequestBuilder<GifDrawable> {
        return requestBuilder
            .transition(DrawableTransitionOptions())
            .apply(DECODE_TYPE_GIF)
    }
}