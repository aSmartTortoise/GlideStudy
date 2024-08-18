package com.wyj.glide

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.ImageViewTarget
import com.bumptech.glide.request.target.Target
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.wyj.glide.net.OnProgressChangeListener
import com.wyj.glide.net.ProgressInterceptor
import com.wyj.glide.ui.ListenerImageViewTarget


class GlideLoadProgressActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProgressActivity"
    }

    private var ivGlide1: ImageView? = null
    private var ivGlide2: ImageView? = null

    @Volatile
    private var totalContentLength: Long = 0
    @Volatile
    private var contentGetCount: Int = 0
    @Volatile
    private var currentSize0: Long = 0
    @Volatile
    private var currentSize1: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_glide_load_progress)
        val url1 = "https://cn.bing.com/sa/simg/hpb/LaDigue_EN-CA1115245085_1920x1080.jpg2"
        val url2 =
            "https://th.bing.com/th/id/R.352fa1cd420cb0bc5b8ef6c1caefa890?rik=h6ue2%2bM7oXuhIg&riu=http%3a%2f%2fimg.pconline.com.cn%2fimages%2fupload%2fupc%2ftx%2fphotoblog%2f1106%2f18%2fc5%2f8042691_8042691_1308378158132.jpg&ehk=XLdW7KUhtMs2MykpRPDF5m3VuHebxKmSOlWyU1H3umg%3d&risl=&pid=ImgRaw&r=0"

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            loadImage(
                url1,
               ivGlide1!!)
            loadImage(
                url2,
                ivGlide2!!,)

        }

        ivGlide1 = findViewById<ImageView>(R.id.ivGlide1)
        ivGlide2 = findViewById<ImageView>(R.id.ivGlide2)

        ProgressInterceptor.addListener(url1, object : OnProgressChangeListener {
            override fun onProgress(progress: Int) {
//                Log.d(TAG, "onProgress, progress: $progress")

            }

            override fun onGetContentLength(contentLength: Long) {
                Log.d(TAG, "onGetContentLength, contentLength: $contentLength")
                addContentLength(contentLength)
            }

            override fun onGetCurrentSize(currentSize: Long) {
                super.onGetCurrentSize(currentSize)
                addSize(currentSize, 0)
            }

        })

        ProgressInterceptor.addListener(url2, object : OnProgressChangeListener {
            override fun onProgress(progress: Int) {
//                Log.d(TAG, "onProgress, progress: $progress")
            }

            override fun onGetContentLength(contentLength: Long) {
                Log.d(TAG, "onGetContentLength, contentLength: $contentLength")
                addContentLength(contentLength)
            }

            override fun onGetCurrentSize(currentSize: Long) {
                super.onGetCurrentSize(currentSize)
                addSize(currentSize, 1)
            }

        })

    }


    @Synchronized
    private fun addContentLength(contentLength: Long) {
        totalContentLength += contentLength
        contentGetCount ++
    }

    @Synchronized
    private fun addSize(currentSize: Long, index: Int) {
        Log.d(TAG, "addSize index:$index, currentSize:$currentSize")
        if (index == 0) {
            currentSize0 = currentSize
        } else {
            currentSize1 = currentSize
        }

        if (contentGetCount == 2) {
            val progress = ((currentSize0 + currentSize1) * 100 / totalContentLength).toInt()
            Log.d(TAG, "addSize: progress:$progress")
        }
    }

    private fun loadImage(url: String, imageView: ImageView) {
        Glide.with(this)
            .load(url)
            .apply(RequestOptions().override(Target.SIZE_ORIGINAL))
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .into(
                ListenerImageViewTarget(
                    url,
                    imageView,
                    onLoadStart = {
                        Log.d(TAG, "loadImage: onLoadStart")
                    },
                    onLoadFailed = {
                        Log.d(TAG, "loadImage: onLoadFailed")

                    },
                    onResourceReady = {
                        Log.d(TAG, "loadImage: onResourceReady")
                    }
                ))

    }
}