package com.malesko.smt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.malesko.smt.audio.TunerEngine
import com.malesko.smt.audio.TunerState
import com.malesko.smt.databinding.ActivityMainBinding
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var tunerEngine: TunerEngine? = null

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startTunerAfterPermissionGranted()
            } else {
                binding.tvNote.text = "--"
                binding.tvFreq.text = "Mic permission required"
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)


        binding.navSettings.setOnClickListener {
        }

        binding.navTuner.setOnClickListener {
        }

        binding.navSongs.setOnClickListener {
        }


        ensureMicThenStart()
    }

    private fun ensureMicThenStart() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            startTunerAfterPermissionGranted()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Call this ONLY when RECORD_AUDIO is granted.
    private fun startTunerAfterPermissionGranted() {
        if (tunerEngine != null) return

        tunerEngine = TunerEngine(
            sampleRate = 44100,
            frameSize = 4096,
            useFloat = true
        ) { state ->
            runOnUiThread {
                binding.tvNote.text = state.noteName
                binding.tvFreq.text = "%.1f Hz".format(state.pitchHz)
                updateCentsUi(state)
            }
        }

        @Suppress("MissingPermission")
        tunerEngine!!.start()
    }

    private fun updateCentsUi(state: TunerState) {
        // 1. Gather tick views
        val ticks = listOf(
            binding.tickL4,
            binding.tickL3,
            binding.tickL2,
            binding.tickL1,
            binding.tickC,
            binding.tickR1,
            binding.tickR2,
            binding.tickR3,
            binding.tickR4
        )

        // 2. Reset all to inactive
        val inactive = R.drawable.tick_inactive
        ticks.forEach { it.setBackgroundResource(inactive) }

        val cents = state.centsOff

        // If pitch is invalid (you can decide on your own sentinel), just leave inactive
        if (!cents.isFinite()) return

        // 3. Decide which index to highlight
        // indices: 0..8 -> L4..R4, center is 4
        val index = when {
            cents <= -40f -> 0
            cents <= -25f -> 1
            cents <= -15f -> 2
            cents <= -7f  -> 3
            cents <   7f  -> 4   // in tune
            cents <  15f  -> 5
            cents <  25f  -> 6
            cents <  40f  -> 7
            else          -> 8
        }

        val isInTune = kotlin.math.abs(cents) < 10f
        val activeRes = if (isInTune) R.drawable.tick_active_good else R.drawable.tick_active_bad

        ticks[index].setBackgroundResource(activeRes)
    }



    private fun stopTuner() {
        tunerEngine?.stop()
        tunerEngine = null
    }

    override fun onStop() {
        super.onStop()
        stopTuner()
    }
}
