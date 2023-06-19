package com.wyj.glide.glidemodule

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.module.GlideModule


class MyGlideModule : GlideModule {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
    }
}