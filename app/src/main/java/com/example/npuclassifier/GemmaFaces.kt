package com.example.npuclassifier

import android.content.Context
import android.net.Uri

class GemmaEmbedder(ctx: Context) : Embedder {
    override val id: String = "gemma"
    override fun embedImage(bmp: android.graphics.Bitmap): FloatArray = FloatArray(0)
    override fun embedText(text: String): FloatArray? = null
    override fun close() { }
}

object ModelDownloader {
    fun status(ctx: Context): String = "not installed"
    fun importFrom(ctx: Context, uri: Uri) { }
    fun download(ctx: Context, onProgress: (Int) -> Unit) { }
}

data class FaceHit(val x: Int, val y: Int, val w: Int, val h: Int, val emb: FloatArray)

object FaceEngine {
    fun detectAndEmbed(ctx: Context, bmp: android.graphics.Bitmap): List<FaceHit> = emptyList()
}