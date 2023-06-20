package com.wyj.glide.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlin.math.min

class ProgressPlaceHolderDrawable(
    private var context: Context,
    private var placeHolderDrawable: Drawable? = null,
    placeHolderId: Int
) : Drawable() {
    private var progress: Int = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val startAngle = 270f
    private val paintStokeWidth = getDensity() * 1.5f
    private val progressPadding = getDensity() * 3.0f

    init {
        if (placeHolderDrawable == null && placeHolderId != 0) {
            placeHolderDrawable = ContextCompat.getDrawable(context, placeHolderId)
        }
        paint.color = Color.GRAY
        paint.strokeWidth = paintStokeWidth
    }

    override fun setBounds(bounds: Rect) {
        super.setBounds(bounds)
        placeHolderDrawable?.bounds = bounds
    }

    override fun setTint(tintColor: Int) {
        super.setTint(tintColor)
        paint.color = tintColor
    }

    fun setProgress(@androidx.annotation.IntRange(from = 0, to = 100) progress: Int) {
        this.progress = progress
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        placeHolderDrawable?.draw(canvas)
        val centerX = (bounds.width() ushr 1).toFloat()
        val centerY = (bounds.height() ushr 1).toFloat()
        var radius = (min(bounds.width(), bounds.height()) ushr 1).toFloat()
        val dp30 = getDensity() * 30
        if (radius > dp30 * 1.25) {
            radius = dp30
        } else {
            radius * 0.85f
        }
        paint.style = Paint.Style.STROKE
        canvas.drawCircle(centerX, centerY, radius, paint)
        paint.style = Paint.Style.FILL
        val endAngle = progress / 100f * 360
        val recF = RectF(
            centerX - radius + progressPadding,
            centerY - radius + progressPadding,
            centerX + radius - progressPadding,
            centerY + radius - progressPadding
        )
        canvas.drawArc(recF, startAngle, endAngle, true, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT

    private fun getDensity() = context.resources.displayMetrics.density
}