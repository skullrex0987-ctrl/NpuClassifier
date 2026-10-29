package com.example.npuclassifier

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File
import java.io.IOException

object FileOps {
    fun safeName(s: String): String =
        s.replace(Regex("[^A-Za-z0-9 _-]"), "_").trim().ifBlank { "Unsorted" }.take(60)

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = f.nameWithoutExtension
        val ext = f.extension.let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!f.exists()) return f
            i++
        }
    }

    /** Copies (or moves) [m] into <destRoot>/<folder>, or <destRoot>/Videos/<folder> for videos. Creates folders. */
    fun place(ctx: Context, m: MediaItem, folder: String, cfg: SortSettings): Op {
        val root = File(cfg.destRoot)
        val dir = File(if (m.isVideo) File(root, "Videos") else root, safeName(folder))
        if (!dir.exists() && !dir.mkdirs()) throw IOException("Cannot create ${dir.path}")
        val dest = unique(dir, m.name)
        var moved = false
        if (m.path != null) {
            val src = File(m.path)
            if (cfg.move) {
                if (!src.renameTo(dest)) {
                    src.copyTo(dest)
                    src.delete()
                }
                moved = true
            } else {
                src.copyTo(dest)
            }
        } else { // picked via file picker: no real path, so always a copy
            ctx.contentResolver.openInputStream(m.uri).use { ins ->
                requireNotNull(ins) { "Cannot open ${m.name}" }
                dest.outputStream().use { ins.copyTo(it) }
            }
        }
        val scan = if (moved && m.path != null) arrayOf(m.path, dest.path) else arrayOf(dest.path)
        MediaScannerConnection.scanFile(ctx, scan, null, null)
        return Op(m.path, dest.path, moved)
    }

    fun undo(ctx: Context, op: Op) {
        val dest = File(op.dest)
        if (op.moved && op.src != null) {
            val s = File(op.src)
            s.parentFile?.mkdirs()
            if (!dest.renameTo(s)) {
                dest.copyTo(s)
                dest.delete()
            }
            MediaScannerConnection.scanFile(ctx, arrayOf(s.path, dest.path), null, null)
        } else {
            dest.delete()
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.path), null, null)
        }
    }
}
