package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer

class ClipClassifier(context: Context) {
    private val env = OrtEnvironment.getEnvironment()
    private val visionSession: OrtSession
    private val textSession: OrtSession
    private val visionInputName: String
    private val textInputName: String
    private val visionOutputName: String
    private val textOutputName: String

    init {
        val visionBytes = context.assets.open("clip/vision.onnx").readBytes()
        val textBytes = context.assets.open("clip/text.onnx").readBytes()
        visionSession = env.createSession(visionBytes, OrtSession.SessionOptions())
        textSession = env.createSession(textBytes, OrtSession.SessionOptions())
        visionInputName = visionSession.inputNames.first()
        textInputName = textSession.inputNames.first()
        visionOutputName = visionSession.outputNames.first { it.contains("image_embeds") || it.contains("embed") } ?: visionSession.outputNames.first()
        textOutputName = textSession.outputNames.first { it.contains("text_embeds") || it.contains("embed") } ?: textSession.outputNames.first()
    }

    /** Encode an image to a 512-d L2-normalized embedding. */
    fun imageEmbed(bitmap: Bitmap): FloatArray {
        val input = preprocessImage(bitmap)
        val tensor = OnnxTensor.createTensor(env, input, longArrayOf(1, 3, 224, 224))
        val result = visionSession.run(mapOf(visionInputName to tensor))
        val output = result.get(visionOutputName)
        val raw = when (val v = output.value) {
            is Array<*> -> {
                val arr = v[0] as FloatArray
                arr
            }
            is FloatArray -> v
            else -> throw IllegalStateException("Unexpected output type: ${v?.javaClass}")
        }
        tensor.close()
        result.close()
        return l2Normalize(raw)
    }

    /** Encode text to a 512-d L2-normalized embedding. */
    fun textEmbed(text: String): FloatArray {
        val ids = ClipTokenizer.encode(text)
        val input = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(ids), longArrayOf(1, 77))
        val result = textSession.run(mapOf(textInputName to input))
        val output = result.get(textOutputName)
        val raw = when (val v = output.value) {
            is Array<*> -> {
                val arr = v[0] as FloatArray
                arr
            }
            is FloatArray -> v
            else -> throw IllegalStateException("Unexpected output type: ${v?.javaClass}")
        }
        input.close()
        result.close()
        return l2Normalize(raw)
    }

    private fun preprocessImage(bitmap: Bitmap): FloatBuffer {
        // Resize shortest side to 224, center-crop 224x224
        val w = bitmap.width
        val h = bitmap.height
        val scale = 224f / minOf(w, h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val cropX = (newW - 224) / 2
        val cropY = (newH - 224) / 2
        val cropped = Bitmap.createBitmap(scaled, cropX, cropY, 224, 224)

        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        val buf = FloatBuffer.allocate(3 * 224 * 224)
        // CHW format
        val pixels = IntArray(224 * 224)
        cropped.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        for (c in 0 until 3) {
            for (y in 0 until 224) {
                for (x in 0 until 224) {
                    val p = pixels[y * 224 + x]
                    val v = when (c) {
                        0 -> ((p shr 16) and 0xFF) / 255f
                        1 -> ((p shr 8) and 0xFF) / 255f
                        else -> (p and 0xFF) / 255f
                    }
                    buf.put((v - mean[c]) / std[c])
                }
            }
        }
        buf.rewind()
        return buf
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = kotlin.math.sqrt(norm)
        if (norm < 1e-8f) return v
        return FloatArray(v.size) { v[it] / norm }
    }

    fun close() {
        visionSession.close()
        textSession.close()
    }
}
XML
echo "Created ClipClassifier.kt"