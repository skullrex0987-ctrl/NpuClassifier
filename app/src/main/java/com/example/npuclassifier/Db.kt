package com.example.npuclassifier

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

object Db {
    fun get(ctx: Context): SQLiteDatabase = Helper(ctx).writableDatabase

    private class Helper(ctx: Context) : SQLiteOpenHelper(ctx, "npusorter.db", null, 2) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""CREATE TABLE media(id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT UNIQUE NOT NULL, path TEXT, uri TEXT NOT NULL, name TEXT NOT NULL,
              is_video INTEGER, size INTEGER, mtime INTEGER, w INTEGER, h INTEGER, status TEXT NOT NULL, label TEXT, extra_labels TEXT,
              source TEXT, conf REAL, gap REAL, top_json TEXT, backend TEXT, ms INTEGER, note TEXT, ocr TEXT, tags TEXT, thumb TEXT,
              dest_path TEXT, batch_id INTEGER, scanned_at INTEGER, emb_mnet BLOB, emb_clip BLOB, emb_gemma BLOB, frames_json TEXT)""")
            db.execSQL("CREATE INDEX idx_media_status ON media(status)")
            db.execSQL("CREATE INDEX idx_media_label ON media(label)")
            db.execSQL("CREATE VIRTUAL TABLE media_fts USING fts4(name, ocr, tags, label)")
            db.execSQL("""CREATE TABLE history(id INTEGER PRIMARY KEY AUTOINCREMENT, batch_id INTEGER, ts INTEGER, media_id INTEGER, src_path TEXT, dest_path TEXT,
              action TEXT, label TEXT, conf REAL, source TEXT, thumb TEXT, ocr_snippet TEXT, tags TEXT, status TEXT)""")
            db.execSQL("""CREATE TABLE faces(id INTEGER PRIMARY KEY AUTOINCREMENT, media_id INTEGER, x INTEGER, y INTEGER, w INTEGER, h INTEGER, emb BLOB, person_id INTEGER)""")
            db.execSQL("""CREATE TABLE people(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)""")
        }
        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { }
        override fun onConfigure(db: SQLiteDatabase) {
            super.onConfigure(db)
            db.enableWriteAheadLogging()
        }
    }
}