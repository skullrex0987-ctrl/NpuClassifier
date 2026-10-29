package com.example.npuclassifier

object Emb {
    fun fromScores(s: FloatArray): FloatArray {
        // clamp: a float model returns logits that can be negative, and sqrt(-x) is NaN,
        // which would silently poison every cosine below (NaN >= threshold is always false).
        val sqrt = FloatArray(s.size) { kotlin.math.sqrt(maxOf(0f, s[it]).toDouble()).toFloat() }
        var norm = 0f
        for (v in sqrt) norm += v * v
        norm = kotlin.math.sqrt(norm.toDouble()).toFloat()
        if (norm == 0f) return sqrt
        return FloatArray(sqrt.size) { sqrt[it] / norm }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        val n = minOf(a.size, b.size)
        for (i in 0 until n) dot += a[i] * b[i]
        return dot
    }
}