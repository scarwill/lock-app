package com.quizlock.app

import android.content.Context
import org.json.JSONObject

/** Flutter asset `assets/questions.json` padhta hai, taki sawal ek hi jagah rahen. */
object QuestionBank {
    private var cache: List<Question>? = null

    fun load(context: Context): List<Question> {
        cache?.let { return it }
        val text = context.assets.open("flutter_assets/assets/questions.json")
            .bufferedReader().use { it.readText() }
        val arr = JSONObject(text).getJSONArray("questions")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val opts = o.getJSONArray("options")
            Question(
                id = o.getString("id"),
                category = o.getString("category"),
                question = o.getString("question"),
                options = (0 until opts.length()).map { opts.getString(it) },
                answer = o.getInt("answer"),
                explanation = o.optString("explanation"),
            )
        }
        cache = list
        return list
    }
}
