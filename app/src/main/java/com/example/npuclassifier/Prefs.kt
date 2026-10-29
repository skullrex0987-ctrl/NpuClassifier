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
            minFileKb = p.getInt("minFileKb", d.minFileKb),
            scanMode = p.getString("scanMode", d.scanMode) ?: d.scanMode,
            ocrMode = p.getString("ocrMode", d.ocrMode) ?: d.ocrMode,
            ocrHindi = p.getString("ocrHindi", d.ocrHindi) ?: d.ocrHindi,
            tagsOn = p.getBoolean("tagsOn", d.tagsOn),
            searchModel = p.getString("searchModel", d.searchModel) ?: d.searchModel,
            facesOn = p.getBoolean("facesOn", d.facesOn),
            faceMatch = p.getFloat("faceMatch", d.faceMatch),
            indexWhileCharging = p.getBoolean("indexWhileCharging", d.indexWhileCharging)
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
            .putString("scanMode", s.scanMode)
            .putString("ocrMode", s.ocrMode)
            .putString("ocrHindi", s.ocrHindi)
            .putBoolean("tagsOn", s.tagsOn)
            .putString("searchModel", s.searchModel)
            .putBoolean("facesOn", s.facesOn)
            .putFloat("faceMatch", s.faceMatch)
            .putBoolean("indexWhileCharging", s.indexWhileCharging)
            .apply()
    }
}