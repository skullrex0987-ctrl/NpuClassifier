package com.example.npuclassifier

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object CategoryStore {
    private fun file(ctx: Context): File = File(ctx.filesDir, "categories.json")

    fun load(ctx: Context): List<Category> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val name = o.getString("name")
                val exArr = o.getJSONArray("examples")
                val examples = (0 until exArr.length()).map { j ->
                    val e = exArr.getJSONObject(j)
                    val embBytes = Base64.decode(e.getString("emb"), Base64.DEFAULT)
                    val emb = FloatArray(embBytes.size / 4)
                    java.nio.ByteBuffer.wrap(embBytes).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer().get(emb)
                    Example(e.getString("name"), e.optString("path", null), emb)
                }
                Category(name, examples)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(ctx: Context, cats: List<Category>) {
        val arr = JSONArray()
        for (cat in cats) {
            val o = JSONObject()
            o.put("name", cat.name)
            val exArr = JSONArray()
            for (ex in cat.examples) {
                val e = JSONObject()
                e.put("name", ex.name)
                ex.path?.let { e.put("path", it) }
                val buf = java.nio.ByteBuffer.allocate(ex.emb.size * 4).order(java.nio.ByteOrder.nativeOrder())
                buf.asFloatBuffer().put(ex.emb)
                e.put("emb", Base64.encodeToString(buf.array(), Base64.DEFAULT))
                exArr.put(e)
            }
            o.put("examples", exArr)
            arr.put(o)
        }
        file(ctx).writeText(arr.toString())
    }
}