package com.example.npuclassifier

object ProgressMath {
    fun stage(done: Int, total: Int): Pair<Int, Int> {
        val t = if (total < 1) 1 else total
        val d = done.coerceIn(0, t)
        return d to t
    }
    fun reset(): Pair<Int, Int> = 0 to 0
}