package com.quizlock.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeTest {
    private val min = 60_000L

    @Test
    fun addsOnlyChosenApps() {
        val marks = listOf(
            UsageMark("insta", true, 0), UsageMark("insta", false, 10 * min),
            UsageMark("maps", true, 10 * min), UsageMark("maps", false, 30 * min),
            UsageMark("insta", true, 30 * min), UsageMark("insta", false, 35 * min),
        )
        assertEquals(15 * min, ScreenTime.foregroundMs(marks, setOf("insta"), 0, 60 * min))
    }

    @Test
    fun appStillOpenCountsTillNow() {
        val marks = listOf(UsageMark("yt", true, 50 * min))
        assertEquals(10 * min, ScreenTime.foregroundMs(marks, setOf("yt"), 0, 60 * min))
    }

    @Test
    fun switchingWithoutPauseEventStillCloses() {
        val marks = listOf(UsageMark("yt", true, 0), UsageMark("home", true, 5 * min))
        assertEquals(5 * min, ScreenTime.foregroundMs(marks, setOf("yt"), 0, 60 * min))
    }

    @Test
    fun daysLeftCountsCalendarDays() {
        val day = 24 * 60 * min
        assertEquals(0, ScreenTime.daysLeft(10 * day, 10 * day))
        assertEquals(180, ScreenTime.daysLeft(10 * day, 190 * day))
        // Ghadi badalne (DST) se ek ghanta aage-peeche ho to bhi same din.
        assertEquals(1, ScreenTime.daysLeft(10 * day, 11 * day - 60 * min))
    }
}
