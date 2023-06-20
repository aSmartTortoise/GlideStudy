package com.wyj.glide.glidemodule

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpLibraryGlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.wyj.glide.net.ProgressInterceptor
import okhttp3.OkHttpClient
import java.io.InputStream

@GlideModule
@Excludes(value = [MyGlideModule::class, MyLibraryGlideModule::class, OkHttpLibraryGlideModule::class])
class MyAppGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)
        builder.setDiskCache(ExternalPreferredCacheDiskCacheFactory(context))
    }

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(ProgressInterceptor())
            .build()
        registry.replace(GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient))
    }

}