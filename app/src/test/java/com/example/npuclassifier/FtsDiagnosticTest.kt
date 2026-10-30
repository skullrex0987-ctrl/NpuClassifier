package com.example.npuclassifier

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FtsDiagnosticTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun dumpFtsState() {
        val c = ctx()
        // Clean slate
        c.deleteDatabase("npusorter.db")
        val items = listOf(MediaItem(uri = android.net.Uri.parse("file:///p.jpg"), path = "/p.jpg", name = "p.jpg", isVideo = false))
        val returned = Repo.upsertPending(c, items, 99L)
        println("DIAG upsertPending returned ids: ${returned.map { it.id }}")
        val page = Repo.page(c, Filter(), 0, 10)
        println("DIAG page size: ${page.size}")
        println("DIAG page[0].media.id: ${page.getOrNull(0)?.media?.id}")
        if (page.isNotEmpty()) {
            Repo.saveEnrichment(c, page[0].media.id, "payment upi transaction", emptyList())
        }
        // sqlite version
        val db = Db.get(c)
        println("DIAG sqlite_version: ${db.rawQuery("SELECT sqlite_version()", null).use { it.apply { moveToFirst() }; it.getString(0) }}")
        // compile options
        println("DIAG compile_options: ${db.rawQuery("PRAGMA compile_options", null).use { cur -> val sb = StringBuilder(); while (cur.moveToNext()) sb.append(cur.getString(0)).append(","); sb.toString() }}")
        // media_fts count
        val ftsCount = db.rawQuery("SELECT count(*) FROM media_fts", null).use { it.apply { moveToFirst() }; it.getInt(0) }
        println("DIAG media_fts count: $ftsCount")
        // media_fts rows
        db.rawQuery("SELECT rowid, name, ocr FROM media_fts", null).use { cur ->
            while (cur.moveToNext()) {
                println("DIAG fts row: rowid=${cur.getLong(0)} name=${cur.getString(1)} ocr=${cur.getString(2)}")
            }
        }
        // media ids
        db.rawQuery("SELECT id, ocr FROM media", null).use { cur ->
            while (cur.moveToNext()) {
                println("DIAG media row: id=${cur.getLong(0)} ocr=${cur.getString(1)}")
            }
        }
        // raw MATCH forms
        for (q in arrayOf("pay*", "invoice", "payment")) {
            try {
                val r = db.rawQuery("SELECT rowid FROM media_fts WHERE media_fts MATCH ?", arrayOf(q)).use { cur -> val l = mutableListOf<Long>(); while (cur.moveToNext()) l.add(cur.getLong(0)); l }
                println("DIAG raw MATCH '$q' -> $r")
            } catch (e: Exception) {
                println("DIAG raw MATCH '$q' -> ERROR ${e.message}")
            }
        }
        // Repo.ftsSearch
        val searchResult = Repo.ftsSearch(c, "pay", 10)
        println("DIAG Repo.ftsSearch('pay') -> $searchResult")
        // Also try the query string Repo.ftsSearch builds
        println("DIAG ftsSearch built query: pay*")
        println("DIAG_DONE")
        org.junit.Assert.fail("DIAG_DUMP: ftsCount=$ftsCount searchResult=$searchResult")
    }
}