package com.wyj.glide

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton

class GlideActivity : AppCompatActivity() {
    companion object {
        // 静态图片资源
        private const val URL = "https://cn.bing.com/sa/simg/hpb/LaDigue_EN-CA1115245085_1920x1080.jpg"
//        private const val URL = ""

        // Gif资源
//        private const val GIF_URL = "http://p1.pstatp.com/large/166200019850062839d3"
        private const val GIF_URL = "https://img.zcool.cn/community/01be175c613345a801203d222acdde.gif"
    }

    private var ivGlide: ImageView? = null;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glide)
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            load()
        }

        ivGlide = findViewById<ImageView>(R.id.ivGlide)


    }

    private fun load() {
//        loadStringWithNoCacheWithPlaceholder()
//        loadGif()
        loadAsBitmapFromGIF()
    }

    /**
     *  如果加载的资源是GIF，但是希望展示的效果是静态图片，可以在Glide.with返回的RequestManager对象调用asBitmap方法。
     */
    private fun loadAsBitmapFromGIF() {
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this).asBitmap().load(GIF_URL).apply(options).into(ivGlide!!)
    }

    /**
     *  加载GIF
     */
    private fun loadGif() {
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this).load(GIF_URL).apply(options).into(ivGlide!!)
    }

    /**
     *  通过RequestOptions设置占位图placeholder、error、fallback。并设置不使用内存缓存和磁盘缓存。
     */
    private fun loadStringWithNoCacheWithPlaceholder() {
        val options = RequestOptions()
            .placeholder(ColorDrawable(Color.GRAY))
            .error(ColorDrawable(Color.RED))
            .fallback(ColorDrawable(Color.CYAN))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
        Glide.with(this).load(URL).apply(options).into(ivGlide!!)
    }
}