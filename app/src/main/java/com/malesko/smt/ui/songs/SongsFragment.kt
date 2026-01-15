package com.malesko.smt.ui.songs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.malesko.smt.data.local.db.SongsRepository
import com.malesko.smt.databinding.FragmentSongsBinding
import com.malesko.smt.data.local.prefs.PrefsKeys
import androidx.core.content.edit


class SongsFragment : Fragment() {

    private var _binding: FragmentSongsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: SongsRepository
    private lateinit var adapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSongsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = SongsRepository(requireContext())

        adapter = SongAdapter { row ->
            saveCurrentTuning(row.tuning)
            Snackbar.make(binding.root, "Selected: ${row.artistName} (${row.tuning})", Snackbar.LENGTH_SHORT).show()
        }

        binding.rvSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongs.adapter = adapter

        binding.btnAddSong.setOnClickListener {
            AddSongDialogFragment { artist, tuning ->
                try {
                    repo.add(artist, tuning)
                    refreshList(binding.etSearch.text?.toString().orEmpty())
                } catch (e: Exception) {
                    Snackbar.make(binding.root, e.message ?: "Invalid input", Snackbar.LENGTH_LONG).show()
                }
            }.show(parentFragmentManager, "AddSongDialog")
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshList(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        refreshList("")
    }

    private fun refreshList(query: String) {
        val rows = repo.searchByArtist(query)   // List<SongRow>
        adapter.submitList(rows)
    }


    private fun saveCurrentTuning(tuning: String) {
        val prefs = requireContext().getSharedPreferences(PrefsKeys.PREFS_TUNER, Context.MODE_PRIVATE)
        prefs.edit {
            putString(PrefsKeys.KEY_CURRENT_TUNING, tuning)
                .putString(PrefsKeys.KEY_TUNING_LABEL, tuning)
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
