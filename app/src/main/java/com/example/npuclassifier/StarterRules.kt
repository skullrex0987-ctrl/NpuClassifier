package com.example.npuclassifier

data class StarterMatch(val label: String, val extra: List<String> = emptyList())

object StarterRules {
    fun match(name: String, path: String?, ocr: String, tags: List<String>, w: Int, h: Int, smartProbs: Map<String, Float>?): StarterMatch? {
        val low = (name + " " + (path ?: "") + " " + ocr).lowercase()
        val tagLow = tags.map { it.lowercase() }
        // Humans win over Animals
        if (tagLow.any { it.contains("person") || it.contains("human") || it.contains("face") }) {
            return StarterMatch("Humans")
        }
        if (tagLow.any { it.contains("dog") || it.contains("cat") || it.contains("animal") }) {
            return StarterMatch("Animals")
        }
        if (low.contains("dental") || low.contains("teeth") || low.contains("tooth")) {
            val extra = if (low.contains("x-ray") || low.contains("xray")) listOf("X-ray") else emptyList()
            return StarterMatch("Teeth", extra)
        }
        if (low.contains("x-ray") || low.contains("xray") || low.contains("chest")) {
            return StarterMatch("X-ray")
        }
        // screenshot by file name
        if (name.lowercase().contains("screenshot") || name.lowercase().contains("screen shot")) {
            val type = ScreenshotTyper.type(ocr)
            return StarterMatch("Screenshots/$type")
        }
        // screenshot by aspect ratio + text (tall portrait with text)
        if (w > 0 && h > 0 && h > w * 1.5 && ocr.length > 20) {
            val type = ScreenshotTyper.type(ocr)
            return StarterMatch("Screenshots/$type")
        }
        return null
    }
}