package com.malesko.smt.ui.songs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.malesko.smt.R

class AddSongDialogFragment(
    private val onSave: (artist: String, tuning: String) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_song, null)
        val etArtist = view.findViewById<TextInputEditText>(R.id.etArtist)
        val etTuning = view.findViewById<TextInputEditText>(R.id.etTuning)

        return AlertDialog.Builder(requireContext())
            .setTitle("Add tuning")
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val artist = etArtist.text?.toString().orEmpty()
                val tuning = etTuning.text?.toString().orEmpty()
                onSave(artist, tuning)
            }
            .create()
    }
}
