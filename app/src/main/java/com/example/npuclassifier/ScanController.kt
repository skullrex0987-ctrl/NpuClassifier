package com.example.npuclassifier

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScanState(val running: Boolean = false, val stage: String = "idle", val done: Int = 0, val total: Int = 0, val perSec: Float = 0f, val message: String? = null)

object ScanController {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state
    fun start(ctx: Context, cfg: SortSettings) { }
    fun stop(ctx: Context) { }
    fun startIndex(ctx: Context, cfg: SortSettings) { }
}

class ScanService : android.app.Service() {
    override fun onBind(intent: Intent?): android.os.IBinder? = null
}

object AppLog {
    fun d(tag: String, msg: String) { }
    fun e(tag: String, msg: String, t: Throwable? = null) { }
    fun tail(n: Int): List<String> = emptyList()
}