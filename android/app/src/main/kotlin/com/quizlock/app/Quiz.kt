package com.quizlock.app

import kotlin.random.Random

data class Question(
    val id: String,
    val category: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String,
    /** User ne khud banaya (My questions). */
    val mine: Boolean = false,
    /** Apne sawal kab jode gaye (millis); naye sawal 3 din tak pehle aate hain. */
    val addedAt: Long = 0,
)

/** Question jiske options shuffle ho chuke hain. */
data class ShownQuestion(val q: Question, val options: List<String>, val answer: Int)

class QuizHistory(
    /** Haal hi mein dikhe sawal, purane se naye ki taraf. */
    val recent: MutableList<String> = mutableListOf(),
    /** Galat kiye sawal -> kab dobara poochna hai (millis). */
    val due: MutableMap<String, Long> = mutableMapOf(),
)

/**
 * Agla sawal chunne ka logic. Android se alag rakha hai taki JVM test chal sake.
 *
 * Kram: (1) galat kiye sawal jinka time aa gaya, (2) jo haal mein nahi dikhe,
 * (3) sabse purana dikha hua. Abhi wala sawal kabhi turant repeat nahi hota.
 *
 * User ke apne sawal: jode jaane ke 3 din tak sirf wahi aate hain (kal ka test),
 * uske baad har subject ke sawalon mein mil jaate hain.
 */
class QuizPicker(
    private val bank: List<Question>,
    private val mine: List<Question> = emptyList(),
    private val random: Random = Random.Default,
) {

    /** Category mein bahut kam sawal hon to sab categories se lo. */
    fun pool(category: String, now: Long = System.currentTimeMillis()): List<Question> {
        val fresh = mine.filter { now - it.addedAt < FRESH_MS }
        if (fresh.isNotEmpty()) return fresh
        val base = if (category == "mixed") bank else bank.filter { it.category == category }
            .let { if (it.size >= MIN_POOL) it else bank }
        return base + mine
    }

    fun next(pool: List<Question>, history: QuizHistory, now: Long, excludeId: String? = null): ShownQuestion {
        require(pool.isNotEmpty()) { "Question bank is empty" }
        val candidates = pool.filter { it.id != excludeId }.ifEmpty { pool }
        val recentWindow = history.recent.takeLast(recentLimit(pool.size)).toSet()

        val q = candidates.filter { (history.due[it.id] ?: Long.MAX_VALUE) <= now && it.id !in recentWindow }
            .randomOrNull(random)
            ?: candidates.filter { it.id !in recentWindow }.randomOrNull(random)
            ?: history.recent.firstNotNullOfOrNull { id -> candidates.find { it.id == id } }
            ?: candidates.random(random)

        history.recent.remove(q.id)
        history.recent.add(q.id)
        while (history.recent.size > MAX_RECENT) history.recent.removeAt(0)
        return shuffle(q)
    }

    fun recordAnswer(history: QuizHistory, q: Question, correct: Boolean, now: Long) {
        if (correct) history.due.remove(q.id) else history.due[q.id] = now + REVIEW_AFTER_MS
    }

    private fun shuffle(q: Question): ShownQuestion {
        val order = q.options.indices.shuffled(random)
        return ShownQuestion(q, order.map { q.options[it] }, order.indexOf(q.answer))
    }

    companion object {
        const val MIN_POOL = 5
        const val MAX_RECENT = 200
        const val REVIEW_AFTER_MS = 24 * 60 * 60 * 1000L
        const val FRESH_MS = 3 * 24 * 60 * 60 * 1000L

        /** Pool ka aadha (max 50) haal ka maana jata hai. */
        fun recentLimit(poolSize: Int) = (poolSize / 2).coerceIn(1, 50)
    }
}
