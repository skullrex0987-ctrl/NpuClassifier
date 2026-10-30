package com.example.npuclassifier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterRulesTest {
    @Test
    fun dentalTeeth() {
        val m = StarterRules.match("dental.jpg", "/dental.jpg", "dental report", emptyList(), 100, 100, null)
        assertEquals("Teeth", m?.label)
    }
    @Test
    fun chestXray() {
        val m = StarterRules.match("chest.jpg", "/chest.jpg", "chest x-ray", emptyList(), 100, 100, null)
        assertEquals("X-ray", m?.label)
    }
    @Test
    fun dentalXrayExtra() {
        val m = StarterRules.match("dental.jpg", "/dental.jpg", "dental x-ray", emptyList(), 100, 100, null)
        assertEquals("Teeth", m?.label)
        assertTrue(m?.extra?.contains("X-ray") == true)
    }
    @Test
    fun screenshotByFileName() {
        val m = StarterRules.match("Screenshot_1.png", "/Screenshot_1.png", "hello world", emptyList(), 100, 200, null)
        assertTrue(m?.label?.startsWith("Screenshots/") == true)
    }
    @Test
    fun personTagsHumans() {
        val m = StarterRules.match("a.jpg", "/a.jpg", "", listOf("person"), 100, 100, null)
        assertEquals("Humans", m?.label)
    }
    @Test
    fun dogTagsAnimals() {
        val m = StarterRules.match("a.jpg", "/a.jpg", "", listOf("dog"), 100, 100, null)
        assertEquals("Animals", m?.label)
    }
    @Test
    fun humansWinOverAnimals() {
        val m = StarterRules.match("a.jpg", "/a.jpg", "", listOf("person", "dog"), 100, 100, null)
        assertEquals("Humans", m?.label)
    }
}