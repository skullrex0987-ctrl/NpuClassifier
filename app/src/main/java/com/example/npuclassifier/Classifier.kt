package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

enum class Backend { NPU_NNAPI, CPU }

data class Prediction(val label: String, val score: Float)

class Classifier(context: Context, val backend: Backend) {

    private val nnApi: NnApiDelegate?
    private val interpreter: Interpreter
    private val labels: List<String> =
        context.assets.open("labels.txt").bufferedReader().use { it.readLines() }
            .dropLastWhile { it.isBlank() }

    init {
        val opts = Interpreter.Options()
        var delegate: NnApiDelegate? = null
        if (backend == Backend.NPU_NNAPI) {
            // NNAPI hands the model to the phone's MediaTek driver (APU / NeuroPilot stack).
            delegate = NnApiDelegate(
                NnApiDelegate.Options().apply {
                    setAllowFp16(true)
                    setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED)
                }
            )
            opts.addDelegate(delegate)
        } else {
            opts.setNumThreads(4)
        }
        nnApi = delegate
        interpreter = try {
            Interpreter(loadModel(context), opts)
        } catch (e: Exception) {
            delegate?.close()
            throw e
        }
    }

    /** Non-null if labels.txt does not match the model's output size. */
    val labelNote: String?
        get() {
            val n = interpreter.getOutputTensor(0).shape().last()
            return if (n != labels.size) "WARNING: model has $n outputs but labels.txt has ${labels.size} lines" else null
        }

    private fun loadModel(context: Context): ByteBuffer =
        context.assets.openFd("model.tflite").use { fd ->
            FileInputStream(fd.fileDescriptor).use { s ->
                s.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }

    private fun prepareInput(bitmap: Bitmap): ByteBuffer {
        val t = interpreter.getInputTensor(0)
        val shape = t.shape()
        require(shape.size == 4 && shape[3] == 3) { "Unexpected input shape: ${shape.toList()}" }
        val h = shape[1]
        val w = shape[2]
        val quant = t.dataType() == DataType.UINT8
        require(quant || t.dataType() == DataType.FLOAT32) { "Unsupported input type: ${t.dataType()}" }
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        val px = IntArray(w * h)
        scaled.getPixels(px, 0, w, 0, 0, w, h)
        val buf = ByteBuffer.allocateDirect(w * h * 3 * (if (quant) 1 else 4))
            .order(ByteOrder.nativeOrder())
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (quant) {
                buf.put(r.toByte()); buf.put(g.toByte()); buf.put(b.toByte())
            } else { // float models usually expect [-1, 1]; change if yours differs
                buf.putFloat((r - 127.5f) / 127.5f)
                buf.putFloat((g - 127.5f) / 127.5f)
                buf.putFloat((b - 127.5f) / 127.5f)
            }
        }
        buf.rewind()
        return buf
    }

    private fun infer(input: ByteBuffer): FloatArray {
        val out = interpreter.getOutputTensor(0)
        val n = out.shape().last()
        input.rewind()
        return if (out.dataType() == DataType.UINT8) {
            val o = Array(1) { ByteArray(n) }
            interpreter.run(input, o)
            FloatArray(n) { (o[0][it].toInt() and 0xFF) / 255f }
        } else {
            val o = Array(1) { FloatArray(n) }
            interpreter.run(input, o)
            o[0]
        }
    }

    /** Returns top-K predictions and the average inference time in ms. */
    fun classify(bitmap: Bitmap, topK: Int = 3, runs: Int = 30): Pair<List<Prediction>, Double> {
        val input = prepareInput(bitmap)
        repeat(5) { infer(input) } // warm-up (first NPU run compiles the model)
        var scores = FloatArray(0)
        val start = System.nanoTime()
        repeat(runs) { scores = infer(input) }
        val ms = (System.nanoTime() - start) / 1e6 / runs
        val top = scores.indices.sortedByDescending { scores[it] }.take(topK)
            .map { Prediction(labels.getOrElse(it) { "class $it" }, scores[it]) }
        return top to ms
    }

    fun close() {
        interpreter.close()
        nnApi?.close()
    }
}
