package com.example.npuclassifier

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressMathTest {
    @Test
    fun doneNeverExceedsTotal() {
        assertEquals(5 to 5, ProgressMath.stage(10, 5))
        assertEquals(3 to 5, ProgressMath.stage(3, 5))
        assertEquals(0 to 1, ProgressMath.stage(0, 0))
    }
    @Test
    fun resetClears() {
        assertEquals(0 to 0, ProgressMath.reset())
    }
}