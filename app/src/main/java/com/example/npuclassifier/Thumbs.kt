package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.FileOutputStream

object Thumbs {
    fun save(ctx: Context, id: Long, bmp: Bitmap): String? {
        return try {
            val dir = File(ctx.filesDir, "thumbs")
            if (!dir.exists()) dir.mkdirs()
            val f = File(dir, "$id.jpg")
            val scaled = if (bmp.width > 256 || bmp.height > 256) {
                val ratio = 256f / maxOf(bmp.width, bmp.height)
                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true)
            } else bmp
            FileOutputStream(f).use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 70, out) }
            if (scaled !== bmp) scaled.recycle()
            f.absolutePath
        } catch (e: Exception) { null }
    }
}