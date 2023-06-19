package com.wyj.glide.glidemodule

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.Excludes
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.ExternalPreferredCacheDiskCacheFactory
import com.bumptech.glide.module.AppGlideModule

@GlideModule
@Excludes(value = [MyGlideModule::class, MyLibraryGlideModule::class])
class MyAppGlideModule : AppGlideModule() {

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        super.applyOptions(context, builder)
        builder.setDiskCache(ExternalPreferredCacheDiskCacheFactory(context))
    }

}