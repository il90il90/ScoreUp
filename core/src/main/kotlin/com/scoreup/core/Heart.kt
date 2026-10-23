package com.scoreup.core

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Average and peak heart rate (beats per minute) over one match. */
@Serializable
data class HeartStats(val avg: Int, val max: Int)

/**
 * Collects live heart-rate readings per match, so a saved score can carry the wearer's average
 * and peak. The watch sensor reports at a steady rate, so a plain mean is a fair average.
 */
class HeartTracker(private val keepMatches: Int = 6) {
    private class Acc(var sum: Long = 0, var count: Int = 0, var max: Int = 0)

    private val byMatch = LinkedHashMap<String, Acc>()

    @Synchronized
    fun record(matchId: String, bpm: Int) {
        if (bpm !in MIN_BPM..MAX_BPM) return
        val acc = byMatch.getOrPut(matchId) { Acc() }
        acc.sum += bpm; acc.count++; acc.max = maxOf(acc.max, bpm)
        while (byMatch.size > keepMatches) byMatch.remove(byMatch.keys.first())
    }

    @Synchronized
    fun statsFor(matchId: String): HeartStats? {
        val acc = byMatch[matchId]?.takeIf { it.count > 0 } ?: return null
        return HeartStats((acc.sum.toDouble() / acc.count).roundToInt(), acc.max)
    }

    companion object {
        /** Readings outside this range are sensor noise (no skin contact, warming up). */
        const val MIN_BPM = 30
        const val MAX_BPM = 240
    }
}
