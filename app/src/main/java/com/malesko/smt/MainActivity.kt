package com.malesko.smt

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.malesko.smt.audio.TunerEngine
import com.malesko.smt.databinding.ActivityMainBinding
import kotlin.math.log2
import kotlin.math.roundToInt

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

        binding.btnMenu.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.END)) {
                binding.drawerLayout.closeDrawer(androidx.core.view.GravityCompat.END)
            } else {
                binding.drawerLayout.openDrawer(androidx.core.view.GravityCompat.END)
            }
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
        ) { pitchHz ->
            val note = freqToNoteNameWithOctave(pitchHz)
            runOnUiThread {
                binding.tvNote.text = note
                binding.tvFreq.text = "%.1f Hz".format(pitchHz)
            }
        }

        @Suppress("MissingPermission")
        tunerEngine!!.start()
    }

    private fun stopTuner() {
        tunerEngine?.stop()
        tunerEngine = null
    }

    override fun onStop() {
        super.onStop()
        stopTuner()
    }

    private fun freqToNoteNameWithOctave(freqHz: Float): String {
        if (freqHz <= 0f) return "--"

        val midi = (69.0 + 12.0 * log2(freqHz / 440.0)).roundToInt()
        val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val noteIndex = ((midi % 12) + 12) % 12
        val octave = (midi / 12) - 1 // 60 -> 4 (C4)

        return "${names[noteIndex]}$octave"
    }
}
