package com.malesko.smt.ui.tuner

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.malesko.smt.R
import com.malesko.smt.audio.TunerEngine
import com.malesko.smt.audio.TunerState
import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.malesko.smt.data.local.db.SongsRepository
import com.malesko.smt.databinding.FragmentTunerBinding
import kotlin.math.abs

class TunerFragment : Fragment() {

    private var _binding: FragmentTunerBinding? = null
    private val binding get() = _binding!!
    private lateinit var songsRepo: SongsRepository

    private var currentTargetNotes: List<TargetNote> = standardE()
    private var tunerEngine: TunerEngine? = null

    private val requestMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startTunerAfterPermissionGranted()
            else {
                binding.tvNote.text = "--"
                binding.tvFreq.text = getString(R.string.mic_permission)
                setAllTicksInactive()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTunerBinding.inflate(inflater, container, false)

        songsRepo = SongsRepository(requireContext())

        binding.btnTuning.setOnClickListener {
            showTuningPicker()
        }

        // restore saved tuning label on button
        val savedLabel = prefs.getString(PREF_TUNING_LABEL, null)
        if (!savedLabel.isNullOrBlank()) {
            binding.btnTuning.text = savedLabel
        }

        val savedId = prefs.getLong(PREF_TUNING_ID, -1L)
        if (savedId != -1L) {
            // simplest: fetch by id and set currentTargetNotes (implement repo function below)
            val t = songsRepo.getTuningById(savedId)
            if (t != null) currentTargetNotes = parseTuningToTargets(t.tuning)
        }

        return binding.root
    }


    override fun onStart() {
        super.onStart()
        ensureMicThenStart()
    }

    override fun onStop() {
        super.onStop()
        stopTuner()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun ensureMicThenStart() {
        val ctx = requireContext()
        val granted = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) startTunerAfterPermissionGranted()
        else requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startTunerAfterPermissionGranted() {
        if (tunerEngine != null) return

        tunerEngine = TunerEngine(
            sampleRate = 44100,
            frameSize = 4096,
            preferFloat = false
        ) { state ->
            // Called from the engine thread; switch to UI thread
            activity?.runOnUiThread {
                binding.tvNote.text = state.noteName
                binding.tvFreq.text = "%.1f Hz".format(state.pitchHz)

                val ok = isTargetNote(state.noteName, currentTargetNotes)
                val colorRes = if (ok) R.color.note_ok else R.color.note_bad
                binding.tvNote.setTextColor(ContextCompat.getColor(requireContext(), colorRes))

                updateCentsUi(state)
            }

        }

        @Suppress("MissingPermission")
        tunerEngine!!.start()
    }

    private fun stopTuner() {
        tunerEngine?.stop()
        tunerEngine = null
    }

    private fun setAllTicksInactive() {
        val ticks = listOf(
            binding.tickL4, binding.tickL3, binding.tickL2, binding.tickL1,
            binding.tickC,
            binding.tickR1, binding.tickR2, binding.tickR3, binding.tickR4
        )
        ticks.forEach { it.setBackgroundResource(R.drawable.tick_inactive) }
    }

    private fun updateCentsUi(state: TunerState) {
        val ticks = listOf(
            binding.tickL4, binding.tickL3, binding.tickL2, binding.tickL1,
            binding.tickC,
            binding.tickR1, binding.tickR2, binding.tickR3, binding.tickR4
        )

        ticks.forEach { it.setBackgroundResource(R.drawable.tick_inactive) }

        val cents = state.centsOff
        if (!cents.isFinite()) return

        val index = when {
            cents <= -40f -> 0
            cents <= -25f -> 1
            cents <= -15f -> 2
            cents <= -7f  -> 3
            cents <   7f  -> 4
            cents <  15f  -> 5
            cents <  25f  -> 6
            cents <  40f  -> 7
            else          -> 8
        }

        val isInTune = abs(cents) < 10f
        val activeRes = if (isInTune) R.drawable.tick_active_good else R.drawable.tick_active_bad
        ticks[index].setBackgroundResource(activeRes)
    }

    private val prefs by lazy {
        requireContext().getSharedPreferences("tuner_prefs", Context.MODE_PRIVATE)
    }
    private val PREF_TUNING_ID = "pref_tuning_id"
    private val PREF_TUNING_LABEL = "pref_tuning_label"


    data class TargetNote(val name: String, val octave: Int) {
        override fun toString(): String = "$name$octave"
    }

    private fun standardE(): List<TargetNote> = listOf(
        TargetNote("E", 2),
        TargetNote("A", 2),
        TargetNote("D", 3),
        TargetNote("G", 3),
        TargetNote("B", 3),
        TargetNote("E", 4),
    )

    private fun parseTuningToTargets(tuning: String): List<TargetNote> {
        // expects "DADGBE" etc.
        val letters = tuning.trim().uppercase()
        require(letters.length == 6) { "Tuning must have 6 notes (e.g., DADGBE)" }

        // Guitar string octaves: 6th..1st are (2,2,3,3,3,4)
        val octaves = listOf(2, 2, 3, 3, 3, 4)

        return letters.mapIndexed { idx, ch ->
            TargetNote(ch.toString(), octaves[idx])
        }
    }

    private fun showTuningPicker() {
        val tunings = songsRepo.getAllTunings()
        if (tunings.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Tunings")
                .setMessage("No tunings in database.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = tunings.map { "${it.title}: ${it.tuning}" }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Select tuning")
            .setItems(labels) { _, which ->
                val selected = tunings[which]

                // Update UI
                binding.btnTuning.text = labels[which]

                prefs.edit()
                    .putLong(PREF_TUNING_ID, selected.id)
                    .putString(PREF_TUNING_LABEL, labels[which])
                    .apply()

                currentTargetNotes = parseTuningToTargets(selected.tuning)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isTargetNote(detected: String, targets: List<TargetNote>): Boolean {
        val name = detected.takeWhile { it.isLetter() || it == '#' }
        val octave = detected.drop(name.length).toIntOrNull() ?: return false
        return targets.any { it.name == name && it.octave == octave }
    }


}
