package com.quizlock.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Flutter wali `LockSettings` (lib/settings.dart) ka Kotlin roop. Keys same rakhni hain. */
data class Settings(
    val onboarded: Boolean,
    val enabled: Boolean,
    val mode: String,
    val lockType: String,
    val lockedApps: Set<String>,
    val category: String,
    val questionsPerUnlock: Int,
    val penaltySeconds: Int,
    val cooldownMinutes: Int,
    val dailySkips: Int,
) {
    val isParent get() = mode == "parent"
    val locksScreen get() = lockType == "apps_lockscreen"

    companion object {
        fun parse(json: String): Settings {
            val j = JSONObject(json)
            val apps = j.optJSONArray("lockedApps") ?: JSONArray()
            return Settings(
                onboarded = j.optBoolean("onboarded"),
                enabled = j.optBoolean("enabled"),
                mode = j.optString("mode", "student"),
                lockType = j.optString("lockType", "apps"),
                lockedApps = (0 until apps.length()).map { apps.getString(it) }.toSet(),
                category = j.optString("category", "mixed"),
                questionsPerUnlock = j.optInt("questionsPerUnlock", 1),
                penaltySeconds = j.optInt("penaltySeconds", 15),
                cooldownMinutes = j.optInt("cooldownMinutes", 30),
                dailySkips = j.optInt("dailySkips", 1),
            )
        }
    }
}

/**
 * Sab kuch jo app band hone ke baad bhi yaad rehna chahiye: settings, pending
 * change, free time, aaj ke stats, quiz history aur Parent PIN.
 */
class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("quizlock", Context.MODE_PRIVATE)

    private var cachedJson: String? = null
    private var cached: Settings? = null

    fun settingsJson(): String? {
        applyPendingIfDue()
        return p.getString("settings", null)
    }

    fun settings(): Settings? {
        val json = settingsJson() ?: return null
        if (json != cachedJson) {
            cached = Settings.parse(json)
            cachedJson = json
        }
        return cached
    }

    /** Naya save hamesha pending change cancel karta hai. */
    fun saveSettings(json: String) {
        p.edit().putString("settings", json).remove("pending").remove("pending_at").apply()
    }

    fun setPending(json: String, applyAt: Long) {
        p.edit().putString("pending", json).putLong("pending_at", applyAt).apply()
    }

    fun pending(): Pair<String, Long>? {
        val json = p.getString("pending", null) ?: return null
        return json to p.getLong("pending_at", 0)
    }

    fun cancelPending() {
        p.edit().remove("pending").remove("pending_at").apply()
    }

    private fun applyPendingIfDue() {
        val (json, at) = pending() ?: return
        if (System.currentTimeMillis() >= at) saveSettings(json)
    }

    var cooldownUntil: Long
        get() = p.getLong("cooldown_until", 0)
        set(v) = p.edit().putLong("cooldown_until", v).apply()

    /** Unlock ya restart ke baad lock screen wala quiz dikhana baaki hai. */
    var lockscreenQuizPending: Boolean
        get() = p.getBoolean("lockscreen_pending", false)
        set(v) = p.edit().putBoolean("lockscreen_pending", v).apply()

    // --- Aaj ke stats (din badalte hi reset) ---

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    private fun rollDay() {
        val t = today()
        if (p.getString("day", "") != t) {
            p.edit().putString("day", t).putInt("correct", 0).putInt("wrong", 0)
                .putInt("skips_used", 0).apply()
        }
    }

    fun recordAnswer(correct: Boolean) {
        rollDay()
        val key = if (correct) "correct" else "wrong"
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    fun todayCorrect(): Int { rollDay(); return p.getInt("correct", 0) }
    fun todayWrong(): Int { rollDay(); return p.getInt("wrong", 0) }

    fun skipsLeft(allowed: Int): Int {
        rollDay()
        return (allowed - p.getInt("skips_used", 0)).coerceAtLeast(0)
    }

    fun useSkip() {
        rollDay()
        p.edit().putInt("skips_used", p.getInt("skips_used", 0) + 1).apply()
    }

    // --- Quiz history ---

    fun history(): QuizHistory {
        val recent = JSONArray(p.getString("recent", "[]"))
        val due = JSONObject(p.getString("due", "{}"))
        return QuizHistory(
            recent = (0 until recent.length()).map { recent.getString(it) }.toMutableList(),
            due = due.keys().asSequence().associateWith { due.getLong(it) }.toMutableMap(),
        )
    }

    fun saveHistory(h: QuizHistory) {
        p.edit()
            .putString("recent", JSONArray(h.recent).toString())
            .putString("due", JSONObject(h.due as Map<*, *>).toString())
            .apply()
    }

    // --- Parent PIN (salt ke saath hash, asli PIN save nahi hota) ---

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        p.edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = p.getString("pin_salt", null) ?: return false
        return hash(salt, pin) == p.getString("pin_hash", null)
    }

    private fun hash(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
