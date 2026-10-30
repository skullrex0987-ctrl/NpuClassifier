package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StopTest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun stopWithin3Seconds() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        // create 500 small images
        val dir = File(ctx.cacheDir, "stoptest").apply { mkdirs() }
        val items = (1..500).map { i ->
            val f = File(dir, "img$i.jpg")
            if (!f.exists()) {
                val bmp = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
                java.io.FileOutputStream(f).use { out -> bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out) }
                bmp.recycle()
            }
            MediaItem(uri = android.net.Uri.fromFile(f), path = f.absolutePath, name = f.name, isVideo = false)
        }
        Repo.upsertPending(ctx, items, System.currentTimeMillis())
        val cfg = SortSettings(scanMode = "fast", backends = setOf(Backend.CPU))
        ScanController.start(ctx, cfg)
        Thread.sleep(1000)
        ScanController.stop(ctx)
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 3000) {
            if (!ScanController.state.value.running) break
            Thread.sleep(100)
        }
        assertTrue("scan did not stop within 3s", !ScanController.state.value.running)
        val counts = Repo.counts(ctx)
        assertTrue("rows stayed RUNNING_*", counts.running == 0)
    }
}