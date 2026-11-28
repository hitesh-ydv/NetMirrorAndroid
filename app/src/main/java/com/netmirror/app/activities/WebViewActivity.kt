package com.netmirror.app.activities

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import android.util.Base64
import android.util.Log
import androidx.lifecycle.lifecycleScope
import com.netmirror.app.R
import com.netmirror.app.controls.IosSliderView
import com.netmirror.app.databinding.ActivityWebviewBinding
import com.netmirror.app.network.APP_VERSION
import com.netmirror.app.network.RetrofitClient
import com.netmirror.app.utils.CustomToast
import com.netmirror.app.utils.FullScreenUtil
import com.netmirror.app.utils.showCustomErrorToast
import com.netmirror.app.utils.showCustomToast
import android.view.ViewGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit


class WebViewActivity : AppCompatActivity() {

    private var isPopupVisible = false
    private var isPortraitMode = true
    private var isLocked = false

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null

    private lateinit var binding: ActivityWebviewBinding
    private lateinit var audioManager: AudioManager

    private val prefs by lazy {
        getSharedPreferences("quick_controls", Context.MODE_PRIVATE)
    }

    private val handler = Handler(Looper.getMainLooper())

    private val hideFabRunnable = Runnable {
        hideFabsWithAnimation()
    }

    companion object {
        private const val KEY_AUTO_SAVE = "auto_save"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_VOLUME = "volume"
    }

    private var sTape: String? = null
    private var stapeUrls: List<String> = emptyList()
    private var downloadJob: Job? = null
    private var spinnerAnimator: ValueAnimator? = null
    private var isHidingLoader = false
    private var hasCheckedUpdate = false
    private var updateApkUrl: String? = null
    private var updateImageUrl: String? = null
    private var updateImageHeightRatio: Double = 0.0
    private var updateImageWidthRatio: Double = 1.0
    private var updateImageMaxHeightDp: Int = 0
    private var showLaterButton: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWebviewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            hasCheckedUpdate = savedInstanceState.getBoolean("state_has_checked_update", false)
        }

        sTape = intent.getStringExtra("sTape")
        stapeUrls = intent.getStringArrayListExtra("stapeUrls") ?: emptyList()
        updateApkUrl = RetrofitClient.decodeBase64Url(
            intent.getStringExtra("updateUrl") ?: RetrofitClient.currentConfig()?.updateUrl
        )
        updateImageUrl = RetrofitClient.decodeBase64Url(
            intent.getStringExtra("updateImageUrl") ?: RetrofitClient.currentConfig()?.updateImageUrl
        )
        updateImageHeightRatio = intent.getDoubleExtra(
            "updateImageHeightRatio",
            RetrofitClient.currentConfig()?.updateImageHeightRatio ?: 0.0
        )
        updateImageWidthRatio = intent.getDoubleExtra(
            "updateImageWidthRatio",
            RetrofitClient.currentConfig()?.updateImageWidthRatio ?: 1.0
        )
        updateImageMaxHeightDp = intent.getIntExtra(
            "updateImageMaxHeightDp",
            RetrofitClient.currentConfig()?.updateImageMaxHeightDp ?: 0
        )
        showLaterButton = intent.getBooleanExtra(
            "showLaterButton",
            RetrofitClient.currentConfig()?.showLaterButton ?: true
        )

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC

        FullScreenUtil.enable(this)
        startLoaderAnimation()

        isPortraitMode = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

        updatePopupWidth()
        updateControlStates()
        setupSliders()
        setupClickListeners()
        setupWebView()
        setupBackNavigation()

        val restored = savedInstanceState?.let { binding.webView.restoreState(it) } != null
        if (!restored) {
            val url = intent.getStringExtra("url") ?: ""
            if (url.isNotEmpty()) {
                val headers = hashMapOf("User-Agent" to binding.webView.settings.userAgentString)
                binding.webView.loadUrl(url, headers)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.webView.saveState(outState)
        outState.putBoolean("state_has_checked_update", hasCheckedUpdate)
    }

    private fun startLoaderAnimation() {
        if (spinnerAnimator == null) {
            spinnerAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 850L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { va ->
                    binding.loaderSpinner.rotation = va.animatedValue as Float
                }
            }
        }
        if (spinnerAnimator?.isStarted != true) {
            spinnerAnimator?.start()
        }
    }

    private fun stopLoaderAnimation() {
        spinnerAnimator?.cancel()
    }

    private fun showLoader() {
        isHidingLoader = false
        binding.loadingOverlay.animate().cancel()
        binding.loadingOverlay.alpha = 1f
        binding.loadingOverlay.visibility = View.VISIBLE
        startLoaderAnimation()
    }

    private fun hideLoader() {
        if (binding.loadingOverlay.visibility != View.VISIBLE || isHidingLoader) return
        isHidingLoader = true
        binding.loadingOverlay.animate().cancel()
        binding.loadingOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                binding.loadingOverlay.visibility = View.GONE
                binding.loadingOverlay.alpha = 1f
                isHidingLoader = false
                stopLoaderAnimation()
            }
            .start()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                    rotatePortrait()
                    return
                }

                if (isPopupVisible) {
                    hideSettingsPopup()
                    return
                }

                if (isLocked) {
                    showFloatingButton()
                    return
                }

                if (!isPortraitMode) {
                    rotatePortrait()
                }

                binding.webView.evaluateJavascript(
                    "javascript:if(typeof ${'$'} !== 'undefined' && ${'$'}('#jw').hasClass('jwplayer')){ ${'$'}('.btn-payer-back').click(); } else if(typeof ${'$'} !== 'undefined' && ${'$'}('#exampleModalScrollable').hasClass('show')){ ${'$'}('.btn-close2').click(); } else if(typeof pressfromAPP === 'function'){ pressfromAPP(); } else { history.back(); }",
                    null
                )
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val userAgent = binding.webView.settings.userAgentString
        binding.webView.apply {
            setBackgroundColor(Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                defaultFocusHighlightEnabled = false
            }

            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                loadsImagesAutomatically = true
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "$userAgent /OS.Gatu v4.0"
            }

            addJavascriptInterface(AndroidVideoEvents(this@WebViewActivity), "AndroidVideoEvents")
            addJavascriptInterface(ReactNativeWebViewInterface(this@WebViewActivity), "ReactNativeWebView")

            webViewClient = object : WebViewClient() {

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString() ?: return false
                    if (targetUrl.startsWith("https://user")) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                            startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        return true
                    }
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    showLoader()
                    view?.evaluateJavascript(
                        """
                        (function() {
                            try {
                                var css = '* { -webkit-tap-highlight-color: transparent !important; -webkit-tap-highlight-color: rgba(0,0,0,0) !important; outline: none !important; } *:focus { outline: none !important; } a, button, input, div, span, select, textarea { -webkit-tap-highlight-color: transparent !important; } html, body { background-color: #000000 !important; }';
                                var style = document.getElementById('no-tap-highlight');
                                if (!style) {
                                    style = document.createElement('style');
                                    style.id = 'no-tap-highlight';
                                    style.type = 'text/css';
                                    style.appendChild(document.createTextNode(css));
                                    (document.head || document.documentElement).appendChild(style);
                                }
                            } catch(e) {}
                        })();
                        """.trimIndent(),
                        null
                    )
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    hideLoader()
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    CookieManager.getInstance().flush()
                    hideLoader()
                    checkForAppUpdate()
                    view?.evaluateJavascript(
                        """
                        (function() {
                            try {
                                var css = '* { -webkit-tap-highlight-color: transparent !important; -webkit-tap-highlight-color: rgba(0,0,0,0) !important; outline: none !important; } *:focus { outline: none !important; } a, button, input, div, span, select, textarea { -webkit-tap-highlight-color: transparent !important; }';
                                var style = document.getElementById('no-tap-highlight');
                                if (!style) {
                                    style = document.createElement('style');
                                    style.id = 'no-tap-highlight';
                                    style.type = 'text/css';
                                    style.appendChild(document.createTextNode(css));
                                    (document.head || document.documentElement).appendChild(style);
                                }

                                if (typeof autoRotate === 'function') {
                                    autoRotate();
                                } else {
                                    var videos = document.getElementsByTagName('video');
                                    for (var i = 0; i < videos.length; i++) {
                                        var v = videos[i];
                                        if (!v.dataset.hasMirrorListeners) {
                                            v.dataset.hasMirrorListeners = 'true';
                                            v.addEventListener('play', function() {
                                                if (window.AndroidVideoEvents) { window.AndroidVideoEvents.onVideoPlay(); }
                                            });
                                            v.addEventListener('pause', function() {
                                                if (window.AndroidVideoEvents) { window.AndroidVideoEvents.onVideoPause(); }
                                            });
                                            v.addEventListener('ended', function() {
                                                if (window.AndroidVideoEvents) { window.AndroidVideoEvents.onVideoEnd(); }
                                            });
                                        }
                                    }
                                }
                            } catch(e) {}
                        })();
                        """.trimIndent(),
                        null
                    )
                }


                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    hideLoader()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress >= 95) {
                        hideLoader()
                    }
                }

                override fun getDefaultVideoPoster(): Bitmap {
                    return Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
                }

                override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                    hideLoader()
                    if (customView != null) {
                        callback?.onCustomViewHidden()
                        return
                    }
                    customView = view
                    customViewCallback = callback
                    binding.fullScreenContainer.addView(view)
                    binding.fullScreenContainer.visibility = View.VISIBLE
                    binding.webView.visibility = View.GONE
                    rotateLandscape()
                }

                override fun onHideCustomView() {
                    hideCustomView()
                }
            }
        }
    }

    private fun hideCustomView() {
        if (customView == null) return
        binding.fullScreenContainer.removeView(customView)
        binding.fullScreenContainer.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }

    private fun setupClickListeners() {
        binding.reloadBtn.setOnClickListener {
            showLoader()
            binding.webView.reload()
            hideSettingsPopup()
        }

        val onRemoveCookiesClick = View.OnClickListener {
            hideSettingsPopup()
            showRemoveCookiesConfirmationDialog()
        }
        binding.removeCookiesBtn.setOnClickListener(onRemoveCookiesClick)
        binding.removeCookiesText.setOnClickListener(onRemoveCookiesClick)

        binding.touchBlocker.setOnTouchListener { _, _ -> true }

        binding.unlockFab.setOnClickListener {
            isLocked = false
            binding.touchBlocker.visibility = View.GONE
            binding.unlockFab.visibility = View.GONE
            updateControlStates()
            showFloatingButton()
        }

        binding.lockBtn.setOnClickListener {
            isLocked = !isLocked
            if (isLocked) {
                lockScreen()
                hideSettingsPopup()
            } else {
                binding.touchBlocker.visibility = View.GONE
            }
            updateControlStates()
        }

        binding.dimView.setOnClickListener {
            hideSettingsPopup()
        }

        binding.settingsPopup.setOnClickListener {
            // Consume clicks inside popup card so dimView is not triggered
        }

        binding.orientationBtn.setOnClickListener {
            if (isPortraitMode) {
                rotateLandscape()
                isPortraitMode = false
            } else {
                rotatePortrait()
                isPortraitMode = true
            }
            updateControlStates()
        }

        binding.settingsFab.setOnClickListener {
            handler.removeCallbacks(hideFabRunnable)
            showSettingsPopup()
        }
    }

    private fun updatePopupWidth() {
        val params = binding.settingsPopup.layoutParams as ConstraintLayout.LayoutParams
        params.matchConstraintPercentWidth =
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 0.5f else 0.9f
        binding.settingsPopup.layoutParams = params
    }

    private fun applyBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = (value / 255f).coerceIn(0.01f, 1.0f)
        window.attributes = lp
    }

    private fun setupSliders() {
        binding.brightnessSlider.valueFrom = 0f
        binding.brightnessSlider.valueTo = 255f

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        binding.volumeSlider.valueFrom = 0f
        binding.volumeSlider.valueTo = maxVolume.toFloat()

        val autoSave = prefs.getBoolean(KEY_AUTO_SAVE, false)
        binding.autoSaveSwitch.isChecked = autoSave

        if (autoSave) {
            binding.brightnessSlider.value = prefs.getFloat(KEY_BRIGHTNESS, 125f).coerceIn(0f, 255f)
            binding.volumeSlider.value = prefs.getFloat(KEY_VOLUME, maxVolume / 2f).coerceIn(0f, maxVolume.toFloat())
        } else {
            val currentBrightness = try {
                Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            } catch (e: Exception) {
                125
            }
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            binding.brightnessSlider.value = currentBrightness.toFloat().coerceIn(0f, 255f)
            binding.volumeSlider.value = currentVolume.toFloat().coerceIn(0f, maxVolume.toFloat())
        }

        applyBrightness(binding.brightnessSlider.value)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            binding.volumeSlider.value.toInt(),
            0
        )

        binding.autoSaveSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(KEY_AUTO_SAVE, checked).apply()
            if (checked) {
                saveSliderPreferences()
            }
        }

        binding.brightnessSlider.addOnChangeListener { value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            hideDimView(immediate = true)
            applyBrightness(value)
        }

        binding.brightnessSlider.setOnSliderTouchListener(object : IosSliderView.OnSliderTouchListener {
            override fun onStartTrackingTouch() {
                hideDimView(immediate = true)
            }

            override fun onStopTrackingTouch() {
                if (binding.autoSaveSwitch.isChecked) {
                    saveSliderPreferences()
                }
            }
        })

        binding.volumeSlider.addOnChangeListener { value, fromUser ->
            if (!fromUser) return@addOnChangeListener
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                value.toInt(),
                AudioManager.FLAG_SHOW_UI
            )
        }

        binding.volumeSlider.setOnSliderTouchListener(object : IosSliderView.OnSliderTouchListener {
            override fun onStartTrackingTouch() {
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    binding.volumeSlider.value.toInt(),
                    AudioManager.FLAG_SHOW_UI
                )
            }

            override fun onStopTrackingTouch() {
                if (binding.autoSaveSwitch.isChecked) {
                    saveSliderPreferences()
                }
            }
        })
    }

    private fun saveSliderPreferences() {
        prefs.edit()
            .putFloat(KEY_BRIGHTNESS, binding.brightnessSlider.value)
            .putFloat(KEY_VOLUME, binding.volumeSlider.value)
            .apply()
    }

    fun rotateLandscape() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    fun rotatePortrait() {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isPortraitMode = newConfig.orientation == Configuration.ORIENTATION_PORTRAIT
        updatePopupWidth()
        updateControlStates()
        FullScreenUtil.reApply(this)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            FullScreenUtil.reApply(this)
        }
    }

    override fun onPause() {
        CookieManager.getInstance().flush()
        binding.webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
        FullScreenUtil.reApply(this)
    }

    override fun onDestroy() {
        stopLoaderAnimation()
        downloadJob?.cancel()
        CustomToast.dismissCurrent()
        handler.removeCallbacksAndMessages(null)
        if (customView != null) {
            hideCustomView()
        }
        CookieManager.getInstance().flush()
        binding.webView.apply {
            stopLoading()
            removeJavascriptInterface("AndroidVideoEvents")
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        super.onDestroy()
    }

    private fun showSettingsPopup() {
        if (isPopupVisible) return
        isPopupVisible = true

        handler.removeCallbacks(hideFabRunnable)
        hideFabsImmediately()
        showDimView()

        binding.settingsPopup.apply {
            animate().cancel()
            visibility = View.VISIBLE
            translationY = 0f
            scaleX = 0.85f
            scaleY = 0.85f
            alpha = 0f

            animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(1.1f))
                .start()
        }
    }

    private fun hideSettingsPopup() {
        if (!isPopupVisible) return
        isPopupVisible = false

        hideDimView()

        binding.settingsPopup.animate().cancel()
        binding.settingsPopup.animate()
            .alpha(0f)
            .scaleX(0.85f)
            .scaleY(0.85f)
            .translationY(60f)
            .setDuration(180)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction {
                binding.settingsPopup.visibility = View.GONE
            }
            .start()
    }

    private fun hideDimView(immediate: Boolean = false) {
        if (binding.dimView.visibility == View.GONE) return
        binding.dimView.animate().cancel()
        if (immediate) {
            binding.dimView.alpha = 0f
            binding.dimView.visibility = View.GONE
        } else {
            binding.dimView.animate()
                .alpha(0f)
                .setDuration(180)
                .withEndAction {
                    binding.dimView.visibility = View.GONE
                }
                .start()
        }
    }

    private fun showDimView() {
        if (binding.dimView.visibility == View.VISIBLE && binding.dimView.alpha >= 0.99f) return
        binding.dimView.animate().cancel()
        binding.dimView.alpha = 0f
        binding.dimView.visibility = View.VISIBLE
        binding.dimView.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    private fun updateControlStates() {
        if (isPortraitMode) {
            binding.orientationBtn.setBackgroundResource(R.drawable.bg_control_inactive)
            binding.orientationBtn.setColorFilter(Color.WHITE)
        } else {
            binding.orientationBtn.setBackgroundResource(R.drawable.bg_control_active)
            binding.orientationBtn.setColorFilter(Color.BLACK)
        }

        if (isLocked) {
            binding.lockBtn.setBackgroundResource(R.drawable.bg_control_active)
            binding.lockBtn.setColorFilter(Color.BLACK)
        } else {
            binding.lockBtn.setBackgroundResource(R.drawable.bg_control_inactive)
            binding.lockBtn.setColorFilter(Color.WHITE)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (isPopupVisible) {
            if (ev.action == MotionEvent.ACTION_DOWN) {
                val x = ev.rawX
                val y = ev.rawY
                if (!isTouchInside(binding.settingsPopup, x, y)) {
                    hideSettingsPopup()
                    return true
                }
            }
            return super.dispatchTouchEvent(ev)
        }

        if (ev.action == MotionEvent.ACTION_DOWN) {
            val x = ev.rawX
            val y = ev.rawY

            if (isTouchInside(binding.settingsFab, x, y) ||
                isTouchInside(binding.unlockFab, x, y)
            ) {
                return super.dispatchTouchEvent(ev)
            }

            showFloatingButton()
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun isTouchInside(view: View, x: Float, y: Float): Boolean {
        if (view.visibility != View.VISIBLE) return false

        val location = IntArray(2)
        view.getLocationOnScreen(location)

        return x >= location[0] &&
                x <= location[0] + view.width &&
                y >= location[1] &&
                y <= location[1] + view.height
    }

    private fun showFloatingButton() {
        handler.removeCallbacks(hideFabRunnable)

        val button = if (isLocked) binding.unlockFab else binding.settingsFab
        val otherButton = if (isLocked) binding.settingsFab else binding.unlockFab

        otherButton.animate().cancel()
        otherButton.visibility = View.GONE

        if (button.visibility == View.VISIBLE && button.alpha > 0.5f) {
            handler.postDelayed(hideFabRunnable, 3000)
            return
        }

        button.animate().cancel()
        button.apply {
            visibility = View.VISIBLE
            translationX = 120f
            alpha = 0f
            animate()
                .translationX(0f)
                .alpha(1f)
                .setDuration(250)
                .setInterpolator(OvershootInterpolator(1.0f))
                .start()
        }

        handler.postDelayed(hideFabRunnable, 3000)
    }

    private fun hideFabsImmediately() {
        binding.settingsFab.animate().cancel()
        binding.unlockFab.animate().cancel()
        binding.settingsFab.visibility = View.GONE
        binding.unlockFab.visibility = View.GONE
    }

    private fun hideFabsWithAnimation() {
        binding.settingsFab.animate().cancel()
        binding.settingsFab.animate()
            .translationX(120f)
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                binding.settingsFab.visibility = View.GONE
            }
            .start()

        binding.unlockFab.animate().cancel()
        binding.unlockFab.animate()
            .translationX(120f)
            .alpha(0f)
            .setDuration(250)
            .withEndAction {
                binding.unlockFab.visibility = View.GONE
            }
            .start()
    }

    fun lockScreen() {
        isLocked = true
        hideFabsImmediately()
        binding.touchBlocker.visibility = View.VISIBLE
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (customView != null) {
            hideCustomView()
            rotatePortrait()
            return
        }

        if (isPopupVisible) {
            hideSettingsPopup()
            return
        }

        if (isLocked) {
            showFloatingButton()
            return
        }

        if (!isPortraitMode) {
            rotatePortrait()
        }

        binding.webView.evaluateJavascript(
            "javascript:if(typeof ${'$'} !== 'undefined' && ${'$'}('#jw').hasClass('jwplayer')){ ${'$'}('.btn-payer-back').click(); } else if(typeof ${'$'} !== 'undefined' && ${'$'}('#exampleModalScrollable').hasClass('show')){ ${'$'}('.btn-close2').click(); } else if(typeof pressfromAPP === 'function'){ pressfromAPP(); } else { history.back(); }",
            null
        )
    }

    fun handleWebViewMessage(data: String?) {
        val message = data?.trim() ?: return
        Log.d("WebViewActivity", "WebView message: $message")

        runOnUiThread {
            when {
                message.startsWith("downloadStart") -> {
                    val vid = message.replace("downloadStart=", "").replace("downloadStart", "").trim()
                    Log.d("WebViewActivity", "📥 Download button clicked in WebView with vid: $vid")
                    fetchSourceAndPost(vid)
                }
            }
        }
    }

    private fun fetchSourceAndPost(vid: String) {

        val decodedUrls = stapeUrls.mapNotNull {
            try {
                String(Base64.decode(it, Base64.DEFAULT)).trim()
            } catch (e: Exception) {
                null
            }
        }

        downloadJob?.cancel()
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            val minToastDuration = 2500L
            var lastToastTime = 0L

            suspend fun displayToast(
                message: String,
                isIndefinite: Boolean = true
            ) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastToastTime
                if (lastToastTime > 0L && elapsed < minToastDuration) {
                    delay(minToastDuration - elapsed)
                }
                withContext(Dispatchers.Main) {
                    showCustomToast(
                        message = message,
                        durationMs = if (isIndefinite) CustomToast.LENGTH_INDEFINITE else CustomToast.LENGTH_LONG,
                        iconRes = R.drawable.ic_download
                    )
                }
                lastToastTime = System.currentTimeMillis()
            }

            suspend fun displayError(title: String, message: String) {
                val now = System.currentTimeMillis()
                val elapsed = now - lastToastTime
                if (lastToastTime > 0L && elapsed < minToastDuration) {
                    delay(minToastDuration - elapsed)
                }
                withContext(Dispatchers.Main) {
                    showCustomErrorToast(title, message, CustomToast.LENGTH_ERROR)
                }
                lastToastTime = System.currentTimeMillis()
            }

            // 1. Initial Toast: Download in Progress
            displayToast("Download in Progress\nIt may take more than 30 sec.", isIndefinite = true)

            var success = false

            val downloadClient = OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .writeTimeout(8, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .build()

            val customUA = withContext(Dispatchers.Main) { binding.webView.settings.userAgentString }

            for (url in decodedUrls) {
                try {
                    val cleanUrl = if (url.endsWith("/")) url else "$url/"
                    val cleanVid = if (vid.startsWith("/")) vid.removePrefix("/") else vid
                    val fullUrl = "$cleanUrl$cleanVid"

                    Log.d("DownloadSource", "Fetching source from: $fullUrl")


                    val getRequest = Request.Builder()
                        .url(fullUrl)
                        .addHeader("X-Requested-With", "")
                        .addHeader("User-Agent", customUA)
                        .addHeader("Cache-Control", "no-cache")
                        .addHeader("Pragma", "no-cache")
                        .addHeader("Expires", "0")
                        .build()

                    val getResponse = downloadClient.newCall(getRequest).execute()
                    val htmlData = getResponse.body?.string() ?: ""

                    // 2. Fetch Source Toast
                    displayToast("Checking Video Process\nPlease wait...", isIndefinite = true)

                    val domain = try {
                        val domainMatch = Regex("^https?://([^/?#]+)").find(url)
                        domainMatch?.groupValues?.get(1) ?: Uri.parse(url).host
                    } catch (e: Exception) {
                        null
                    }

                    Log.d("DownloadSource", "Stream Domain: $domain")

                    if (!sTape.isNullOrBlank()) {
                        // 3. Post Response Toast
                        displayToast("Preparing Download Link\nPlease wait...", isIndefinite = true)

                        val formBody = FormBody.Builder()
                            .add("stdata", htmlData)
                            .add("streamDomain", domain ?: "")
                            .build()

                        val postRequest = Request.Builder()
                            .url(sTape!!)
                            .addHeader("Content-Type", "application/x-www-form-urlencoded")
                            .post(formBody)
                            .build()

                        try {
                            val postResponse = downloadClient.newCall(postRequest).execute()
                            val postResponseBody = postResponse.body?.string() ?: ""
                            Log.d("DownloadSource", "✅ POST Response: $postResponseBody")

                            val json = JSONObject(postResponseBody)
                            val status = json.optString("status")

                            if (status == "ok") {
                                success = true
                                val dlUrl = json.optString("dlUrl")
                                Log.d("DownloadSource", "✅ Download URL: $dlUrl")

                                // 4. Download Starting Toast
                                displayToast("Download Starting\nDownload Start in your Browser.", isIndefinite = false)

                                // Give the user at least 2.5s to read the toast before opening the browser
                                delay(minToastDuration)

                                withContext(Dispatchers.Main) {
                                    try {
                                        if (!dlUrl.isNullOrBlank()) {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(dlUrl))
                                            startActivity(browserIntent)
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                break // Exit loop if successful
                            } else if(status == "error") {
                                val errorMsg = json.optString("errorMsg", "Download failed")
                                displayError("Download Failed", errorMsg)
                                success = true
                            }else{
                                displayError("ERROR Code: 1", "Something Went Wrong!")
                                success = true
                            }
                        } catch (error: Exception) {
                            displayError("ERROR Code: 2", "Something Wrong With API url")
                            success = true
                        }
                    }
                } catch (error: Exception) {
                    // Continue to next URL
                }
            }

            if (!success) {
                displayError("ERROR Code: 3", "Something Wrong With Download Link")
            }
        }
    }

    private fun checkForAppUpdate() {
        if (hasCheckedUpdate || isFinishing || isDestroyed) return
        hasCheckedUpdate = true

        val serverVersion = (intent.getStringExtra("serverVersion")
            ?: RetrofitClient.currentConfig()?.serverVersion)?.trim()

        if (!serverVersion.isNullOrBlank() && serverVersion != APP_VERSION.trim() && !updateApkUrl.isNullOrBlank()) {
            binding.root.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    showUpdateDialog(serverVersion)
                }
            }, 300)
        }
    }

    private fun showUpdateDialog(serverVersion: String) {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_app_update)
        dialog.setCanceledOnTouchOutside(showLaterButton)
        dialog.setCancelable(showLaterButton)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setDimAmount(0.85f)
            window.setWindowAnimations(R.style.DialogAnimation)
            // Dialog width = screen width * API ratio (e.g. 0.5 = 50%). Falls back to 88% if not given.
            val dialogWidthRatio = normalizeRatio(updateImageWidthRatio, 0.88f).coerceIn(0.3f, 1.0f)
            val width = (resources.displayMetrics.widthPixels * dialogWidthRatio).toInt()
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val updateImageCard = dialog.findViewById<View>(R.id.updateImageCard)
        val updateImageView = dialog.findViewById<com.google.android.material.imageview.ShapeableImageView>(R.id.updateImage)
        val progressSection = dialog.findViewById<View>(R.id.progressSection)
        val btnCancel = dialog.findViewById<TextView>(R.id.btnCancel)
        val btnUpdate = dialog.findViewById<TextView>(R.id.btnUpdate)
        val tvBottomMessage = dialog.findViewById<TextView>(R.id.tvBottomMessage)

        // Hide progress section — no in-app download anymore
        progressSection?.visibility = View.GONE
        tvBottomMessage?.visibility = View.GONE

        // Show/hide Later button based on API flag
        if (showLaterButton) {
            btnCancel?.visibility = View.VISIBLE
        } else {
            btnCancel?.visibility = View.GONE
            // Remove start margin from Update button when Later is hidden
            val updateParams = btnUpdate?.layoutParams as? android.widget.LinearLayout.LayoutParams
            updateParams?.marginStart = 0
            btnUpdate?.layoutParams = updateParams
        }

        // Load update image from API
        if (!updateImageUrl.isNullOrBlank()) {

            // Image height = screen height * API ratio (e.g. 0.5 = 50%).
            // Set BEFORE the image loads so the dialog size is final and does not jump.
            val heightRatio = normalizeRatio(updateImageHeightRatio, 0f)
            val screenHeight = resources.displayMetrics.heightPixels

            updateImageCard?.visibility = View.VISIBLE
            updateImageView?.apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                if (heightRatio > 0f) {
                    adjustViewBounds = false
                    layoutParams = layoutParams.apply {
                        width = ViewGroup.LayoutParams.MATCH_PARENT
                        height = (screenHeight * heightRatio.coerceAtMost(0.8f)).toInt()
                    }
                } else {
                    // No height ratio from API -> wrap image, optional dp cap
                    adjustViewBounds = true
                    maxHeight = if (updateImageMaxHeightDp > 0) dpToPx(updateImageMaxHeightDp) else Int.MAX_VALUE
                }
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val imgClient = OkHttpClient.Builder()
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(20, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                    val imgRequest = Request.Builder()
                        .url(updateImageUrl!!)
                        .build()

                    imgClient.newCall(imgRequest).execute().use { response ->
                        if (response.isSuccessful) {
                            val bitmap = response.body?.byteStream()?.let { android.graphics.BitmapFactory.decodeStream(it) }
                            if (bitmap != null) {
                                withContext(Dispatchers.Main) {
                                    if (!isFinishing && !isDestroyed && dialog.isShowing) {
                                        updateImageView?.setImageBitmap(bitmap)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppUpdate", "Failed to load update image: $e")
                }
            }
        }

        btnCancel?.setOnClickListener {
            dialog.dismiss()
        }

        // Update button: open the update URL in the browser
        btnUpdate?.setOnClickListener {
            if (!updateApkUrl.isNullOrBlank()) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateApkUrl))
                    startActivity(browserIntent)
                } catch (e: Exception) {
                    Log.e("AppUpdate", "Failed to open browser: $e", e)
                    showCustomErrorToast("Update Error", "Could not open browser", CustomToast.LENGTH_SHORT)
                }
            }
        }

        dialog.setOnDismissListener {
            FullScreenUtil.reApply(this)
        }

        FullScreenUtil.showImmersive(dialog)
    }

    private fun showRemoveCookiesConfirmationDialog() {
        val dialog = android.app.Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_confirm_clear_data)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.setDimAmount(0.85f)
            window.setWindowAnimations(R.style.DialogAnimation)
            val width = (resources.displayMetrics.widthPixels * 0.88).toInt().coerceAtMost(dpToPx(380))
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        dialog.findViewById<TextView>(R.id.dialogTitle)?.text = "Remove Cookies?"
        dialog.findViewById<TextView>(R.id.dialogMessage)?.text =
            "Are you sure you want to remove cookies from this site? It will remove your Continue watching, Watchlist and playback data."
        dialog.findViewById<TextView>(R.id.btnConfirm)?.text = "Remove"

        dialog.findViewById<View>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.findViewById<View>(R.id.btnConfirm).setOnClickListener {
            dialog.dismiss()
            removeSiteCookies()
        }

        dialog.setOnDismissListener {
            FullScreenUtil.reApply(this)
        }

        FullScreenUtil.showImmersive(dialog)
    }

    private fun removeSiteCookies() {
        val currentUrl = binding.webView.url ?: intent.getStringExtra("url") ?: ""
        val cookieManager = CookieManager.getInstance()

        // 1. Target cookies for the current site URL & domain
        if (currentUrl.isNotEmpty()) {
            val uri = Uri.parse(currentUrl)
            val host = uri.host ?: ""

            val cookieString = cookieManager.getCookie(currentUrl)
            if (!cookieString.isNullOrBlank()) {
                val cookies = cookieString.split(";")
                for (cookie in cookies) {
                    val name = cookie.substringBefore("=").trim()
                    if (name.isNotEmpty()) {
                        cookieManager.setCookie(currentUrl, "$name=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/")
                        if (host.isNotEmpty()) {
                            cookieManager.setCookie(currentUrl, "$name=; Domain=$host; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                            cookieManager.setCookie(currentUrl, "$name=; Domain=.$host; Path=/; Expires=Thu, 01 Jan 1970 00:00:00 GMT")
                        }
                    }
                }
            }

            // 2. Also clear in-page JavaScript document.cookie for this site
            binding.webView.evaluateJavascript(
                """
                (function() {
                    try {
                        var cookies = document.cookie.split(";");
                        for (var i = 0; i < cookies.length; i++) {
                            var cookie = cookies[i];
                            var eqPos = cookie.indexOf("=");
                            var name = eqPos > -1 ? cookie.substr(0, eqPos).trim() : cookie.trim();
                            if (name) {
                                document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/";
                                document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=" + window.location.hostname;
                                var hostParts = window.location.hostname.split('.');
                                while (hostParts.length > 1) {
                                    document.cookie = name + "=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=." + hostParts.join('.');
                                    hostParts.shift();
                                }
                            }
                        }
                    } catch(e) {}
                })();
                """.trimIndent(),
                null
            )
        }

        // 3. Clear cookies in CookieManager for this one-time user action
        cookieManager.removeAllCookies {
            cookieManager.removeSessionCookies(null)
            cookieManager.flush()
        }

        runOnUiThread {
            showCustomToast("Cookies removed", CustomToast.LENGTH_LONG)
            showLoader()
            binding.webView.reload()
        }
    }

    /** Accepts 0.5 or 50 (percent) and returns a 0..1 fraction. <=0 returns [default]. */
    private fun normalizeRatio(value: Double, default: Float): Float {
        if (value <= 0.0) return default
        val v = if (value > 1.0) value / 100.0 else value
        return v.toFloat().coerceIn(0f, 1f)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    class AndroidVideoEvents(private val activity: WebViewActivity) {
        @JavascriptInterface
        fun onVideoPlay() {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.rotateLandscape()
                }
            }
        }

        @JavascriptInterface
        fun onVideoPause() {
            // Do nothing on pause
        }

        @JavascriptInterface
        fun onVideoEnd() {
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.rotatePortrait()
                }
            }
        }
    }

    class ReactNativeWebViewInterface(private val activity: WebViewActivity) {
        @JavascriptInterface
        fun postMessage(message: String?) {
            activity.handleWebViewMessage(message)
        }
    }
}