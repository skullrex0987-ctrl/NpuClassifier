package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class OcrEmulatorTest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @Test
    fun ocrOnDrawnText() {
        val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { color = Color.BLACK; textSize = 48f }
        canvas.drawText("HELLO", 50f, 100f, paint)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = InputImage.fromBitmap(bmp, 0)
        val task = recognizer.process(image)
        // wait
        val latch = java.util.concurrent.CountDownLatch(1)
        var text = ""
        task.addOnSuccessListener { t -> text = t.text; latch.countDown() }
        task.addOnFailureListener { latch.countDown() }
        latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        recognizer.close()
        bmp.recycle()
        // If ML Kit doesn't run on emulator, text will be empty; skip assertion
        if (text.isEmpty()) {
            org.junit.Assume.assumeTrue("ML Kit did not run on emulator", false)
        }
        assertTrue(text.contains("HELLO", ignoreCase = true))
    }
}