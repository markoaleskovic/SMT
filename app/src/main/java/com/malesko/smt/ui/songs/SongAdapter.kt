package com.malesko.smt.ui.songs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.malesko.smt.R
import com.malesko.smt.data.local.db.SongRow

class SongAdapter(
    private val onClick: (SongRow) -> Unit
) : ListAdapter<SongRow, SongAdapter.SongVH>(Diff) {

    object Diff : DiffUtil.ItemCallback<SongRow>() {
        override fun areItemsTheSame(oldItem: SongRow, newItem: SongRow) =
            oldItem.tuningId == newItem.tuningId

        override fun areContentsTheSame(oldItem: SongRow, newItem: SongRow) =
            oldItem == newItem
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongVH(view, onClick)
    }

    override fun onBindViewHolder(holder: SongVH, position: Int) {
        holder.bind(getItem(position))
    }

    class SongVH(
        itemView: View,
        private val onClick: (SongRow) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvArtist: TextView = itemView.findViewById(R.id.tvArtist)
        private val tvTuning: TextView = itemView.findViewById(R.id.tvTuning)

        private var current: SongRow? = null

        init {
            itemView.setOnClickListener { current?.let(onClick) }
        }

        fun bind(item: SongRow) {
            current = item
            tvArtist.text = item.artistName
            tvTuning.text = item.tuning
        }
    }
}
