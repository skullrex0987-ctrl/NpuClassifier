package com.example.npuclassifier

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File

object Media {
    private val IMG = setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "bmp", "gif")
    private val VID = setOf("mp4", "mkv", "3gp", "webm", "mov", "m4v")

    /** Recursively lists images/videos under [root], skipping hidden and excluded folders. */
    fun scanFolder(root: File, excludes: List<String>): List<MediaItem> {
        fun excluded(f: File): Boolean =
            f.name.startsWith(".") || excludes.any { e ->
                if (e.startsWith("/")) f.path.startsWith(e) else f.name.equals(e, ignoreCase = true)
            }
        val out = ArrayList<MediaItem>()
        root.walkTopDown()
            .onEnter { d -> d == root || !excluded(d) }
            .filter { it.isFile }
            .forEach { f ->
                val ext = f.extension.lowercase()
                when {
                    ext in IMG -> out.add(MediaItem(uri = Uri.fromFile(f), path = f.absolutePath, name = f.name, isVideo = false))
                    ext in VID -> out.add(MediaItem(uri = Uri.fromFile(f), path = f.absolutePath, name = f.name, isVideo = true))
                }
            }
        return out
    }

    fun fromUri(ctx: Context, uri: Uri): MediaItem {
        var name = "file_${System.currentTimeMillis()}"
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.let { name = it }
        }
        val mime = ctx.contentResolver.getType(uri) ?: ""
        return MediaItem(uri = uri, path = null, name = name, isVideo = mime.startsWith("video"))
    }

    /** content://...externalstorage.../tree/primary:DCIM/Camera -> /storage/emulated/0/DCIM/Camera */
    fun treePath(tree: Uri): String? = try {
        val parts = DocumentsContract.getTreeDocumentId(tree).split(":", limit = 2)
        val base = if (parts[0] == "primary") "/storage/emulated/0" else "/storage/${parts[0]}"
        if (parts.size > 1 && parts[1].isNotEmpty()) "$base/${parts[1]}" else base
    } catch (e: Exception) {
        null
    }
}
