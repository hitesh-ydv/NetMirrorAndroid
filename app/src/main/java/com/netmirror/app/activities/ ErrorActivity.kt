package com.netmirror.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.netmirror.app.databinding.ActivityErrorBinding
import com.netmirror.app.utils.CustomToast
import com.netmirror.app.utils.FullScreenUtil
import com.netmirror.app.utils.showCustomToast

class ErrorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityErrorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityErrorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        FullScreenUtil.enable(this)

        val errorMessage = intent.getStringExtra("error_message")
        if (!errorMessage.isNullOrBlank()) {
            binding.message.text = errorMessage
        }

        startEntranceAnimation()

        binding.retryButton.setOnClickListener {
            binding.retryButton.animate()
                .scaleX(0.94f)
                .scaleY(0.94f)
                .setDuration(100)
                .withEndAction {
                    binding.retryButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .withEndAction {
                            startActivity(Intent(this, SplashActivity::class.java))
                            finish()
                        }
                        .start()
                }
                .start()
        }
    }

    private fun startEntranceAnimation() {
        binding.contentLayout.alpha = 0f
        binding.contentLayout.translationY = 40f

        binding.contentLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }
}