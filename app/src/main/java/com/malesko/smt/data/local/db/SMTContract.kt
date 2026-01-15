package com.malesko.smt.data.local.db

object SmtContract {

    object Artists {
        const val TABLE = "artists"
        const val COL_ID = "id"
        const val COL_NAME = "name"
    }

    object Tunings {
        const val TABLE = "tunings"
        const val COL_ID = "id"
        const val COL_ARTIST_ID = "artist_id"
        const val COL_TUNING = "tuning"
    }
}
