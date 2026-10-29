package com.example.npuclassifier

import android.net.Uri

enum class Backend { CPU, GPU, NPU }

data class MediaItem(val id: Long = 0, val uri: Uri, val path: String?, val name: String, val isVideo: Boolean)

enum class Status { PENDING, NEEDS_SMART, AUTO, REVIEW, DONE, ERROR, MISSING }

data class SortItem(
    val id: Long = 0,
    val media: MediaItem,
    val label: String = "",
    val conf: Float = 0f,
    val gap: Float = 0f,
    val top: List<Pair<String, Float>> = emptyList(),
    val status: Status = Status.PENDING,
    val backend: Backend? = null,
    val ms: Long = 0,
    val note: String = "",
    val emb: FloatArray? = null,
    val source: String = "",
    val frameItems: List<SortItem>? = null,
    val extraLabels: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val ocr: String = "",
    val thumb: String? = null,
    val destPath: String? = null
)

data class SortSettings(
    val threshold: Float = 0.5f,
    val minGap: Float = 0.08f,
    val move: Boolean = false,
    val destRoot: String = "/storage/emulated/0/Pictures/Sorted",
    val excludes: String = "Sorted,.thumbnails,Android,.trash",
    val backends: Set<Backend> = setOf(Backend.NPU),
    val catThreshold: Float = 0.80f,
    val catGap: Float = 0.03f,
    val useMine: Boolean = true,
    val useBuiltin: Boolean = true,
    val model: String = "clip",
    val videoDepth: Int = 1,
    val videoMajority: Float = 0.5f,
    val minFileKb: Int = 20,
    val scanMode: String = "auto",
    val ocrMode: String = "docs",
    val ocrHindi: String = "auto",
    val tagsOn: Boolean = true,
    val searchModel: String = "clip",
    val facesOn: Boolean = false,
    val faceMatch: Float = 0.65f,
    val indexWhileCharging: Boolean = true
)

/** One file operation, kept so a batch can be undone. */
data class Op(val src: String?, val dest: String, val moved: Boolean)

data class Example(val name: String, val path: String?, val embs: Map<String, FloatArray>)

data class Category(val name: String, val examples: List<Example>, val prompt: String = "")

data class Candidate(val name: String, val source: String, val textEmb: FloatArray)

data class Counts(val total: Int, val pending: Int, val needsSmart: Int, val auto: Int, val review: Int, val done: Int, val error: Int)

data class Filter(val statuses: Set<Status> = emptySet(), val label: String? = null, val latestFirst: Boolean = true)

data class HistoryRow(val id: Long, val batchId: Long, val ts: Long, val srcPath: String?, val destPath: String, val action: String, val label: String, val conf: Float, val source: String, val thumb: String?, val ocrSnippet: String, val tags: String, val status: String)