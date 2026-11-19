package com.netmirror.app.controls

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class IosSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var valueFrom: Float = 0f
        set(v) {
            field = v
            invalidate()
        }

    var valueTo: Float = 100f
        set(v) {
            field = v
            invalidate()
        }

    var value: Float = 50f
        set(v) {
            val clamped = v.coerceIn(valueFrom, valueTo)
            if (field != clamped) {
                field = clamped
                invalidate()
            }
        }

    /**
     * Corner radius in pixels. If null, defaults to full pill shape (height / 2f).
     */
    var cornerRadius: Float? = null
        set(v) {
            field = v
            invalidate()
        }

    var trackColorInactive: Int = Color.parseColor("#3C3C3E")
        set(v) {
            field = v
            invalidate()
        }

    var trackColorActive: Int = Color.WHITE
        set(v) {
            field = v
            invalidate()
        }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bgRect = RectF()
    private val bgPath = Path()

    interface OnSliderTouchListener {
        fun onStartTrackingTouch()
        fun onStopTrackingTouch()
    }

    private var changeListener: ((value: Float, fromUser: Boolean) -> Unit)? = null
    private var touchListener: OnSliderTouchListener? = null

    fun addOnChangeListener(listener: (value: Float, fromUser: Boolean) -> Unit) {
        changeListener = listener
    }

    fun setOnSliderTouchListener(listener: OnSliderTouchListener) {
        touchListener = listener
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        // Default to full pill radius (height / 2f) if cornerRadius is null
        val radius = cornerRadius ?: (height / 3.5f)

        // Create Background Capsule Pill Path
        bgRect.set(0f, 0f, width, height)
        bgPath.reset()
        bgPath.addRoundRect(bgRect, radius, radius, Path.Direction.CW)

        // Draw Inactive Background Track
        trackPaint.color = trackColorInactive
        canvas.drawPath(bgPath, trackPaint)

        // Calculate Fill Width
        val range = (valueTo - valueFrom).let { if (it <= 0f) 1f else it }
        val fraction = ((value - valueFrom) / range).coerceIn(0f, 1f)
        val fillWidth = width * fraction

        // Draw Active Progress Fill Clipped to Background Capsule Pill
        if (fillWidth > 0f) {
            canvas.save()
            canvas.clipPath(bgPath)
            progressPaint.color = trackColorActive
            canvas.drawRect(0f, 0f, fillWidth, height, progressPaint)
            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                touchListener?.onStartTrackingTouch()
                updateValueFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                updateValueFromTouch(event.x)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                updateValueFromTouch(event.x)
                touchListener?.onStopTrackingTouch()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun updateValueFromTouch(x: Float) {
        val width = width.toFloat()
        if (width <= 0f) return
        val fraction = (x / width).coerceIn(0f, 1f)
        val newValue = valueFrom + fraction * (valueTo - valueFrom)
        if (value != newValue) {
            value = newValue
            changeListener?.invoke(newValue, true)
        }
    }
}
