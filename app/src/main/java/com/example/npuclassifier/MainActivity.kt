package com.example.npuclassifier

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var pickBtn: Button
    private lateinit var image: ImageView
    private lateinit var result: TextView
    private val classifiers = mutableMapOf<Backend, Classifier>()
    private val lock = Any() // one inference job at a time; also guards close()

    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) run(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        pickBtn = Button(this).apply { text = "Pick image"; setOnClickListener { picker.launch("image/*") } }
        image = ImageView(this).apply { adjustViewBounds = true; maxHeight = (300 * d).toInt() }
        result = TextView(this).apply { textSize = 16f; text = "Pick an image to classify on NPU and CPU." }
        root.addView(pickBtn); root.addView(image); root.addView(result)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun run(uri: Uri) {
        pickBtn.isEnabled = false
        result.text = "Running..."
        Thread {
            val text = synchronized(lock) {
                try {
                    val bmp: Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { dec, info, _ ->
                        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val m = maxOf(info.size.width, info.size.height)
                        if (m > 1024) { // avoid decoding huge photos at full size
                            val f = 1024f / m
                            dec.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                        }
                    }
                    runOnUiThread { image.setImageBitmap(bmp) }
                    Backend.values().joinToString("\n\n") { b ->
                        try {
                            val c = classifiers.getOrPut(b) { Classifier(this, b) }
                            val (preds, ms) = c.classify(bmp)
                            "$b  (${"%.2f".format(ms)} ms/inference)\n" +
                                preds.joinToString("\n") { "  ${it.label}: ${"%.1f".format(it.score * 100)}%" } +
                                (c.labelNote?.let { "\n  $it" } ?: "")
                        } catch (e: Throwable) {
                            "$b failed: ${e.message}"
                        }
                    }
                } catch (e: Throwable) {
                    "Error: ${e.message}"
                }
            }
            runOnUiThread { result.text = text; pickBtn.isEnabled = true }
        }.start()
    }

    override fun onDestroy() {
        synchronized(lock) {
            classifiers.values.forEach { it.close() }
            classifiers.clear()
        }
        super.onDestroy()
    }
}
