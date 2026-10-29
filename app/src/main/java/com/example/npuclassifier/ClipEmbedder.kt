package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer

class ClipEmbedder(ctx: Context, val backend: Backend) : Embedder {
    private val env = OrtEnvironment.getEnvironment()
    private val visionSession: OrtSession
    private val textSession: OrtSession
    private val tokenizer = ClipTokenizer(ctx)
    private val textCache = mutableMapOf<String, FloatArray>()

    override val id: String = "clip"

    init {
        val visionFile = ModelFiles.ensure(ctx, "clip/vision.onnx")
        val textFile = ModelFiles.ensure(ctx, "clip/text.onnx")
        val opts = OrtSession.SessionOptions()
        when (backend) {
            Backend.NPU -> {
                try {
                    opts.addNnapi()
                } catch (e: Exception) {
                    // fall back to CPU
                }
            }
            Backend.CPU -> opts.setIntraOpNumThreads(4)
            Backend.GPU -> {} // not supported for CLIP
        }
        visionSession = env.createSession(visionFile.absolutePath, opts)
        textSession = env.createSession(textFile.absolutePath, opts)
    }

    override fun embedImage(bmp: Bitmap): FloatArray {
        // preprocess: resize shortest side to 224, center crop 224x224, normalize
        val w = bmp.width
        val h = bmp.height
        val scale = 224f / minOf(w, h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(bmp, newW, newH, true)
        val cropX = (newW - 224) / 2
        val cropY = (newH - 224) / 2
        val cropped = Bitmap.createBitmap(scaled, cropX, cropY, 224, 224)

        val mean = floatArrayOf(0.48145466f, 0.4578275f, 0.40821073f)
        val std = floatArrayOf(0.26862954f, 0.26130258f, 0.27577711f)

        val buf = FloatBuffer.allocate(3 * 224 * 224)
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

        val inputName = visionSession.inputNames.first()
        val outputName = visionSession.outputNames.first()
        val tensor = OnnxTensor.createTensor(env, buf, longArrayOf(1, 3, 224, 224))
        val result = visionSession.run(mapOf(inputName to tensor))
        val output = result.get(outputName).get()
        val raw = (output.value as Array<*>)[0] as FloatArray
        tensor.close()
        result.close()
        return l2Normalize(raw)
    }

    override fun embedText(text: String): FloatArray? {
        val prompt = "a photo of $text"
        textCache[prompt]?.let { return it }
        return try {
            val ids = tokenizer.encode(prompt)
            val inputName = textSession.inputNames.first()
            val outputName = textSession.outputNames.first()
            val input = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(ids), longArrayOf(1, 77))
            val result = textSession.run(mapOf(inputName to input))
            val output = result.get(outputName).get()
            val raw = (output.value as Array<*>)[0] as FloatArray
            input.close()
            result.close()
            val emb = l2Normalize(raw)
            textCache[prompt] = emb
            emb
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        visionSession.close()
        textSession.close()
    }

    private fun l2Normalize(v: FloatArray): FloatArray {
        var norm = 0f
        for (x in v) norm += x * x
        norm = kotlin.math.sqrt(norm)
        if (norm < 1e-8f) return v
        return FloatArray(v.size) { v[it] / norm }
    }
}