package com.quizlock.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Calendar

sealed class LockReason {
    object Screen : LockReason()
    data class App(val pkg: String) : LockReason()
    /** Kids Lock: parent ke phone par game; quiz nahi, parent ki permission. */
    data class Kids(val pkg: String) : LockReason()
    object Test : LockReason()
}

/**
 * Poori screen ka quiz jo dusre apps ke upar dikhta hai. Back button kaam nahi
 * karta. Ek "session" tab tak chalta hai jab tak zaroori sahi jawab na ho jayen
 * (ya kadam pure na hon), call aane par sirf chhupta hai (progress yaad rehta hai).
 * Kids Lock ke liye isi window mein alag screen banti hai: PIN ya fingerprint.
 */
class QuizOverlay(private val ctx: Context, private val store: Store, private val listener: Listener) {

    interface Listener {
        fun onUnlocked(reason: LockReason)
        fun onEmergency()
        fun onGoHome(pkg: String)
        fun onTestClosed()
        fun onKidsUnlocked(minutes: Int)
        fun onKidsFingerprint(minutes: Int)
        fun lockedMinutesToday(apps: Set<String>): Int
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val sensors = ctx.getSystemService(SensorManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var picker: QuizPicker

    var reason: LockReason? = null
        private set
    val isActive get() = reason != null
    val isShown get() = root?.isAttachedToWindow == true

    private lateinit var settings: Settings
    private var root: View? = null
    private var correctCount = 0
    private var current: ShownQuestion? = null

    private lateinit var subtitle: TextView
    private lateinit var progress: TextView
    private lateinit var questionText: TextView
    private lateinit var options: LinearLayout
    private lateinit var feedback: TextView
    private lateinit var skipBtn: Button
    private lateinit var walkBtn: Button
    private lateinit var homeBtn: Button
    private lateinit var pinBtn: Button
    private lateinit var pinRow: LinearLayout
    private lateinit var closeBtn: Button
    private lateinit var quizPart: LinearLayout
    private lateinit var walkPart: LinearLayout
    private lateinit var walkCount: TextView

    private var kidsMinutes = 15
    private val kidsChoices = mutableListOf<Pair<Int, Button>>()

    // ---------- Walk-to-Unlock ----------

    private var walk: StepGate? = null
    private var walkListening = false
    private val stepListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val gate = walk ?: return
            gate.onReading(e.values[0], System.currentTimeMillis())
            walkCount.text = "${gate.counted} / ${gate.target}"
            if (gate.done) walkDone()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    /** Naya session shuru karo, ya chal rahe session ki wajah (reason) badlo. */
    fun start(reason: LockReason, settings: Settings) {
        val wasKids = this.reason is LockReason.Kids
        if (isActive && wasKids == (reason is LockReason.Kids)) {
            this.reason = reason
            updateReasonViews()
            show()
            return
        }
        if (isActive) finish()
        this.reason = reason
        this.settings = settings
        correctCount = 0
        current = null
        if (reason is LockReason.Kids) {
            root = wrap(buildKidsViews(reason))
        } else {
            picker = QuizPicker(QuestionBank.load(ctx), store.myQuestions())
            root = wrap(buildViews())
            updateReasonViews()
            nextQuestion()
        }
        show()
    }

    fun show() {
        val v = root ?: return
        if (v.isAttachedToWindow) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        try {
            wm.addView(v, params)
        } catch (e: Exception) {
            // Overlay permission chhin gayi ho to app crash na ho.
        }
        if (walk != null) listenSteps(true)
    }

    /** Sirf chhupao (jaise call ke time). Session chalta rehta hai; kadam ki ginti rukti hai. */
    fun hide() {
        listenSteps(false)
        walk?.pause()
        val v = root ?: return
        if (v.isAttachedToWindow) wm.removeViewImmediate(v)
    }

    /** Session khatam. */
    fun finish() {
        handler.removeCallbacksAndMessages(null)
        hide()
        walk = null
        root = null
        reason = null
        current = null
    }

    // ---------- Quiz ----------

    private fun nextQuestion() {
        val pool = picker.pool(settings.category)
        val history = store.history()
        val shown = picker.next(pool, history, System.currentTimeMillis(), current?.q?.id)
        store.saveHistory(history)
        current = shown

        progress.text = "Correct: $correctCount / ${settings.questionsPerUnlock}" +
            if (shown.q.mine) "   •   ✍️ Your question" else ""
        questionText.text = shown.q.question
        feedback.visibility = View.GONE
        options.removeAllViews()
        shown.options.forEachIndexed { i, text ->
            options.addView(button(text, CARD, Color.WHITE, 18f) { answer(i) }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }, matchWidth(top = 10))
        }
    }

    private fun answer(i: Int) {
        val shown = current ?: return
        val correct = i == shown.answer
        for (c in 0 until options.childCount) options.getChildAt(c).isEnabled = false
        options.getChildAt(shown.answer).background = rounded(GREEN)
        if (!correct) options.getChildAt(i).background = rounded(RED)

        val history = store.history()
        picker.recordAnswer(history, shown.q, correct, System.currentTimeMillis())
        store.saveHistory(history)
        if (reason != LockReason.Test) store.recordAnswer(correct)

        feedback.visibility = View.VISIBLE
        if (correct) {
            correctCount++
            progress.text = "Correct: $correctCount / ${settings.questionsPerUnlock}"
            feedback.setTextColor(GREEN)
            feedback.text = "✅ Correct!"
            handler.postDelayed({
                val r = reason ?: return@postDelayed
                if (correctCount >= settings.questionsPerUnlock) listener.onUnlocked(r) else nextQuestion()
            }, 800)
        } else {
            feedback.setTextColor(Color.WHITE)
            penaltyTick(shown, settings.penaltySeconds)
        }
    }

    private fun penaltyTick(shown: ShownQuestion, left: Int) {
        if (current !== shown) return
        if (left <= 0) {
            nextQuestion()
            return
        }
        feedback.text = "❌ Wrong. Correct answer: ${shown.options[shown.answer]}\n" +
            (if (shown.q.explanation.isNotEmpty()) "${shown.q.explanation}\n\n" else "\n") +
            "⏳ Next question in $left sec..."
        handler.postDelayed({ penaltyTick(shown, left - 1) }, 1000)
    }

    // ---------- Walk ----------

    private fun stepSensor(): Sensor? = sensors?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    private fun walkAllowed(): Boolean {
        if (!settings.walkEnabled || stepSensor() == null) return false
        if (Build.VERSION.SDK_INT >= 29 &&
            ctx.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) return false
        return reason == LockReason.Test || store.walksLeft() > 0
    }

    private fun startWalk() {
        walk = StepGate(settings.walkSteps.coerceIn(10, 1000))
        walkCount.text = "0 / ${settings.walkSteps}"
        quizPart.visibility = View.GONE
        walkPart.visibility = View.VISIBLE
        listenSteps(true)
    }

    private fun stopWalk() {
        listenSteps(false)
        walk = null
        walkPart.visibility = View.GONE
        quizPart.visibility = View.VISIBLE
    }

    private fun walkDone() {
        listenSteps(false)
        val r = reason ?: return
        if (r != LockReason.Test) store.useWalk()
        walkCount.text = "✅ Done!"
        handler.postDelayed({ if (reason == r) listener.onUnlocked(r) }, 800)
    }

    private fun listenSteps(on: Boolean) {
        if (on == walkListening) return
        val sensor = stepSensor() ?: return
        walkListening = on
        if (on) {
            sensors.registerListener(stepListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            sensors.unregisterListener(stepListener)
        }
    }

    // ---------- Views ----------

    private fun updateReasonViews() {
        val r = reason ?: return
        val need = settings.questionsPerUnlock
        val answers = if (need == 1) "1 correct answer" else "$need correct answers"
        subtitle.text = when (r) {
            is LockReason.App -> "Answer $answers to open ${appLabel(r.pkg)}."
            LockReason.Screen -> "Answer $answers to use your phone."
            LockReason.Test -> "This is just a test. The real lock looks exactly like this."
            is LockReason.Kids -> ""
        }
        val test = r == LockReason.Test
        val skipsLeft = store.skipsLeft(settings.dailySkips)
        skipBtn.text = "Skip ($skipsLeft left today)"
        skipBtn.visibility = if (!test && skipsLeft > 0) View.VISIBLE else View.GONE
        val walksLeft = store.walksLeft()
        walkBtn.text = "🚶 Or walk ${settings.walkSteps} steps" + if (test) "" else " ($walksLeft left today)"
        walkBtn.visibility = if (walkAllowed()) View.VISIBLE else View.GONE
        homeBtn.visibility = if (r is LockReason.App) View.VISIBLE else View.GONE
        pinBtn.visibility = if (!test && settings.isParent) View.VISIBLE else View.GONE
        closeBtn.visibility = if (test) View.VISIBLE else View.GONE
    }

    private fun appLabel(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        "this app"
    }

    /** Exam countdown aur aaj ka screen time. Kuch na ho to null. */
    private fun countdownText(): String? {
        val lines = mutableListOf<String>()
        val name = settings.examName.trim()
        if (name.isNotEmpty() && settings.examDate > 0) {
            val days = ScreenTime.daysLeft(dayStart(System.currentTimeMillis()), dayStart(settings.examDate))
            when {
                days > 1 -> lines += "⏳ $name: only $days days left!"
                days == 1L -> lines += "⏳ $name is tomorrow!"
                days == 0L -> lines += "⏳ $name is today. All the best!"
            }
        }
        if (reason != LockReason.Test) {
            val min = listener.lockedMinutesToday(settings.lockedApps)
            if (min >= 1) {
                val t = if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
                lines += "📱 Today you've spent $t on locked apps."
            }
        }
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun dayStart(ms: Long) = Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun buildViews(): View {
        val content = column()
        countdownText()?.let { msg ->
            content.addView(text(msg, 18f, color = Color.WHITE, bold = true).apply {
                background = rounded(Color.parseColor("#7C2D12"))
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }, matchWidth(bottom = 16))
        }
        content.addView(text("🔒 Answer first", 26f, bold = true))
        subtitle = text("", 16f, color = MUTED)
        content.addView(subtitle, matchWidth(top = 6))

        quizPart = column(padding = false)
        progress = text("", 14f, color = ACCENT, bold = true)
        quizPart.addView(progress, matchWidth(top = 20))
        questionText = text("", 22f, bold = true)
        quizPart.addView(questionText, matchWidth(top = 8))
        options = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        quizPart.addView(options, matchWidth(top = 12))
        feedback = text("", 16f)
        quizPart.addView(feedback, matchWidth(top = 16))
        walkBtn = button("🚶 Or walk", CARD, Color.WHITE) { startWalk() }
        quizPart.addView(walkBtn, matchWidth(top = 28))
        skipBtn = button("Skip", CARD, Color.WHITE) {
            store.useSkip()
            reason?.let { listener.onUnlocked(it) }
        }
        quizPart.addView(skipBtn, matchWidth(top = 10))
        content.addView(quizPart, matchWidth())

        walkPart = column(padding = false).apply { visibility = View.GONE }
        walkPart.addView(text("🚶 Walk with your phone", 22f, bold = true), matchWidth(top = 24))
        walkPart.addView(text("Keep the phone in your hand or pocket and walk. Shaking it won't work.", 16f, color = MUTED), matchWidth(top = 6))
        walkCount = text("0", 44f, color = ACCENT, bold = true).apply { gravity = Gravity.CENTER }
        walkPart.addView(walkCount, matchWidth(top = 24))
        walkPart.addView(text("steps", 16f, color = MUTED).apply { gravity = Gravity.CENTER }, matchWidth())
        walkPart.addView(button("❓ Answer questions instead", CARD, Color.WHITE) { stopWalk() }, matchWidth(top = 28))
        content.addView(walkPart, matchWidth())

        homeBtn = button("🏠 Go back", CARD, Color.WHITE) {
            (reason as? LockReason.App)?.let { listener.onGoHome(it.pkg) }
        }
        content.addView(homeBtn, matchWidth(top = 10))
        pinBtn = button("👨‍👩‍👧 Unlock with parent PIN", CARD, Color.WHITE) {
            pinRow.visibility = if (pinRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        content.addView(pinBtn, matchWidth(top = 10))
        pinRow = buildPinRow { reason?.let { listener.onUnlocked(it) } }
        content.addView(pinRow, matchWidth(top = 10))
        content.addView(emergencyButton(), matchWidth(top = 10))
        closeBtn = button("Close test", ACCENT, Color.WHITE) { listener.onTestClosed() }
        content.addView(closeBtn, matchWidth(top = 10))
        return content
    }

    private fun buildKidsViews(r: LockReason.Kids): View {
        val content = column()
        content.addView(text("🔒 Ask a parent", 26f, bold = true))
        content.addView(text("${appLabel(r.pkg)} needs a parent's permission.", 16f, color = MUTED), matchWidth(top = 6))
        content.addView(text("Unlock for", 14f, color = ACCENT, bold = true), matchWidth(top = 24))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        kidsChoices.clear()
        kidsMinutes = 15
        for (m in listOf(15, 30, 60)) {
            val b = button("$m min", CARD, Color.WHITE) { pickKidsMinutes(m) }
            kidsChoices += m to b
            row.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = if (m != 60) dp(8) else 0
            })
        }
        content.addView(row, matchWidth(top = 8))
        pickKidsMinutes(15)
        val pin = buildPinRow { listener.onKidsUnlocked(kidsMinutes) }.apply { visibility = View.VISIBLE }
        content.addView(pin, matchWidth(top = 16))
        if (BiometricActivity.available(ctx)) {
            content.addView(button("👆 Use fingerprint", CARD, Color.WHITE) {
                listener.onKidsFingerprint(kidsMinutes)
            }, matchWidth(top = 10))
        }
        content.addView(button("🏠 Go back", CARD, Color.WHITE) { listener.onGoHome(r.pkg) }, matchWidth(top = 10))
        content.addView(emergencyButton(), matchWidth(top = 10))
        return content
    }

    private fun pickKidsMinutes(m: Int) {
        kidsMinutes = m
        for ((v, b) in kidsChoices) b.background = rounded(if (v == m) ACCENT else CARD)
    }

    private fun emergencyButton() = button("📞 Emergency call (112)", Color.TRANSPARENT, RED, stroke = RED) {
        listener.onEmergency()
    }

    private fun wrap(content: View): View = object : FrameLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
    }.apply {
        setBackgroundColor(BG)
        addView(ScrollView(ctx).apply { addView(content) })
    }

    private fun buildPinRow(onOk: () -> Unit): LinearLayout {
        val input = EditText(ctx).apply {
            hint = "Parent PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
        }
        val error = text("", 14f, color = RED)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(input, matchWidth())
            addView(button("Unlock", ACCENT, Color.WHITE) {
                if (store.checkPin(input.text.toString())) {
                    onOk()
                } else {
                    error.text = "Wrong PIN"
                    input.text.clear()
                }
            }, matchWidth(top = 6))
            addView(error, matchWidth(top = 4))
        }
    }

    private fun column(padding: Boolean = true) = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        if (padding) setPadding(dp(20), dp(48), dp(20), dp(32))
    }

    private fun text(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
        TextView(ctx).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }

    private fun button(
        label: String, bg: Int, fg: Int, size: Float = 16f, stroke: Int? = null, onClick: () -> Unit,
    ) = Button(ctx).apply {
        text = label
        textSize = size
        isAllCaps = false
        setTextColor(fg)
        background = rounded(bg, stroke)
        setPadding(dp(16), dp(14), dp(16), dp(14))
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(14).toFloat()
        if (stroke != null) setStroke(dp(2), stroke)
    }

    private fun matchWidth(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top); bottomMargin = dp(bottom) }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    companion object {
        private val BG = Color.parseColor("#0F172A")
        private val CARD = Color.parseColor("#1E293B")
        private val MUTED = Color.parseColor("#94A3B8")
        private val ACCENT = Color.parseColor("#6366F1")
        private val GREEN = Color.parseColor("#16A34A")
        private val RED = Color.parseColor("#DC2626")
    }
}
