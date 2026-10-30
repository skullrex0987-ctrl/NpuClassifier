package com.example.npuclassifier

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenshotTyperTest {
    @Test
    fun payment() { assertEquals("Payment", ScreenshotTyper.type("Paid ₹500 via UPI")) }
    @Test
    fun whatsapp() { assertEquals("WhatsApp", ScreenshotTyper.type("WhatsApp typing... online")) }
    @Test
    fun instagram() { assertEquals("Instagram", ScreenshotTyper.type("Instagram 120 likes")) }
    @Test
    fun maps() { assertEquals("Maps", ScreenshotTyper.type("Google Maps directions 5 km 10 min")) }
    @Test
    fun shopping() { assertEquals("Shopping", ScreenshotTyper.type("Add to cart checkout")) }
    @Test
    fun gmail() { assertEquals("Gmail", ScreenshotTyper.type("Gmail inbox @gmail.com")) }
    @Test
    fun code() { assertEquals("Code", ScreenshotTyper.type("fun main() { }")) }
    @Test
    fun other() { assertEquals("Other", ScreenshotTyper.type("random text here")) }
}