package com.example.npuclassifier

import android.content.Context

class Prefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): SortSettings {
        val d = SortSettings()
        val saved = p.getStringSet("backends", null)
            ?.mapNotNull { runCatching { Backend.valueOf(it) }.getOrNull() }
            ?.toSet()
        val backends: Set<Backend> = if (saved.isNullOrEmpty()) d.backends else saved
        return SortSettings(
            threshold = p.getFloat("thr", d.threshold),
            minGap = p.getFloat("gap", d.minGap),
            move = p.getBoolean("move", d.move),
            destRoot = p.getString("dest", d.destRoot) ?: d.destRoot,
            excludes = p.getString("excl", d.excludes) ?: d.excludes,
            backends = backends,
            catThreshold = p.getFloat("catThr", d.catThreshold),
            catGap = p.getFloat("catGap", d.catGap),
            useMine = p.getBoolean("useMine", d.useMine),
            useBuiltin = p.getBoolean("useBuiltin", d.useBuiltin),
            model = p.getString("model", d.model) ?: d.model,
            videoDepth = p.getInt("videoDepth", d.videoDepth),
            videoMajority = p.getFloat("videoMajority", d.videoMajority),
            minFileKb = p.getInt("minFileKb", d.minFileKb)
        )
    }

    fun save(s: SortSettings) {
        p.edit()
            .putFloat("thr", s.threshold)
            .putFloat("gap", s.minGap)
            .putBoolean("move", s.move)
            .putString("dest", s.destRoot)
            .putString("excl", s.excludes)
            .putStringSet("backends", s.backends.map { it.name }.toSet())
            .putFloat("catThr", s.catThreshold)
            .putFloat("catGap", s.catGap)
            .putBoolean("useMine", s.useMine)
            .putBoolean("useBuiltin", s.useBuiltin)
            .putString("model", s.model)
            .putInt("videoDepth", s.videoDepth)
            .putFloat("videoMajority", s.videoMajority)
            .putInt("minFileKb", s.minFileKb)
            .apply()
    }
}