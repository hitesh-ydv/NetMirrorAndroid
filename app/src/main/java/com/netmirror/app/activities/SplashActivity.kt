package com.netmirror.app.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.netmirror.app.databinding.ActivitySplashBinding
import com.netmirror.app.network.AppConfigData
import com.netmirror.app.network.RetrofitClient
import com.netmirror.app.utils.CustomToast
import com.netmirror.app.utils.FullScreenUtil
import com.netmirror.app.utils.InternetUtil
import com.netmirror.app.utils.showCustomErrorToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

class SplashActivity : AppCompatActivity() {

    companion object {
        // Staged animation timing for clean, premium UI/UX hierarchy
        private const val LOGO_DROP_DELAY_MS = 250L
        private const val SPINNER_SHOW_DELAY_MS = 900L
    }

    private lateinit var binding: ActivitySplashBinding
    private var dropBounceAnimator: ValueAnimator? = null
    private var idleAnimator: ValueAnimator? = null
    private var exitAnimator: Animator? = null
    private val runningAnimators = mutableListOf<Animator>()

    private var isDropBounceCompleted = false
    private var pendingConfig: AppConfigData? = null
    private var isRetrying = false
    private var isExiting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FullScreenUtil.enable(this)

        startDropAndBounceAnimation()
        startFadeArcSpinnerAnimation()

        binding.root.setOnClickListener {
            loadUrl()
        }

        loadUrl()
    }

    /**
     * Professional Physics-based Drop & Bounce Animation:
     * - Strictly NO rotation (0 degrees throughout)
     * - Phase 1: Heavy gravity acceleration drop from top of screen (~470ms)
     * - Phase 2: High-energy floor impact with organic horizontal squash & vertical compression (~120ms)
     * - Phase 3: First elastic rebound arc with vertical stretch (~380ms)
     * - Phase 4: Secondary floor contact with micro-squash (~135ms)
     * - Phase 5: Secondary micro-bounce & gentle damped settle upright (~245ms)
     * Total duration: 1350ms
     */
    private fun startDropAndBounceAnimation() {
        dropBounceAnimator?.cancel()

        val density = resources.displayMetrics.density
        val startDropY = -density * 320f

        // Initial setup
        binding.logo.rotation = 0f
        binding.logo.translationY = startDropY
        binding.logo.scaleX = 0.95f
        binding.logo.scaleY = 1.05f
        binding.logo.alpha = 0f

        binding.logoGlow.rotation = 0f
        binding.logoGlow.translationY = 0f
        binding.logoGlow.alpha = 0f
        binding.logoGlow.scaleX = 0.7f
        binding.logoGlow.scaleY = 0.7f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            startDelay = LOGO_DROP_DELAY_MS
            duration = 1350L
            interpolator = LinearInterpolator()

            addUpdateListener { va ->
                val t = va.animatedFraction

                when {
                    // Phase 1: Gravity Drop (0.00 -> 0.35, ~470ms)
                    t < 0.35f -> {
                        val p = t / 0.35f
                        val fall = p * p // Accelerate downwards
                        binding.logo.translationY = startDropY * (1f - fall)
                        binding.logo.alpha = (p * 3.5f).coerceIn(0f, 1f)
                        binding.logo.scaleX = 1.0f - 0.05f * p
                        binding.logo.scaleY = 1.0f + 0.08f * p
                        binding.logo.rotation = 0f

                        binding.logoGlow.alpha = 0.05f + 0.15f * fall
                        binding.logoGlow.scaleX = 0.8f + 0.2f * fall
                        binding.logoGlow.scaleY = 0.8f + 0.2f * fall
                    }

                    // Phase 2: Impact & Squash (0.35 -> 0.44, ~120ms)
                    t < 0.44f -> {
                        val p = (t - 0.35f) / 0.09f
                        val squash = sin(p * PI.toFloat())
                        binding.logo.translationY = 4f * density * squash
                        binding.logo.scaleX = 1.0f + 0.18f * squash
                        binding.logo.scaleY = 1.0f - 0.16f * squash
                        binding.logo.alpha = 1.0f
                        binding.logo.rotation = 0f

                        // Energy flash in ambient glow on impact
                        binding.logoGlow.alpha = 0.20f + 0.30f * squash
                        binding.logoGlow.scaleX = 1.0f + 0.35f * squash
                        binding.logoGlow.scaleY = 1.0f + 0.35f * squash
                    }

                    // Phase 3: First Rebound Arc & Stretch (0.44 -> 0.72, ~380ms)
                    t < 0.72f -> {
                        val p = (t - 0.44f) / 0.28f
                        val arc = sin(p * PI.toFloat())
                        val apexHeight = -65f * density
                        binding.logo.translationY = apexHeight * arc
                        binding.logo.scaleX = 1.0f - 0.04f * arc
                        binding.logo.scaleY = 1.0f + 0.07f * arc
                        binding.logo.alpha = 1.0f
                        binding.logo.rotation = 0f

                        binding.logoGlow.alpha = 0.20f - 0.08f * arc
                        binding.logoGlow.scaleX = 1.0f - 0.12f * arc
                        binding.logoGlow.scaleY = 1.0f - 0.12f * arc
                    }

                    // Phase 4: Impact 2 & Micro-squash (0.72 -> 0.82, ~135ms)
                    t < 0.82f -> {
                        val p = (t - 0.72f) / 0.10f
                        val microSquash = sin(p * PI.toFloat())
                        binding.logo.translationY = 0f
                        binding.logo.scaleX = 1.0f + 0.07f * microSquash
                        binding.logo.scaleY = 1.0f - 0.06f * microSquash
                        binding.logo.alpha = 1.0f
                        binding.logo.rotation = 0f

                        binding.logoGlow.alpha = 0.20f + 0.12f * microSquash
                        binding.logoGlow.scaleX = 1.0f + 0.10f * microSquash
                        binding.logoGlow.scaleY = 1.0f + 0.10f * microSquash
                    }

                    // Phase 5: Secondary Micro-bounce & Settle (0.82 -> 1.00, ~245ms)
                    else -> {
                        val p = (t - 0.82f) / 0.18f
                        val microArc = sin(p * PI.toFloat())
                        val microApex = -14f * density
                        binding.logo.translationY = microApex * microArc
                        binding.logo.scaleX = 1.0f
                        binding.logo.scaleY = 1.0f
                        binding.logo.alpha = 1.0f
                        binding.logo.rotation = 0f

                        binding.logoGlow.alpha = 0.20f
                        binding.logoGlow.scaleX = 1.0f
                        binding.logoGlow.scaleY = 1.0f
                    }
                }
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isExiting || isFinishing || isDestroyed) return

                    binding.logo.translationY = 0f
                    binding.logo.scaleX = 1.0f
                    binding.logo.scaleY = 1.0f
                    binding.logo.rotation = 0f
                    binding.logo.alpha = 1.0f

                    isDropBounceCompleted = true

                    // If API already finished loading, zoom in immediately!
                    val config = pendingConfig
                    if (config != null) {
                        playZoomInTransitionAndLaunch(config)
                    } else {
                        startIdleBreathingAnimation()
                    }
                }
            })
        }

        dropBounceAnimator = animator
        runningAnimators.add(animator)
        animator.start()
    }

    /**
     * Idle Breathing Animation:
     * - Keeps the logo alive while awaiting network response
     * - Strictly NO rotation
     * - Elegant, subtle floating and breathing pulse
     */
    private fun startIdleBreathingAnimation() {
        idleAnimator?.cancel()
        if (isExiting || isFinishing || isDestroyed) return

        val density = resources.displayMetrics.density

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1800L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART

            addUpdateListener { va ->
                val t = va.animatedFraction
                val wave = sin(t * 2f * PI.toFloat())

                binding.logo.rotation = 0f
                binding.logo.scaleX = 1.0f + 0.03f * wave
                binding.logo.scaleY = 1.0f + 0.03f * wave
                binding.logo.translationY = -4f * density * ((wave + 1f) / 2f)

                binding.logoGlow.alpha = 0.20f + 0.08f * wave
                binding.logoGlow.scaleX = 1.0f + 0.05f * wave
                binding.logoGlow.scaleY = 1.0f + 0.05f * wave
            }
        }

        idleAnimator = animator
        runningAnimators.add(animator)
        animator.start()
    }

    /**
     * Continuous multi-color fade arc spinner rotation:
     * - Staged appearance: Fades and scales in smoothly after the logo establishes entrance
     * - Smooth 360° linear rotation
     * - Matches new logo color scheme (Electric Blue -> Magenta -> Fuchsia)
     */
    private fun startFadeArcSpinnerAnimation() {
        binding.fadeArcSpinner.alpha = 0f
        binding.fadeArcSpinner.scaleX = 0.65f
        binding.fadeArcSpinner.scaleY = 0.65f
        binding.fadeArcSpinner.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setStartDelay(SPINNER_SHOW_DELAY_MS)
            .setDuration(450)
            .setInterpolator(DecelerateInterpolator())
            .start()

        val spinnerAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            startDelay = SPINNER_SHOW_DELAY_MS
            duration = 850L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            interpolator = LinearInterpolator()

            addUpdateListener { va ->
                binding.fadeArcSpinner.rotation = va.animatedValue as Float
            }
        }

        runningAnimators.add(spinnerAnimator)
        spinnerAnimator.start()
    }

    private fun loadUrl() {
        if (!InternetUtil.isConnected(this)) {
            val intent = Intent(this, ErrorActivity::class.java).apply {
                putExtra("error_message", "No internet connection")
            }
            startActivity(intent)
            finish()
            return
        }

        if (isRetrying) return
        isRetrying = true

        lifecycleScope.launch {
            try {
                val config = RetrofitClient.resolveWorkingConfig()

                if (isFinishing || isDestroyed) return@launch

                Log.d("API_RESPONSE", "Resolved URL: ${config.webviewUrl}")

                if (isDropBounceCompleted) {
                    // Drop & bounce animation has fully finished, so zoom in now!
                    playZoomInTransitionAndLaunch(config)
                } else {
                    // API finished early! Wait for the drop & bounce animation to finish completely
                    pendingConfig = config
                }
            } catch (e: Exception) {
                if (!isDropBounceCompleted) {
                    delay(1350L + LOGO_DROP_DELAY_MS)
                }
                isRetrying = false
                showCustomErrorToast("ERROR Code: 69", "Something Went Wrong, Download Latest App from netmirror.gg", CustomToast.LENGTH_ERROR)
            }
        }
    }

    /**
     * Professional Two-Stage Exit Transition:
     * - Immediately dissolves the spinner
     * - Stage 1: Zoom in full logo until it covers the full screen (100% visible and solid)
     * - Stage 2: THEN fade out cleanly into WebViewActivity
     */
    private fun playZoomInTransitionAndLaunch(config: AppConfigData) {
        if (isExiting || isFinishing || isDestroyed) return
        isExiting = true

        // Stop looping and idle animators
        idleAnimator?.cancel()
        idleAnimator = null
        dropBounceAnimator?.cancel()
        dropBounceAnimator = null
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()

        // Dissolve spinner quickly
        binding.fadeArcSpinner.animate().cancel()
        binding.fadeArcSpinner.animate()
            .alpha(0f)
            .scaleX(0.3f)
            .scaleY(0.3f)
            .setDuration(150)
            .start()

        // Reset any translation/rotation offsets from idle breathing and center pivots
        binding.logo.translationY = 0f
        binding.logo.rotation = 0f
        binding.logo.alpha = 1.0f

        if (binding.logo.width > 0) {
            binding.logo.pivotX = binding.logo.width / 2f
            binding.logo.pivotY = binding.logo.height / 2f
            binding.logoGlow.pivotX = binding.logoGlow.width / 2f
            binding.logoGlow.pivotY = binding.logoGlow.height / 2f
        }

        val metrics = resources.displayMetrics
        val logoW = if (binding.logo.width > 0) binding.logo.width.toFloat() else 140f * metrics.density
        val logoH = if (binding.logo.height > 0) binding.logo.height.toFloat() else 140f * metrics.density
        val screenW = metrics.widthPixels.toFloat()
        val screenH = metrics.heightPixels.toFloat()

        // Dynamically compute the scale required to completely cover the entire screen
        val fullScreenScale = maxOf(screenW / logoW, screenH / logoH) * 1.30f
        val targetScale = maxOf(fullScreenScale, 8.5f)

        val startScaleX = binding.logo.scaleX.coerceIn(0.9f, 1.2f)
        val startGlowAlpha = binding.logoGlow.alpha.coerceIn(0.15f, 0.4f)

        // Stage 1: Zoom in full logo (completely solid & fully visible)
        val zoomInAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            interpolator = AccelerateInterpolator(1.4f)

            addUpdateListener { va ->
                val progress = va.animatedFraction
                val logoScale = startScaleX + (targetScale - startScaleX) * progress
                binding.logo.scaleX = logoScale
                binding.logo.scaleY = logoScale
                binding.logo.alpha = 1.0f // 100% visible throughout zoom in!

                val glowScale = 1.0f + (targetScale - 1.0f) * progress
                binding.logoGlow.scaleX = glowScale
                binding.logoGlow.scaleY = glowScale
                binding.logoGlow.alpha = (startGlowAlpha * (1.0f + 0.3f * progress)).coerceIn(0f, 0.5f)
            }
        }

        // Stage 2: THEN fade out cleanly
        val fadeOutAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 220L
            interpolator = DecelerateInterpolator()

            addUpdateListener { va ->
                val progress = va.animatedFraction
                // Gentle expansion continuation as it dissolves
                val expandScale = targetScale * (1.0f + 0.12f * progress)
                binding.logo.scaleX = expandScale
                binding.logo.scaleY = expandScale

                // Fade out logo and glow completely
                binding.logo.alpha = (1.0f - progress).coerceIn(0f, 1f)
                binding.logoGlow.alpha = (startGlowAlpha * 1.3f * (1.0f - progress)).coerceIn(0f, 1f)
            }

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (isFinishing || isDestroyed) return

                    val intent = Intent(this@SplashActivity, WebViewActivity::class.java).apply {
                        putExtra("url", config.webviewUrl)
                        config.sTape?.let { putExtra("sTape", it) }
                        putStringArrayListExtra("stapeUrls", ArrayList(config.stapeUrls))
                        config.serverVersion?.let { putExtra("serverVersion", it) }
                        config.updateUrl?.let { putExtra("updateUrl", it) }
                        config.updateImageUrl?.let { putExtra("updateImageUrl", it) }
                        putExtra("updateImageHeightRatio", config.updateImageHeightRatio)
                        putExtra("updateImageWidthRatio", config.updateImageWidthRatio)
                        putExtra("updateImageMaxHeightDp", config.updateImageMaxHeightDp)
                        putExtra("showLaterButton", config.showLaterButton)
                    }

                    startActivity(intent)
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            })
        }

        val exitSet = AnimatorSet().apply {
            playSequentially(zoomInAnimator, fadeOutAnimator)
        }

        exitAnimator = exitSet
        runningAnimators.add(exitSet)
        exitSet.start()
    }

    override fun onDestroy() {
        binding.fadeArcSpinner.animate().cancel()
        dropBounceAnimator?.cancel()
        dropBounceAnimator = null
        idleAnimator?.cancel()
        idleAnimator = null
        exitAnimator?.cancel()
        exitAnimator = null
        runningAnimators.forEach { it.cancel() }
        runningAnimators.clear()
        super.onDestroy()
    }
}