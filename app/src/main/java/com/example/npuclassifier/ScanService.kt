package com.example.npuclassifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ScanService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ScanController.stopRequested = true
            Repo.releaseRunning(applicationContext)
            stopSelf()
            return START_NOT_STICKY
        }
        ScanController.stopRequested = false
        Repo.releaseRunning(applicationContext) // resume from crashed state
        val cfg = ScanController.currentCfg
        startForeground(NOTIF_ID, buildNotification("Starting...", 0, 0))
        running = true
        scope.launch {
            try {
                val engine = SortEngine(applicationContext)
                val cats = CategoryStore.load(applicationContext)
                engine.runQueue(applicationContext, cfg, cats)
            } catch (e: Exception) {
                AppLog.e("ScanService", "runQueue failed", e)
            } finally {
                running = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(stage: String, done: Int, total: Int): Notification {
        val stopIntent = Intent(this, ScanService::class.java).apply { action = ACTION_STOP }
        val stopPi = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NPU Sorter")
            .setContentText("$stage $done/$total")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "scan"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.example.npuclassifier.STOP_SCAN"

        fun ensureChannel(ctx: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Scanning", NotificationManager.IMPORTANCE_LOW))
                }
            }
        }
    }
}