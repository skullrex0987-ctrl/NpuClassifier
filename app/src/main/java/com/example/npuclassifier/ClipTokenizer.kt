package com.example.npuclassifier

import android.content.Context
import org.json.JSONObject

class ClipTokenizer(ctx: Context) {
    private val SOT = 49406
    private val EOT = 49407
    private val MAXLEN = 77

    private var vocab: Map<String, Int> = emptyMap()
    private var merges: Map<String, Int> = emptyMap()
    private var byteEncoder: Map<Int, Char> = emptyMap()

    init {
        val input = ctx.assets.open("clip/tokenizer.json")
        val root = JSONObject(input.bufferedReader().readText())
        input.close()
        val vocabObj = root.getJSONObject("model").getJSONObject("vocab")
        val v = mutableMapOf<String, Int>()
        val keys = vocabObj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            v[k] = vocabObj.getInt(k)
        }
        vocab = v

        val mergesArr = root.getJSONObject("model").getJSONArray("merges")
        val m = mutableMapOf<String, Int>()
        for (i in 0 until mergesArr.length()) {
            val item = mergesArr.get(i)
            val pair = when (item) {
                is String -> item
                is org.json.JSONArray -> "${item.getString(0)} ${item.getString(1)}"
                else -> item.toString()
            }
            m[pair] = i
        }
        merges = m

        val be = mutableMapOf<Int, Char>()
        val bs = mutableListOf<Int>()
        for (i in '!'.code..'~'.code) bs.add(i)
        for (i in 0xa1..0xac) bs.add(i)
        for (i in 0xae..0xff) bs.add(i)
        var n = 0
        for (i in 0..255) {
            if (i !in bs) {
                be[i] = (256 + n).toChar()
                n++
            } else {
                be[i] = i.toChar()
            }
        }
        byteEncoder = be
    }

    fun encode(text: String): LongArray {
        val cleaned = text.lowercase().trim().replace(Regex("\\s+"), " ")
        val tokens = Regex("""<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|\p{L}+|\p{N}|[^\s\p{L}\p{N}]+""").findAll(cleaned).map { it.value }.toList()

        val bpeTokens = mutableListOf<String>()
        for (token in tokens) {
            val bytes = token.toByteArray(Charsets.UTF_8)
            val chars = StringBuilder()
            for (b in bytes) {
                chars.append(byteEncoder[(b.toInt() and 0xFF)] ?: '?')
            }
            val word = chars.toString()
            val pieces = bpe(word)
            if (pieces.isNotEmpty()) {
                pieces[pieces.size - 1] = pieces[pieces.size - 1] + "</w>"
            }
            bpeTokens.addAll(pieces)
        }

        val ids = mutableListOf<Int>()
        for (piece in bpeTokens) {
            ids.add(vocab[piece] ?: 0)
        }

        val result = mutableListOf<Int>()
        result.add(SOT)
        result.addAll(ids)
        result.add(EOT)
        while (result.size > MAXLEN) result.removeAt(result.size - 1)
        while (result.size < MAXLEN) result.add(0)

        return LongArray(MAXLEN) { result[it].toLong() }
    }

    private fun bpe(word: String): MutableList<String> {
        if (word.isEmpty()) return mutableListOf()
        var wordChars = word.map { it.toString() }.toMutableList()
        while (wordChars.size > 1) {
            var bestRank = Int.MAX_VALUE
            var bestIdx = -1
            for (i in 0 until wordChars.size - 1) {
                val pair = "${wordChars[i]} ${wordChars[i + 1]}"
                val rank = merges[pair]
                if (rank != null && rank < bestRank) {
                    bestRank = rank
                    bestIdx = i
                }
            }
            if (bestIdx == -1) break
            val merged = wordChars[bestIdx] + wordChars[bestIdx + 1]
            val newChars = wordChars.subList(0, bestIdx).toMutableList()
            newChars.add(merged)
            newChars.addAll(wordChars.subList(bestIdx + 2, wordChars.size))
            wordChars = newChars
        }
        return wordChars
    }
}