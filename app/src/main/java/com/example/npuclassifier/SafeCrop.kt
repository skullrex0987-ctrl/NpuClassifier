package com.example.npuclassifier

import android.graphics.Bitmap

object SafeCrop {
    /** Safe center crop: clamps x,y,w,h to bitmap; scales up if smaller than size; never throws. */
    fun safeCenterCrop(bmp: Bitmap, size: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= 0 || h <= 0 || size <= 0) return bmp
        // scale up if either side smaller than size
        val scale = if (w < size || h < size) size.toFloat() / minOf(w, h) else 1f
        val newW = (w * scale).toInt().coerceAtLeast(size)
        val newH = (h * scale).toInt().coerceAtLeast(size)
        val scaled = if (scale > 1f) Bitmap.createScaledBitmap(bmp, newW, newH, true) else bmp
        val cropX = ((scaled.width - size) / 2).coerceIn(0, scaled.width - size.coerceAtMost(scaled.width))
        val cropY = ((scaled.height - size) / 2).coerceIn(0, scaled.height - size.coerceAtMost(scaled.height))
        val cw = size.coerceAtMost(scaled.width)
        val ch = size.coerceAtMost(scaled.height)
        return try {
            Bitmap.createBitmap(scaled, cropX, cropY, cw, ch)
        } catch (e: Exception) {
            scaled
        }
    }
}