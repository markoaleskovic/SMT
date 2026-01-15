package com.malesko.smt.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SMTDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE ${SmtContract.Artists.TABLE} (
                ${SmtContract.Artists.COL_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SmtContract.Artists.COL_NAME} TEXT NOT NULL UNIQUE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE ${SmtContract.Tunings.TABLE} (
                ${SmtContract.Tunings.COL_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SmtContract.Tunings.COL_ARTIST_ID} INTEGER NOT NULL,
                ${SmtContract.Tunings.COL_TUNING} TEXT NOT NULL,
                FOREIGN KEY(${SmtContract.Tunings.COL_ARTIST_ID})
                    REFERENCES ${SmtContract.Artists.TABLE}(${SmtContract.Artists.COL_ID})
                    ON DELETE CASCADE,
                UNIQUE(${SmtContract.Tunings.COL_ARTIST_ID}, ${SmtContract.Tunings.COL_TUNING})
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX idx_tunings_artist_id ON ${SmtContract.Tunings.TABLE}(${SmtContract.Tunings.COL_ARTIST_ID})"
        )

        db.execSQL(
            "CREATE INDEX idx_artists_name_nocase ON ${SmtContract.Artists.TABLE}(${SmtContract.Artists.COL_NAME} COLLATE NOCASE)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS ${SmtContract.Tunings.TABLE}")
        db.execSQL("DROP TABLE IF EXISTS ${SmtContract.Artists.TABLE}")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "tuner.db"
        private const val DATABASE_VERSION = 3
    }
}
