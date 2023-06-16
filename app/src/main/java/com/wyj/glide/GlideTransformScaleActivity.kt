package com.wyj.glide

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.bumptech.glide.request.target.Target as GlideTarget

/**
 *  演示 Glide对图片原数据进行缩放、转换。
 */
class GlideTransformScaleActivity : AppCompatActivity() {
    companion object {
        // 静态图片资源
//        private const val URL = "https://cn.bing.com/sa/simg/hpb/LaDigue_EN-CA1115245085_1920x1080.jpg"
//        private const val URL = "https://wallpapercave.com/wp/wp5853423.jpg"
        private const val URL = "https://www.baidu.com/img/bd_logo1.png"
//        private const val URL = ""

        // Gif资源
        private const val GIF_URL = "https://img.zcool.cn/community/01be175c613345a801203d222acdde.gif"
    }
    private var ivGlide: ImageView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glide_transform)
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            load()
        }
        ivGlide = findViewById<ImageView>(R.id.ivGlide)
    }

    private fun load() {
        Glide.with(this)
            .load(URL)
            .dontTransform()
            .override(GlideTarget.SIZE_ORIGINAL, GlideTarget.SIZE_ORIGINAL)
            .into(ivGlide!!)
    }
}