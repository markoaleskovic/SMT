package com.malesko.smt

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.malesko.smt.audio.NativeYin
import com.malesko.smt.databinding.ActivitySplashScreenBinding
import kotlin.concurrent.thread

class SplashScreenActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySplashScreenBinding
    @Volatile private var nativeReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        startLoadingAnimation()

        //Kick off native init
        warmUpNativeAsync()
    }

    private fun startLoadingAnimation() {
        binding.loadingBar.max = 100
        ValueAnimator.ofInt(0, 100).apply {
            duration = 1200
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                binding.loadingBar.progress = anim.animatedValue as Int
            }
            start()
        }
    }

    private fun warmUpNativeAsync() {
        thread(name = "NativeWarmup") {
            try {
                // Forces System.loadLibrary + constructs internal buffers once.
                val yin = NativeYin(sampleRate = 44100, bufferSize = 4096)
                yin.close()
                nativeReady = true
            } catch (_: Throwable) {
                nativeReady = false
            }
            maybeRedirect()
        }
    }

    @Synchronized
    private fun maybeRedirect() {
        if (!nativeReady) return

        runOnUiThread {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
