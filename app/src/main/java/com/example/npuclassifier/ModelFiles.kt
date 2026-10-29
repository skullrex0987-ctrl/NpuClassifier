package com.example.npuclassifier

import android.content.Context
import java.io.File

object ModelFiles {
    fun ensure(ctx: Context, assetName: String): File {
        val out = File(ctx.filesDir, assetName)
        if (out.exists()) {
            // Check size matches asset
            val assetSize = ctx.assets.open(assetName).use { it.available().toLong() }
            if (out.length() == assetSize) return out
        }
        out.parentFile?.mkdirs()
        ctx.assets.open(assetName).use { input ->
            out.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return out
    }
}