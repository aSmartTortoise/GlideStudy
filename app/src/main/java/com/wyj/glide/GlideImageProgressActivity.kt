package com.wyj.glide

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wyj.glide.glidemodule.GlideApp
import com.wyj.glide.ui.ProgressImageViewTarget

class GlideImageProgressActivity : AppCompatActivity() {
    companion object {
        // 静态图片资源
        private const val URL = "https://cn.bing.com/sa/simg/hpb/LaDigue_EN-CA1115245085_1920x1080.jpg"
//        private const val URL = ""

        // Gif资源
        private const val GIF_URL = "https://img.zcool.cn/community/01be175c613345a801203d222acdde.gif"
    }

    private var ivGlide: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glide_image_progress)
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            load()
        }
        ivGlide = findViewById<ImageView>(R.id.ivGlide)
    }

    private fun load() {
        GlideApp
            .with(this)
            .load(URL)
            .progress(this)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .error(R.color.colorError)
            .into(ProgressImageViewTarget(URL, ivGlide!!))
    }
}