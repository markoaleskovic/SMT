package com.malesko.smt.data.local.db

import android.content.ContentValues
import android.content.Context

data class SongRow(
    val tuningId: Long,
    val artistName: String,
    val tuning: String
)

class SongsRepository(context: Context) {

    private val dbHelper = SMTDbHelper(context.applicationContext)

    fun add(artistName: String, tuningRaw: String): Long {
        val artist = artistName.trim()
        require(artist.isNotEmpty()) { "Artist/Band is empty" }

        val tuning = normalizeTuning(tuningRaw)

        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val artistId = upsertArtistId(db, artist)

            val tuningValues = ContentValues().apply {
                put(SmtContract.Tunings.COL_ARTIST_ID, artistId)
                put(SmtContract.Tunings.COL_TUNING, tuning)
            }

            // With UNIQUE(artist_id, tuning) this will return -1 if duplicate.
            val newId = db.insert(SmtContract.Tunings.TABLE, null, tuningValues)

            db.setTransactionSuccessful()
            return newId
        } finally {
            db.endTransaction()
        }
    }

    fun deleteTuning(tuningId: Long): Int {
        return dbHelper.writableDatabase.delete(
            SmtContract.Tunings.TABLE,
            "${SmtContract.Tunings.COL_ID}=?",
            arrayOf(tuningId.toString())
        )
    }

    fun searchByArtist(query: String): List<SongRow> {
        val q = query.trim()
        val db = dbHelper.readableDatabase

        val sql = StringBuilder().apply {
            append(
                """
                SELECT
                    t.${SmtContract.Tunings.COL_ID} AS tid,
                    a.${SmtContract.Artists.COL_NAME} AS aname,
                    t.${SmtContract.Tunings.COL_TUNING} AS tuning
                FROM ${SmtContract.Tunings.TABLE} t
                JOIN ${SmtContract.Artists.TABLE} a
                    ON a.${SmtContract.Artists.COL_ID} = t.${SmtContract.Tunings.COL_ARTIST_ID}
                """.trimIndent()
            )
            if (q.isNotEmpty()) {
                append(" WHERE a.${SmtContract.Artists.COL_NAME} LIKE ? ")
            }
            append(" ORDER BY a.${SmtContract.Artists.COL_NAME} COLLATE NOCASE ASC, tuning COLLATE NOCASE ASC")
        }.toString()

        val args = if (q.isNotEmpty()) arrayOf("%$q%") else null
        val cursor = db.rawQuery(sql, args)

        return cursor.use { c ->
            val out = mutableListOf<SongRow>()
            val idxTid = c.getColumnIndexOrThrow("tid")
            val idxA = c.getColumnIndexOrThrow("aname")
            val idxT = c.getColumnIndexOrThrow("tuning")

            while (c.moveToNext()) {
                out += SongRow(
                    tuningId = c.getLong(idxTid),
                    artistName = c.getString(idxA),
                    tuning = c.getString(idxT)
                )
            }
            out
        }
    }

    fun getDistinctTunings(): List<String> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT DISTINCT ${SmtContract.Tunings.COL_TUNING}
            FROM ${SmtContract.Tunings.TABLE}
            ORDER BY ${SmtContract.Tunings.COL_TUNING} COLLATE NOCASE ASC
            """.trimIndent(),
            null
        )

        return cursor.use { c ->
            val out = mutableListOf<String>()
            while (c.moveToNext()) out += c.getString(0)
            out
        }
    }

    private fun upsertArtistId(db: android.database.sqlite.SQLiteDatabase, artist: String): Long {
        // 1) Try insert (UNIQUE(name) prevents duplicates)
        val values = ContentValues().apply {
            put(SmtContract.Artists.COL_NAME, artist)
        }
        val inserted = db.insert(SmtContract.Artists.TABLE, null, values)
        if (inserted != -1L) return inserted

        // 2) Already exists -> SELECT id
        val cursor = db.query(
            SmtContract.Artists.TABLE,
            arrayOf(SmtContract.Artists.COL_ID),
            "${SmtContract.Artists.COL_NAME}=?",
            arrayOf(artist),
            null, null, null,
            "1"
        )

        return cursor.use { c ->
            if (!c.moveToFirst()) throw IllegalStateException("Artist insert failed")
            c.getLong(0)
        }
    }

    private fun normalizeTuning(raw: String): String {
        val notes = parseTuningNotes(raw)
        require(notes.size == 6) { "Tuning must have 6 notes (strings), e.g. E A D G B E" }
        return notes.joinToString(" ")
    }

    private fun parseTuningNotes(raw: String): List<String> {
        val s = raw.trim()
        require(s.isNotEmpty()) { "Tuning is empty" }

        val spaced = s.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokens = if (spaced.size > 1) spaced else tokenizeCompact(s)

        return tokens.map { normalizeNoteToken(it) }
    }

    private fun tokenizeCompact(s: String): List<String> {
        val out = mutableListOf<String>()
        val src = s.trim()
        var i = 0

        while (i < src.length) {
            val ch = src[i]
            if (ch.isWhitespace()) { i++; continue }

            require(ch.uppercaseChar() in 'A'..'G') { "Invalid note at position $i in '$s'" }

            var token = ch.toString()
            val next = i + 1
            if (next < src.length) {
                val acc = src[next]
                if (acc == '#' || acc == 'b' || acc == 'B') {
                    token += acc.toString()
                    i += 2
                } else {
                    i += 1
                }
            } else {
                i += 1
            }
            out += token
        }
        return out
    }

    private fun normalizeNoteToken(token: String): String {
        val t = token.trim()
        require(t.isNotEmpty()) { "Empty note token" }

        val letter = t[0].uppercaseChar()
        require(letter in 'A'..'G') { "Invalid note '$token'" }

        val acc = t.drop(1)
        return when {
            acc.isEmpty() -> letter.toString()
            acc == "#" -> "$letter#"
            acc.equals("b", ignoreCase = true) -> "${letter}b"
            else -> throw IllegalArgumentException("Invalid accidental in '$token' (use # or b)")
        }
    }
}
