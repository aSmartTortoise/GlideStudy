package com.wyj.glide

import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.MultiTransformation
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.load.resource.bitmap.Rotate
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.module.AppGlideModule
import com.bumptech.glide.request.RequestOptions
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wyj.glide.transformation.CircleBorderTransform
import com.wyj.glide.transformation.CropCircleWithBorderTransformation

class GlideTransformActivity : AppCompatActivity() {
    companion object {
        // 静态图片资源
//        private const val URL = "https://cn.bing.com/sa/simg/hpb/LaDigue_EN-CA1115245085_1920x1080.jpg"
        private const val URL = "https://wallpapercave.com/wp/wp5853423.jpg"
//        private const val URL = "https://www.baidu.com/img/bd_logo1.png"
//        private const val URL = ""

    }
    private var ivGlide1: ImageView? = null
    private var ivGlide2: ImageView? = null
    private var ivGlide3: ImageView? = null
    private var ivGlide4: ImageView? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glide_transform)
        ivGlide1 = findViewById<ImageView>(R.id.ivGlide1)
        ivGlide2 = findViewById<ImageView>(R.id.ivGlide2)
        ivGlide3 = findViewById<ImageView>(R.id.ivGlide3)
        ivGlide4 = findViewById<ImageView>(R.id.ivGlide4)
        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            load()
        }

    }

    private fun load() {
        Glide.with(this)
            .load(URL)
            .into(ivGlide1!!)

        Glide.with(this)
            .load(URL)
            .transform(FitCenter(), Rotate(180))
            .into(ivGlide2!!)

        Glide.with(this)
            .load(URL)
            .transform(MultiTransformation(FitCenter(), RoundedCorners(303 ushr 1)))
            .into(ivGlide3!!)
//
        val requestOptions = RequestOptions()
            .transform(CircleBorderTransform( 4.0f, Color.parseColor("#3498db")))
        Glide.with(this)
            .load(URL)
            .apply(requestOptions)
            .into(ivGlide4!!)
    }
}