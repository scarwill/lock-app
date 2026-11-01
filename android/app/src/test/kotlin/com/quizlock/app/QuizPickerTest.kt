package com.quizlock.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class QuizPickerTest {
    private val bank = (1..5).map {
        Question("q$it", if (it <= 2) "maths" else "gk", "Q$it", listOf("a$it", "b$it", "c$it", "d$it"), 1, "")
    }
    private val picker = QuizPicker(bank, random = Random(42))

    @Test
    fun optionsShuffleButAnswerStaysCorrect() {
        repeat(50) {
            val s = picker.next(bank, QuizHistory(), 0)
            assertEquals(s.q.options[s.q.answer], s.options[s.answer])
            assertEquals(s.q.options.toSet(), s.options.toSet())
        }
    }

    @Test
    fun neverRepeatsCurrentQuestionAfterWrongAnswer() {
        val h = QuizHistory()
        var prev = picker.next(bank, h, 0)
        repeat(100) {
            val s = picker.next(bank, h, 0, prev.q.id)
            assertNotEquals(prev.q.id, s.q.id)
            prev = s
        }
    }

    @Test
    fun recentQuestionsAreSkipped() {
        val h = QuizHistory()
        repeat(100) {
            val before = h.recent.takeLast(QuizPicker.recentLimit(bank.size)).toSet()
            val s = picker.next(bank, h, 0)
            assertTrue(s.q.id !in before)
        }
    }

    @Test
    fun wrongQuestionComesBackWhenDue() {
        val h = QuizHistory()
        picker.recordAnswer(h, bank[3], correct = false, now = 0)
        assertEquals("q4", picker.next(bank, h, QuizPicker.REVIEW_AFTER_MS).q.id)
        picker.recordAnswer(h, bank[3], correct = true, now = QuizPicker.REVIEW_AFTER_MS)
        assertTrue(h.due.isEmpty())
    }

    @Test
    fun smallCategoryFallsBackToWholeBank() {
        assertEquals(bank, picker.pool("maths"))
        assertEquals(bank, picker.pool("mixed"))
    }
}

class MyQuestionsPickerTest {
    private val bank = (1..5).map { Question("q$it", "gk", "Q$it", listOf("a", "b", "c", "d"), 0, "") }
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun freshOwnQuestionsComeFirstForThreeDays() {
        val mine = listOf(Question("m1", "mine", "M1", listOf("x", "y"), 1, "", mine = true, addedAt = 10 * day))
        val picker = QuizPicker(bank, mine, Random(1))
        assertEquals(mine, picker.pool("gk", 10 * day + 2 * day))
        val later = picker.pool("gk", 10 * day + 3 * day)
        assertEquals(6, later.size)
        assertTrue(later.containsAll(mine))
    }
}
