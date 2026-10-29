package com.example.npuclassifier

import android.net.Uri

enum class Backend { CPU, GPU, NPU }

data class MediaItem(val uri: Uri, val path: String?, val name: String, val isVideo: Boolean)

enum class Status { PENDING, AUTO, REVIEW, DONE, ERROR }

data class SortItem(
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
    val source: String = ""
)

data class SortSettings(
    val threshold: Float = 0.5f,
    val minGap: Float = 0.08f,
    val move: Boolean = false,
    val destRoot: String = "/storage/emulated/0/Pictures/Sorted",
    val excludes: String = "Sorted,.thumbnails,Android,.trash",
    val frameIntervalSec: Int = 2,
    val maxFrames: Int = 8,
    val backends: Set<Backend> = setOf(Backend.NPU),
    val catThreshold: Float = 0.80f,
    val catGap: Float = 0.03f,
    val useMine: Boolean = true,
    val useBuiltin: Boolean = true
)

/** One file operation, kept so a batch can be undone. */
data class Op(val src: String?, val dest: String, val moved: Boolean)

data class Example(val name: String, val path: String?, val emb: FloatArray)

data class Category(val name: String, val examples: List<Example>)