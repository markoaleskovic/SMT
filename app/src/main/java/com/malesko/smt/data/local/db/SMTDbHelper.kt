package com.malesko.smt.data.local.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class SMTDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE ${SongsContract.TABLE} (
                ${SongsContract.COL_ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${SongsContract.COL_TITLE} TEXT NOT NULL,
                ${SongsContract.COL_TUNING} TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // For defense: explain that raising DATABASE_VERSION triggers this. [web:13]
        // Simple strategy for v1→v2 prototypes:
        db.execSQL("DROP TABLE IF EXISTS ${SongsContract.TABLE}")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "tuner.db"
        private const val DATABASE_VERSION = 1
    }
}