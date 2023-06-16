package com.wyj.glide

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GlideActivity::class.java))
            }
        }
        findViewById<Button>(R.id.btn_transform).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, GlideTransformActivity::class.java))
            }
        }
    }
}