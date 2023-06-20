package com.wyj.glide

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wyj.glide.glidemodule.GlideApp

class GlideExtensionActivity : AppCompatActivity() {
    companion object {
        // 静态图片资源
        private const val URL = "https://cn.bing.com/sa/simg/hpb/LaDigue_EN-CA1115245085_1920x1080.jpg"
//        private const val URL = ""

        // Gif资源
        private const val GIF_URL = "https://img.zcool.cn/community/01be175c613345a801203d222acdde.gif"
    }

    private var ivGlide: ImageView? = null
    private var ivGlide1: ImageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glide_extension)
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            load()
        }
        ivGlide = findViewById<ImageView>(R.id.ivGlide)
        ivGlide1 = findViewById<ImageView>(R.id.ivGlide1)
    }

    private fun load() {
        GlideApp
            .with(this)
            .load(URL)
            .miniThumb(100)
            .transform(CenterCrop())
            .into(ivGlide!!)
        GlideApp
            .with(this)
            .asGifTest()
            .load(GIF_URL)
            .into(ivGlide1!!)
    }
}