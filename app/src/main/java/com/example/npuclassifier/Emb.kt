package com.example.npuclassifier

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Emb {
    fun fromScores(s: FloatArray): FloatArray {
        val sqrt = FloatArray(s.size) { kotlin.math.sqrt(s[it].toDouble()).toFloat() }
        var norm = 0f
        for (v in sqrt) norm += v * v
        norm = kotlin.math.sqrt(norm.toDouble()).toFloat()
        if (norm == 0f) return sqrt
        return FloatArray(sqrt.size) { sqrt[it] / norm }
    }

    fun cosine(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }
}