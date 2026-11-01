package com.quizlock.app

import kotlin.math.ceil

/**
 * Walk-to-Unlock ki ginti. Step sensor kul kadam (boot se) deta hai; ye class
 * shuru ke baad ke kadam ginti hai, par ek second mein [maxPerSecond] se zyada
 * nahi. Phone hilane par sensor bahut tez kadam deta hai, wo kat jaate hain.
 * Android se alag, taki JVM test chal sake.
 */
class StepGate(val target: Int, private val maxPerSecond: Double = 2.5) {
    var counted = 0
        private set
    private var lastRaw: Float? = null
    private var lastAt = 0L

    val done get() = counted >= target

    /** Sensor ki nayi reading. Pehli reading sirf shuruaat maani jaati hai. */
    fun onReading(raw: Float, atMs: Long) {
        val prev = lastRaw
        if (prev == null || raw < prev) {
            lastRaw = raw
            lastAt = atMs
            return
        }
        val delta = (raw - prev).toInt()
        if (delta <= 0) return
        val seconds = (atMs - lastAt).coerceAtLeast(0) / 1000.0
        val allowed = ceil(seconds * maxPerSecond).toInt()
        counted = (counted + minOf(delta, allowed)).coerceAtMost(target)
        lastRaw = raw
        lastAt = atMs
    }

    /** Call ya screen chhupne ke baad: beech ke kadam mat gino. */
    fun pause() {
        lastRaw = null
    }
}
