package com.malesko.smt.ui.songs

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class SongsPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount() = 3

    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> SongsFragment()         // your existing local DB list
        1 -> OnlineSongsFragment()   // new
        else -> MySongsFragment()    // new
    }
}
