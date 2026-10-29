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
    val note: String = ""
)

data class SortSettings(
    val threshold: Float = 0.5f,
    val minGap: Float = 0.08f,
    val move: Boolean = false, // default: copy, originals stay put
    val destRoot: String = "/storage/emulated/0/Pictures/Sorted",
    val excludes: String = "Sorted,.thumbnails,Android,.trash",
    val frameIntervalSec: Int = 2,
    val maxFrames: Int = 8,
    val backends: Set<Backend> = setOf(Backend.NPU)
)

/** One file operation, kept so a batch can be undone. */
data class Op(val src: String?, val dest: String, val moved: Boolean)
