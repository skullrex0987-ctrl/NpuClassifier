package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileOpsTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun extraLabelsCopiesToMultipleFolders() {
        val c = ctx()
        val tmp = File(c.cacheDir, "fileops_test").apply { mkdirs() }
        val src = File(tmp, "dental.jpg").apply { writeText("xray") }
        val m = MediaItem(uri = android.net.Uri.fromFile(src), path = src.absolutePath, name = "dental.jpg", isVideo = false)
        val cfg = SortSettings(destRoot = File(tmp, "sorted").absolutePath, move = false)
        val ops = FileOps.place(c, m, "Teeth", cfg, listOf("X-ray"))
        assertEquals(2, ops.size)
        val dests = ops.map { it.dest }
        assertTrue(dests.any { it.contains("Teeth") })
        assertTrue(dests.any { it.contains("X-ray") })
        // clean up
        tmp.deleteRecursively()
    }
}