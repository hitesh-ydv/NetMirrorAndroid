package com.netmirror.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.annotation.Keep

@Keep
class FadeArcSpinnerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var startColor: Int = Color.parseColor("#0051FF") // Electric Royal Blue
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    var midColor: Int = Color.parseColor("#E00084") // Neon Magenta / Hot Pink
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    var endColor: Int = Color.parseColor("#FF007F") // Bright Fuchsia / Coral
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    var strokeWidthDp: Float = 6f
        set(value) {
            field = value
            updateBounds()
            updateShader()
            invalidate()
        }

    var sweepAngle: Float = 280f
        set(value) {
            field = value
            updateShader()
            invalidate()
        }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRect = RectF()
    private var cachedWidth = 0
    private var cachedHeight = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultSize = (48f * resources.displayMetrics.density).toInt()
        val width = resolveSize(defaultSize, widthMeasureSpec)
        val height = resolveSize(defaultSize, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedWidth = w
        cachedHeight = h
        updateBounds()
        updateShader()
    }

    private fun updateBounds() {
        if (cachedWidth <= 0 || cachedHeight <= 0) return
        val density = resources.displayMetrics.density
        val strokePx = strokeWidthDp * density
        arcPaint.strokeWidth = strokePx

        val availWidth = (cachedWidth - paddingLeft - paddingRight).toFloat()
        val availHeight = (cachedHeight - paddingTop - paddingBottom).toFloat()
        val size = minOf(availWidth, availHeight).coerceAtLeast(0f)

        val inset = strokePx / 2f + 1f
        val diameter = (size - inset * 2f).coerceAtLeast(0f)
        val radius = diameter / 2f

        val cx = paddingLeft + availWidth / 2f
        val cy = paddingTop + availHeight / 2f

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
    }

    private fun updateShader() {
        if (cachedWidth <= 0 || cachedHeight <= 0 || arcRect.width() <= 0) return
        val cx = arcRect.centerX()
        val cy = arcRect.centerY()

        val r = (sweepAngle / 360f).coerceIn(0.1f, 0.9f)

        val colors = intArrayOf(
            withAlpha(startColor, 0),     // Tail start at 0° (0% alpha)
            withAlpha(startColor, 35),    // Subtle electric blue aura
            withAlpha(startColor, 120),   // Electric royal blue
            withAlpha(midColor, 180),     // Vibrant magenta
            withAlpha(midColor, 230),     // Hot pink
            withAlpha(endColor, 255),     // Bright fuchsia / coral peak
            withAlpha(endColor, 255),     // Head peak at sweepAngle
            withAlpha(endColor, 255),     // Head rounded cap coverage
            withAlpha(endColor, 0),       // Dissolve in empty gap
            withAlpha(startColor, 0)      // Wrap-around tail protection
        )

        val positions = floatArrayOf(
            0.0f,
            0.12f * r,
            0.30f * r,
            0.58f * r,
            0.82f * r,
            r,
            (r + 0.045f).coerceAtMost(0.95f),
            (r + 0.115f).coerceAtMost(0.98f),
            (r + 0.150f).coerceAtMost(0.99f),
            1.0f
        )

        arcPaint.shader = SweepGradient(cx, cy, colors, positions)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (arcRect.width() > 0 && arcRect.height() > 0) {
            canvas.drawArc(arcRect, 0f, sweepAngle, false, arcPaint)
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return (color and 0x00FFFFFF) or (alpha.coerceIn(0, 255) shl 24)
    }
}
