package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

data class Enrichment(val ocr: String, val tags: List<String>)

object Enricher {
    fun run(ctx: Context, m: MediaItem, cfg: SortSettings, wantOcr: Boolean, wantTags: Boolean): Enrichment {
        if (!wantOcr) return Enrichment("", emptyList())
        val bmp = try { decodeBitmap(ctx, m) } catch (e: Exception) { null } ?: return Enrichment("", emptyList())
        val ocr = try { recognize(bmp) } catch (e: Exception) { "" }
        bmp.recycle()
        return Enrichment(ocr, emptyList())
    }

    private fun decodeBitmap(ctx: Context, m: MediaItem): Bitmap? {
        return if (m.isVideo) null else {
            try {
                if (m.path != null) android.graphics.BitmapFactory.decodeFile(m.path) else null
            } catch (e: Exception) { null }
        }
    }

    private fun recognize(bmp: Bitmap): String {
        val latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val dev = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        val sb = StringBuilder()
        try {
            val img = InputImage.fromBitmap(bmp, 0)
            val r1 = com.google.android.gms.tasks.Tasks.await(latin.process(img))
            sb.append(r1.text).append("\n")
        } catch (e: Exception) { }
        try {
            val img = InputImage.fromBitmap(bmp, 0)
            val r2 = com.google.android.gms.tasks.Tasks.await(dev.process(img))
            if (r2.text.isNotBlank()) sb.append(r2.text)
        } catch (e: Exception) { }
        try { latin.close() } catch (e: Exception) { }
        try { dev.close() } catch (e: Exception) { }
        return sb.toString().trim()
    }
}