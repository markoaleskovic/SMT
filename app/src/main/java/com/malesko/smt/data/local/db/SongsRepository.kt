package com.malesko.smt.data.local.db

import android.content.ContentValues
import android.content.Context

data class Song(
    val id: Long = 0,
    val title: String,
    val tuning: String
)

class SongsRepository(context: Context) {
    private val dbHelper = SMTDbHelper(context.applicationContext)

    fun insert(song: Song): Long {
        val values = ContentValues().apply {
            put(SongsContract.COL_TITLE, song.title.trim())
            put(SongsContract.COL_TUNING, normalizeTuning(song.tuning))
        }
        return dbHelper.writableDatabase.insert(SongsContract.TABLE, null, values)
    }


    fun getAllTunings(): List<Song> {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SongsContract.TABLE,
            arrayOf(SongsContract.COL_ID, SongsContract.COL_TITLE, SongsContract.COL_TUNING),
            null, null, null, null,
            "${SongsContract.COL_TITLE} COLLATE NOCASE ASC"
        )

        return cursor.use {
            val out = mutableListOf<Song>()
            while (it.moveToNext()) {
                out += Song(
                    id = it.getLong(0),
                    title = it.getString(1),
                    tuning = it.getString(2)
                )
            }
            out
        }
    }

    fun update(song: Song): Int {
        val values = ContentValues().apply {
            put(SongsContract.COL_TITLE, song.title.trim())
            put(SongsContract.COL_TUNING, normalizeTuning(song.tuning))
        }
        return dbHelper.writableDatabase.update(
            SongsContract.TABLE,
            values,
            "${SongsContract.COL_ID}=?",
            arrayOf(song.id.toString())
        )
    }

    fun delete(id: Long): Int {
        return dbHelper.writableDatabase.delete(
            SongsContract.TABLE,
            "${SongsContract.COL_ID}=?",
            arrayOf(id.toString())
        )
    }

    private fun normalizeTuning(t: String): String =
        t.trim().uppercase().also { require(it.length == 6) { "Tuning must be 6 letters, e.g. DADGBE" } }

    fun getTuningById(id: Long): Song? {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            SongsContract.TABLE,
            arrayOf(SongsContract.COL_ID, SongsContract.COL_TITLE, SongsContract.COL_TUNING),
            "${SongsContract.COL_ID}=?",
            arrayOf(id.toString()),
            null,
            null,
            null,
            "1"
        )

        return cursor.use { c ->
            if (!c.moveToFirst()) return@use null

            val idxId = c.getColumnIndexOrThrow(SongsContract.COL_ID)
            val idxTitle = c.getColumnIndexOrThrow(SongsContract.COL_TITLE)
            val idxTuning = c.getColumnIndexOrThrow(SongsContract.COL_TUNING)

            Song(
                id = c.getLong(idxId),
                title = c.getString(idxTitle),
                tuning = c.getString(idxTuning)
            )
        }
    }

}
