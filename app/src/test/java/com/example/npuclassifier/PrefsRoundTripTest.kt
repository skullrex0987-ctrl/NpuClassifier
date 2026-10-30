package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefsRoundTripTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun roundTripAllFields() {
        val c = ctx()
        val s = SortSettings(
            threshold = 0.7f, minGap = 0.2f, move = true, destRoot = "/tmp/dest", excludes = "foo,bar",
            backends = setOf(Backend.CPU, Backend.GPU), catThreshold = 0.8f, catGap = 0.3f,
            useMine = false, useBuiltin = true, model = "clip", videoDepth = 2, videoMajority = 0.6f,
            minFileKb = 50, scanMode = "smart", ocrMode = "all", ocrHindi = "on", tagsOn = false,
            searchModel = "mnet", facesOn = true, faceMatch = 0.7f, indexWhileCharging = false
        )
        val prefs = Prefs(c)
        prefs.save(s)
        val loaded = prefs.load()
        assertEquals(s.threshold, loaded.threshold, 0.001f)
        assertEquals(s.minGap, loaded.minGap, 0.001f)
        assertEquals(s.move, loaded.move)
        assertEquals(s.destRoot, loaded.destRoot)
        assertEquals(s.excludes, loaded.excludes)
        assertEquals(s.backends, loaded.backends)
        assertEquals(s.catThreshold, loaded.catThreshold, 0.001f)
        assertEquals(s.catGap, loaded.catGap, 0.001f)
        assertEquals(s.useMine, loaded.useMine)
        assertEquals(s.useBuiltin, loaded.useBuiltin)
        assertEquals(s.model, loaded.model)
        assertEquals(s.videoDepth, loaded.videoDepth)
        assertEquals(s.videoMajority, loaded.videoMajority, 0.001f)
        assertEquals(s.minFileKb, loaded.minFileKb)
        assertEquals(s.scanMode, loaded.scanMode)
        assertEquals(s.ocrMode, loaded.ocrMode)
        assertEquals(s.ocrHindi, loaded.ocrHindi)
        assertEquals(s.tagsOn, loaded.tagsOn)
        assertEquals(s.searchModel, loaded.searchModel)
        assertEquals(s.facesOn, loaded.facesOn)
        assertEquals(s.faceMatch, loaded.faceMatch, 0.001f)
        assertEquals(s.indexWhileCharging, loaded.indexWhileCharging)
    }
}