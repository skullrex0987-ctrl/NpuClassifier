package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri

object VideoAnalyzer {
    fun forEachFrame(ctx: Context, m: MediaItem, cfg: SortSettings, onFrame: (Bitmap) -> Unit) {
        val r = MediaMetadataRetriever()
        try {
            if (m.path != null) r.setDataSource(m.path) else r.setDataSource(ctx, m.uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val maxFrames = when (cfg.videoDepth) {
                0 -> 8
                1 -> 24
                else -> (durMs / 2000L).toInt().coerceAtMost(120).coerceAtLeast(1)
            }
            val n = maxFrames.coerceAtLeast(1)
            for (i in 0 until n) {
                val tUs = durMs * 1000L * (2 * i + 1) / (2 * n)
                try {
                    val bmp = r.getScaledFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 256, 256)
                    if (bmp != null) {
                        onFrame(bmp)
                        bmp.recycle()
                    }
                } catch (e: Exception) {
                    // skip failed frames
                }
            }
        } finally {
            r.release()
        }
    }
}