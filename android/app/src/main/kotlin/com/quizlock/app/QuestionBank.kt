package com.quizlock.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Flutter asset `assets/questions.json` padhta hai, taki sawal ek hi jagah rahen. */
object QuestionBank {
    private var cache: List<Question>? = null

    fun load(context: Context): List<Question> {
        cache?.let { return it }
        val text = context.assets.open("flutter_assets/assets/questions.json")
            .bufferedReader().use { it.readText() }
        val list = parse(JSONObject(text).getJSONArray("questions"))
        cache = list
        return list
    }

    /** Wahi format jo questions.json aur "My questions" dono use karte hain. */
    fun parse(arr: JSONArray, mine: Boolean = false): List<Question> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.getJSONObject(i)
        val opts = o.getJSONArray("options")
        val options = (0 until opts.length()).map { opts.getString(it) }
        val answer = o.getInt("answer")
        if (options.size < 2 || answer !in options.indices) return@mapNotNull null
        Question(
            id = o.getString("id"),
            category = o.optString("category", "mine"),
            question = o.getString("question"),
            options = options,
            answer = answer,
            explanation = o.optString("explanation"),
            mine = mine,
            addedAt = o.optLong("addedAt", 0),
        )
    }
}
