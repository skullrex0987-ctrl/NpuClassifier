package com.example.npuclassifier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EnricherInjectableTest {
    private fun ctx(): Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fakeEnricherIsInjectable() {
        val c = ctx()
        var callCount = 0
        val fake = object : EnricherApi {
            override fun run(ctx: Context, m: MediaItem, cfg: SortSettings, wantOcr: Boolean, wantTags: Boolean): Enrichment {
                callCount++
                return Enrichment("fake ocr", listOf("fake tag"))
            }
        }
        val engine = SortEngine(c, fake)
        assertTrue(engine != null)
        // Verify the fake returns expected values
        val enr = fake.run(c, MediaItem(uri = android.net.Uri.parse("file:///x.jpg"), path = "/x.jpg", name = "x.jpg", isVideo = false), SortSettings(), true, true)
        assertEquals("fake ocr", enr.ocr)
        assertEquals(listOf("fake tag"), enr.tags)
        assertEquals(1, callCount)
    }
}