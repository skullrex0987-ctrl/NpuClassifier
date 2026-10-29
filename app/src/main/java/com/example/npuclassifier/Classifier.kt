package com.example.npuclassifier

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * One TFLite interpreter on one backend. NOT thread-safe, and the GPU delegate must be
 * created, used and closed on the SAME thread (SortEngine guarantees this).
 */
class Classifier(context: Context, val backend: Backend) {

    private var nnApi: NnApiDelegate? = null
    private var gpu: GpuDelegate? = null
    private val interpreter: Interpreter
    val labels: List<String> = context.assets.open("labels.txt").bufferedReader().use { it.readLines() }
        .dropLastWhile { it.isBlank() }

    init {
        val opts = Interpreter.Options()
        when (backend) {
            Backend.NPU -> {
                val d = NnApiDelegate(
                    NnApiDelegate.Options().apply {
                        setAllowFp16(true)
                        setExecutionPreference(NnApiDelegate.Options.EXECUTION_PREFERENCE_SUSTAINED_SPEED)
                    }
                )
                nnApi = d
                opts.addDelegate(d)
            }
            Backend.GPU -> {
                val d = GpuDelegate()
                gpu = d
                opts.addDelegate(d)
            }
            Backend.CPU -> opts.setNumThreads(4)
        }
        interpreter = try {
            Interpreter(loadModel(context), opts)
        } catch (e: Exception) {
            nnApi?.close()
            gpu?.close()
            throw e
        }
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
        val buf = ByteBuffer.allocateDirect(w * h * 3 * (if (quant) 1 else 4)).order(ByteOrder.nativeOrder())
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (quant) {
                buf.put(r.toByte()); buf.put(g.toByte()); buf.put(b.toByte())
            } else {
                buf.putFloat((r - 127.5f) / 127.5f)
                buf.putFloat((g - 127.5f) / 127.5f)
                buf.putFloat((b - 127.5f) / 127.5f)
            }
        }
        buf.rewind()
        return buf
    }

    /** Class probabilities (0..1) for one image. */
    fun scores(bitmap: Bitmap): FloatArray {
        val input = prepareInput(bitmap)
        val out = interpreter.getOutputTensor(0)
        val n = out.shape().last()
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

    fun close() {
        interpreter.close()
        nnApi?.close()
        gpu?.close()
    }
}
