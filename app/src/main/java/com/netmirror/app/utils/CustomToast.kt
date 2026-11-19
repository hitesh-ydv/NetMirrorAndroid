package com.netmirror.app.utils

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.netmirror.app.R
import java.lang.ref.WeakReference

object CustomToast {

    private var currentToastView: WeakReference<View>? = null
    private var hideRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())

    const val LENGTH_SHORT = 2000L
    const val LENGTH_LONG = 3500L
    const val LENGTH_ERROR = 20_000L
    const val LENGTH_INDEFINITE = -1L

    fun show(
        activity: Activity?,
        message: String = "All cookies removed",
        durationMs: Long = LENGTH_SHORT,
        title: String? = null,
        @DrawableRes iconRes: Int? = R.drawable.ic_cookie
    ) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            val prevView = currentToastView?.get()
            if (prevView != null && prevView.isAttachedToWindow) {
                // Cancel previous auto-dismiss task
                hideRunnable?.let { handler.removeCallbacks(it) }
                hideRunnable = null

                val titleView = prevView.findViewById<TextView>(R.id.toastTitle)
                val textView = prevView.findViewById<TextView>(R.id.toastText)
                val iconView = prevView.findViewById<ImageView>(R.id.toastIcon)
                val iconContainer = prevView.findViewById<View>(R.id.toastIconContainer)

                prevView.animate().cancel()
                prevView.alpha = 1f
                prevView.translationY = 0f

                textView.text = message

                if (!title.isNullOrBlank()) {
                    titleView.visibility = View.VISIBLE
                    titleView.text = title
                } else {
                    titleView.visibility = View.GONE
                }

                val isError = iconRes == R.drawable.ic_error || (title != null && title.contains("ERROR", ignoreCase = true))

                if (iconRes != null && iconRes != 0) {
                    iconContainer?.visibility = View.VISIBLE
                    iconView?.visibility = View.VISIBLE
                    iconView?.setImageResource(iconRes)
                    if (isError) {
                        iconContainer?.setBackgroundResource(R.drawable.bg_toast_icon_error)
                        iconView?.clearColorFilter()
                    } else {
                        iconContainer?.setBackgroundResource(R.drawable.bg_toast_icon_default)
                        iconView?.setColorFilter(android.graphics.Color.WHITE)
                    }
                } else {
                    iconContainer?.visibility = View.GONE
                    iconView?.visibility = View.GONE
                }

                // Subtle refresh animation indicating content update
                prevView.scaleX = 0.96f
                prevView.scaleY = 0.96f
                prevView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()

                if (durationMs > 0) {
                    val decorView = prevView.parent as? ViewGroup
                    val dismissTask = Runnable {
                        animateDismiss(prevView, decorView)
                    }
                    hideRunnable = dismissTask
                    handler.postDelayed(dismissTask, durationMs)
                }
                return@runOnUiThread
            }

            // Dismiss any existing toast view immediately if not attached
            dismissCurrent()

            val decorView = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread
            val inflater = LayoutInflater.from(activity)
            val toastView = inflater.inflate(R.layout.layout_custom_toast, decorView, false)

            val titleView = toastView.findViewById<TextView>(R.id.toastTitle)
            val textView = toastView.findViewById<TextView>(R.id.toastText)
            val iconView = toastView.findViewById<ImageView>(R.id.toastIcon)
            val iconContainer = toastView.findViewById<View>(R.id.toastIconContainer)

            textView.text = message

            if (!title.isNullOrBlank()) {
                titleView.visibility = View.VISIBLE
                titleView.text = title
            } else {
                titleView.visibility = View.GONE
            }

            val isError = iconRes == R.drawable.ic_error || (title != null && title.contains("ERROR", ignoreCase = true))

            if (iconRes != null && iconRes != 0) {
                iconContainer?.visibility = View.VISIBLE
                iconView.visibility = View.VISIBLE
                iconView.setImageResource(iconRes)
                if (isError) {
                    iconContainer?.setBackgroundResource(R.drawable.bg_toast_icon_error)
                    iconView.clearColorFilter()
                } else {
                    iconContainer?.setBackgroundResource(R.drawable.bg_toast_icon_default)
                    iconView.setColorFilter(android.graphics.Color.WHITE)
                }
            } else {
                iconContainer?.visibility = View.GONE
                iconView.visibility = View.GONE
            }

            // Guaranteed gap between screen edge and toast when large
            val sideMargin = dpToPx(activity, 20)
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                leftMargin = sideMargin
                rightMargin = sideMargin
                bottomMargin = dpToPx(activity, 64)
            }

            toastView.layoutParams = params
            toastView.alpha = 0f
            toastView.scaleX = 0.9f
            toastView.scaleY = 0.9f
            toastView.translationY = dpToPx(activity, 24).toFloat()

            // Allow user to tap toast to dismiss immediately
            toastView.setOnClickListener {
                dismissCurrent()
            }

            decorView.addView(toastView)
            currentToastView = WeakReference(toastView)

            // Animate in with smooth overshoot feel
            toastView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(OvershootInterpolator(0.85f))
                .start()

            // Schedule dismiss only if durationMs > 0
            if (durationMs > 0) {
                val dismissTask = Runnable {
                    animateDismiss(toastView, decorView)
                }
                hideRunnable = dismissTask
                handler.postDelayed(dismissTask, durationMs)
            }
        }
    }

    /**
     * Shows a specialized Error Toast matching: notyToaster('ERROR Code: 69', 'Token Hash not found.', 'error', 20)
     */
    fun showError(
        activity: Activity?,
        title: String = "ERROR Code: 69",
        message: String = "Something Went Wrong, Download Latest App from netmirror.gg",
        durationMs: Long = LENGTH_ERROR
    ) {
        show(
            activity = activity,
            message = message,
            durationMs = durationMs,
            title = title,
            iconRes = R.drawable.ic_error
        )
    }

    private fun animateDismiss(toastView: View?, parent: ViewGroup?) {
        if (toastView == null || parent == null) return

        toastView.animate()
            .alpha(0f)
            .scaleX(0.92f)
            .scaleY(0.92f)
            .translationY(dpToPx(toastView.context, 16).toFloat())
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                try {
                    parent.removeView(toastView)
                    if (currentToastView?.get() == toastView) {
                        currentToastView = null
                    }
                } catch (e: Exception) {
                    // View might already be removed
                }
            }
            .start()
    }

    fun dismissCurrent() {
        hideRunnable?.let { handler.removeCallbacks(it) }
        hideRunnable = null

        val prevView = currentToastView?.get()
        if (prevView != null) {
            val parent = prevView.parent as? ViewGroup
            parent?.removeView(prevView)
            currentToastView = null
        }
    }

    private fun dpToPx(context: android.content.Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

/**
 * Extension function on Activity to easily display custom toast from anywhere.
 */
fun Activity.showCustomToast(
    message: String = "All cookies removed",
    durationMs: Long = CustomToast.LENGTH_SHORT,
    title: String? = null,
    @DrawableRes iconRes: Int? = R.drawable.ic_cookie
) {
    CustomToast.show(this, message, durationMs, title, iconRes)
}

/**
 * Extension function on Activity to easily display custom error toast matching notyToaster.
 */
fun Activity.showCustomErrorToast(
    title: String = "ERROR Code: 69",
    message: String = "Token Hash not found.",
    durationMs: Long = CustomToast.LENGTH_ERROR
) {
    CustomToast.showError(this, title, message, durationMs)
}
