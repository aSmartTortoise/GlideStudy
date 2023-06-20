package com.wyj.glide

import android.app.ProgressDialog
import android.graphics.drawable.Drawable
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.DrawableImageViewTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wyj.glide.glidemodule.GlideApp
import com.wyj.glide.net.OnProgressChangeListener
import com.wyj.glide.net.ProgressInterceptor

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
        val progressDialog = ProgressDialog(this)
        progressDialog.setProgressStyle(ProgressDialog.BUTTON_POSITIVE)
        progressDialog.setMessage("加载中")
        ProgressInterceptor.addListener(URL, object : OnProgressChangeListener {
            override fun onProgress(progress: Int) {
                progressDialog.progress = progress
            }
        })
        GlideApp
            .with(this)
            .load(URL)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .placeholder(R.color.colorLoading)
            .error(R.color.colorError)
            .into(object : DrawableImageViewTarget(ivGlide) {
                override fun onLoadStarted(placeholder: Drawable?) {
                    super.onLoadStarted(placeholder)
                    progressDialog.show()
                }

                override fun onLoadFailed(errorDrawable: Drawable?) {
                    super.onLoadFailed(errorDrawable)
                    progressDialog.dismiss()
                    ProgressInterceptor.removeListener(URL)
                }

                override fun onResourceReady(
                    resource: Drawable,
                    transition: Transition<in Drawable>?
                ) {
                    super.onResourceReady(resource, transition)
                    progressDialog.dismiss()
                    ProgressInterceptor.removeListener(URL)
                }
            })
    }
}