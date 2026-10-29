package com.example.npuclassifier

import org.json.JSONObject
import java.io.InputStream

object ClipTokenizer {
    private const val SOT = 49406
    private const val EOT = 49407
    private const val MAXLEN = 77

    private var vocab: Map<String, Int> = emptyMap()
    private var merges: Map<String, Int> = emptyMap() // "a b" -> rank
    private var byteEncoder: Map<Int, Char> = emptyMap()
    private var byteDecoder: Map<Char, Int> = emptyMap()
    private var loaded = false

    fun load(input: InputStream) {
        val root = JSONObject(input.bufferedReader().readText())
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

        // GPT-2 byte encoder
        val be = mutableMapOf<Int, Char>()
        val bd = mutableMapOf<Char, Int>()
        val bs = mutableListOf<Int>()
        for (i in '!'.code..'~'.code) bs.add(i)
        for (i in 0xa1..0xac) bs.add(i)
        for (i in 0xae..0xff) bs.add(i)
        var n = 0
        for (i in 0..255) {
            if (i !in bs) {
                val c = (256 + n).toChar()
                be[i] = c
                bd[c] = i
                n++
            } else {
                be[i] = i.toChar()
                bd[i.toChar()] = i
            }
        }
        byteEncoder = be
        byteDecoder = bd
        loaded = true
    }

    fun encode(text: String): LongArray {
        if (!loaded) throw IllegalStateException("Tokenizer not loaded")
        val cleaned = text.lowercase().trim().replace(Regex("\\s+"), " ")
        val tokens = mutableListOf<String>()
        // Split with regex
        val pattern = Regex("""<\|startoftext\|>|<\|endoftext\|>|'s|'t|'re|'ve|'m|'ll|'d|\p{L}+|\p{N}|[^\s\p{L}\p{N}]+""")
        for (match in pattern.findAll(cleaned)) {
            tokens.add(match.value)
        }

        val bpeTokens = mutableListOf<String>()
        for (token in tokens) {
            // bytes -> unicode chars
            val bytes = token.toByteArray(Charsets.UTF_8)
            val chars = StringBuilder()
            for (b in bytes) {
                chars.append(byteEncoder[(b.toInt() and 0xFF)] ?: '?')
            }
            var word = chars.toString()
            if (word.isNotEmpty()) {
                // append </w> to last char
                word = word.dropLast(1) + word.last() + "</w>"
            }
            // apply BPE
            val pieces = bpe(word)
            bpeTokens.addAll(pieces)
        }

        // map pieces to ids
        val ids = mutableListOf<Int>()
        for (piece in bpeTokens) {
            ids.add(vocab[piece] ?: vocab["<|endoftext|>"] ?: 0)
        }

        // ids = [49406] + ids + [49407], cut to 77, pad with 0
        val result = mutableListOf<Int>()
        result.add(SOT)
        result.addAll(ids)
        result.add(EOT)
        // cut to 77
        while (result.size > MAXLEN) result.removeAt(result.size - 1)
        // pad with 0
        while (result.size < MAXLEN) result.add(0)

        return LongArray(MAXLEN) { result[it].toLong() }
    }

    private fun bpe(word: String): List<String> {
        if (word.isEmpty()) return emptyList()
        var wordChars = word.map { it.toString() }.toMutableList()
        // Apply merges
        while (wordChars.size > 1) {
            // find best merge
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
            // merge
            val merged = wordChars[bestIdx] + wordChars[bestIdx + 1]
            wordChars = wordChars.subList(0, bestIdx).toMutableList() +
                    mutableListOf(merged) +
                    wordChars.subList(bestIdx + 2, wordChars.size).toMutableList()
        }
        return wordChars
    }
}
XML
echo "Created ClipTokenizer.kt"