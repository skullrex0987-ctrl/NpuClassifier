package com.example.npuclassifier

import android.graphics.Bitmap

interface Embedder {
    val id: String
    fun embedImage(bmp: Bitmap): FloatArray
    fun embedText(text: String): FloatArray?
    fun close()
}