package com.quizlock.app

/** Ek usage event: kaunsa app, saamne aaya (true) ya hata (false), kab. */
data class UsageMark(val pkg: String, val resumed: Boolean, val at: Long)

/**
 * Aaj chune hue apps kitni der saamne rahe. Events se jod kar ginta hai,
 * kyunki Android ke daily totals mein kal ka hissa bhi aa jaata hai.
 */
object ScreenTime {
    fun foregroundMs(marks: List<UsageMark>, apps: Set<String>, from: Long, now: Long): Long {
        var total = 0L
        var openPkg: String? = null
        var openSince = 0L
        for (m in marks.sortedBy { it.at }) {
            if (m.resumed) {
                if (openPkg != null) total += m.at - openSince
                openPkg = if (m.pkg in apps) m.pkg else null
                openSince = maxOf(m.at, from)
            } else if (m.pkg == openPkg) {
                total += m.at - openSince
                openPkg = null
            }
        }
        if (openPkg != null) total += now - openSince
        return total.coerceAtLeast(0)
    }

    /** "NEET 2027" ke din: aaj se exam ke din tak (aaj ko gine bina). */
    fun daysLeft(todayStart: Long, examDayStart: Long): Long =
        Math.floorDiv(examDayStart - todayStart + 12 * 3600_000L, 24 * 3600_000L)
}
