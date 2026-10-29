package com.example.npuclassifier

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
                val prompt = o.optString("prompt", "")
                val exArr = o.getJSONArray("examples")
                val examples = (0 until exArr.length()).map { j ->
                    val e = exArr.getJSONObject(j)
                    val exName = e.getString("name")
                    val path = e.optString("path", null)
                    val embs = mutableMapOf<String, FloatArray>()
                    // New format: embs object
                    if (e.has("embs")) {
                        val embsObj = e.getJSONObject("embs")
                        val keys = embsObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val embBytes = Base64.decode(embsObj.getString(k), Base64.DEFAULT)
                            val emb = FloatArray(embBytes.size / 4)
                            ByteBuffer.wrap(embBytes).order(ByteOrder.nativeOrder()).asFloatBuffer().get(emb)
                            embs[k] = emb
                        }
                    }
                    // Migration: old single "emb" -> embs["mnet"]
                    if (embs.isEmpty() && e.has("emb")) {
                        val embBytes = Base64.decode(e.getString("emb"), Base64.DEFAULT)
                        val emb = FloatArray(embBytes.size / 4)
                        ByteBuffer.wrap(embBytes).order(ByteOrder.nativeOrder()).asFloatBuffer().get(emb)
                        embs["mnet"] = emb
                    }
                    Example(exName, path, embs)
                }
                Category(name, examples, prompt)
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
            if (cat.prompt.isNotEmpty()) o.put("prompt", cat.prompt)
            val exArr = JSONArray()
            for (ex in cat.examples) {
                val e = JSONObject()
                e.put("name", ex.name)
                ex.path?.let { e.put("path", it) }
                val embsObj = JSONObject()
                for ((key, emb) in ex.embs) {
                    val buf = ByteBuffer.allocate(emb.size * 4).order(ByteOrder.nativeOrder())
                    buf.asFloatBuffer().put(emb)
                    embsObj.put(key, Base64.encodeToString(buf.array(), Base64.DEFAULT))
                }
                e.put("embs", embsObj)
                exArr.put(e)
            }
            o.put("examples", exArr)
            arr.put(o)
        }
        file(ctx).writeText(arr.toString())
    }
}