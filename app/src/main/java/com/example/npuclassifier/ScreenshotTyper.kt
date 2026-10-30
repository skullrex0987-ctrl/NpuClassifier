package com.example.npuclassifier

object ScreenshotTyper {
    fun type(ocr: String): String {
        val t = ocr.lowercase()
        if (t.contains("₹") || t.contains("upi") || t.contains("paid") || t.contains("payment")) return "Payment"
        if (t.contains("whatsapp") || t.contains("typing...") || t.contains("online")) return "WhatsApp"
        if (t.contains("instagram") || t.contains("followers") || t.contains("likes")) return "Instagram"
        if (t.contains("directions") || t.contains("km") && t.contains("min") || t.contains("maps")) return "Maps"
        if (t.contains("cart") || t.contains("add to bag") || t.contains("checkout")) return "Shopping"
        if (t.contains("inbox") || t.contains("gmail") || t.contains("@gmail")) return "Gmail"
        if (t.contains("fun ") || t.contains("class ") || t.contains("import ") || t.contains("{")) return "Code"
        return "Other"
    }
}