package com.example.npuclassifier

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class ScanState(val running: Boolean = false, val stage: String = "idle", val done: Int = 0, val total: Int = 0, val perSec: Float = 0f, val message: String? = null)

object ScanController {
    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state
    var currentCfg: SortSettings = SortSettings()
    @Volatile
    var stopRequested: Boolean = false

    fun start(ctx: Context, cfg: SortSettings) {
        stopRequested = false
        currentCfg = cfg
        ScanService.ensureChannel(ctx)
        val i = Intent(ctx, ScanService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            ctx.startForegroundService(i)
        } else {
            ctx.startService(i)
        }
        _state.value = ScanState(running = true, stage = "fast", message = "Starting...")
    }
    fun stop(ctx: Context) {
        stopRequested = true
        Repo.releaseRunning(ctx)
        ctx.stopService(Intent(ctx, ScanService::class.java))
        _state.value = ScanState(running = false, stage = "idle", message = "Stopped")
    }
    fun startIndex(ctx: Context, cfg: SortSettings) { }
    fun update(s: ScanState) { _state.value = s }
}