package com.quizlock.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
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
    val walkEnabled: Boolean,
    val walkSteps: Int,
    val examName: String,
    val examDate: Long,
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
                walkEnabled = j.optBoolean("walkEnabled", true),
                walkSteps = j.optInt("walkSteps", 100),
                examName = j.optString("examName", ""),
                examDate = j.optLong("examDate", 0),
            )
        }
    }
}

/**
 * Sab kuch jo app band hone ke baad bhi yaad rehna chahiye: settings, pending
 * change, free time, aaj ke stats, quiz history, Parent PIN, apne sawal,
 * Kids Lock aur Premium.
 */
class Store(context: Context) {
    private val app = context.applicationContext
    private val p = app.getSharedPreferences("quizlock", Context.MODE_PRIVATE)

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
        val wasOn = p.getString("settings", null)?.let { Settings.parse(it).enabled } == true
        p.edit().putString("settings", json).remove("pending").remove("pending_at").apply()
        // Lock band hua to device admin hata do, taki ab app normal tarike se uninstall ho sake.
        if (wasOn && !Settings.parse(json).enabled && !kidsModeOn) removeAdmin()
    }

    private fun removeAdmin() {
        val dpm = app.getSystemService(DevicePolicyManager::class.java)
        val admin = ComponentName(app, AdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) dpm.removeActiveAdmin(admin)
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

    /** Lock ya Kids Lock mein se kuch bhi chalu hai, to service chalni chahiye. */
    fun shouldRun(): Boolean = settings()?.enabled == true || kidsModeOn

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
                .putInt("skips_used", 0).putInt("walks_used", 0).apply()
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

    fun walksLeft(): Int {
        rollDay()
        return (WALKS_PER_DAY - p.getInt("walks_used", 0)).coerceAtLeast(0)
    }

    fun useWalk() {
        rollDay()
        p.edit().putInt("walks_used", p.getInt("walks_used", 0) + 1).apply()
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

    // --- Apne banaye sawal (Flutter ki "My questions" screen) ---

    fun myQuestionsJson(): String = p.getString("my_questions", "[]")!!

    fun saveMyQuestions(json: String) {
        p.edit().putString("my_questions", json).apply()
    }

    fun myQuestions(): List<Question> = try {
        QuestionBank.parse(JSONArray(myQuestionsJson()), mine = true)
    } catch (e: Exception) {
        emptyList()
    }

    // --- Parent PIN (salt ke saath hash, asli PIN save nahi hota) ---

    fun hasPin() = p.getString("pin_hash", null) != null

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

    // --- Kids Lock (parent ke apne phone par) ---

    var kidsApps: Set<String>
        get() = JSONArray(p.getString("kids_apps", "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
        set(v) = p.edit().putString("kids_apps", JSONArray(v.toList()).toString()).apply()

    var kidsModeOn: Boolean
        get() = p.getBoolean("kids_on", false)
        set(v) {
            p.edit().putBoolean("kids_on", v).putLong("kids_free_until", 0).apply()
            if (!v && settings()?.enabled != true) removeAdmin()
        }

    var kidsFreeUntil: Long
        get() = p.getLong("kids_free_until", 0)
        set(v) = p.edit().putLong("kids_free_until", v).apply()

    // --- Premium (abhi sirf testing switch; Play billing baad mein) ---

    var premium: Boolean
        get() = p.getBoolean("premium_test", false)
        set(v) = p.edit().putBoolean("premium_test", v).apply()

    // --- User ne haath se jo setup kiya (autostart, recents lock...) ---

    fun flags(): Map<String, Boolean> = FLAGS.associateWith { p.getBoolean("flag_$it", false) }

    fun setFlag(name: String, value: Boolean) {
        if (name in FLAGS) p.edit().putBoolean("flag_$name", value).apply()
    }

    // --- Phone ne service band ki? (heartbeat) ---

    /** Service har minute batati hai ki wo zinda hai. */
    fun beat() {
        p.edit().putLong("beat_wall", System.currentTimeMillis())
            .putLong("beat_elapsed", SystemClock.elapsedRealtime()).apply()
    }

    /**
     * Service dobara shuru hui. Agar restart ke bina pichhli dhadkan 3 minute se
     * purani hai, to phone ne service maari thi.
     */
    fun noteServiceStart() {
        val wall = p.getLong("beat_wall", 0)
        val elapsed = p.getLong("beat_elapsed", 0)
        val nowElapsed = SystemClock.elapsedRealtime()
        if (wall > 0 && nowElapsed >= elapsed && nowElapsed - elapsed > 3 * 60_000L) {
            p.edit().putInt("kills", p.getInt("kills", 0) + 1)
                .putLong("last_kill", System.currentTimeMillis()).apply()
        }
        beat()
    }

    fun recentKills(): Int {
        val last = p.getLong("last_kill", 0)
        return if (System.currentTimeMillis() - last < 3 * 24 * 3600_000L) p.getInt("kills", 0) else 0
    }

    fun clearKills() {
        p.edit().putInt("kills", 0).remove("last_kill").apply()
    }

    companion object {
        const val WALKS_PER_DAY = 3
        val FLAGS = listOf("autostart", "battery_brand", "recents")
    }
}
