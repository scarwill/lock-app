package com.quizlock.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

sealed class LockReason {
    object Screen : LockReason()
    data class App(val pkg: String) : LockReason()
    object Test : LockReason()
}

/**
 * Poori screen ka quiz jo dusre apps ke upar dikhta hai. Back button kaam nahi
 * karta. Ek "session" tab tak chalta hai jab tak zaroori sahi jawab na ho jayen,
 * call aane par sirf chhupta hai (progress yaad rehta hai).
 */
class QuizOverlay(private val ctx: Context, private val store: Store, private val listener: Listener) {

    interface Listener {
        fun onUnlocked(reason: LockReason)
        fun onEmergency()
        fun onGoHome(reason: LockReason.App)
        fun onTestClosed()
    }

    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val picker by lazy { QuizPicker(QuestionBank.load(ctx)) }

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
    private lateinit var homeBtn: Button
    private lateinit var pinBtn: Button
    private lateinit var pinRow: LinearLayout
    private lateinit var closeBtn: Button

    /** Naya session shuru karo, ya chal rahe session ki wajah (reason) badlo. */
    fun start(reason: LockReason, settings: Settings) {
        if (isActive) {
            this.reason = reason
            updateReasonViews()
            show()
            return
        }
        this.reason = reason
        this.settings = settings
        correctCount = 0
        current = null
        root = buildViews()
        updateReasonViews()
        nextQuestion()
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
    }

    /** Sirf chhupao (jaise call ke time). Session chalta rehta hai. */
    fun hide() {
        val v = root ?: return
        if (v.isAttachedToWindow) wm.removeViewImmediate(v)
    }

    /** Session khatam. */
    fun finish() {
        handler.removeCallbacksAndMessages(null)
        hide()
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

        progress.text = "Sahi jawab: $correctCount / ${settings.questionsPerUnlock}"
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
            progress.text = "Sahi jawab: $correctCount / ${settings.questionsPerUnlock}"
            feedback.setTextColor(GREEN)
            feedback.text = "✅ Sahi!"
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
        feedback.text = "❌ Galat. Sahi jawab: ${shown.options[shown.answer]}\n" +
            (if (shown.q.explanation.isNotEmpty()) "${shown.q.explanation}\n\n" else "\n") +
            "⏳ Agla sawal $left sec mein..."
        handler.postDelayed({ penaltyTick(shown, left - 1) }, 1000)
    }

    // ---------- Views ----------

    private fun updateReasonViews() {
        val r = reason ?: return
        val need = settings.questionsPerUnlock
        val jawab = if (need == 1) "1 sahi jawab" else "$need sahi jawab"
        subtitle.text = when (r) {
            is LockReason.App -> "${appLabel(r.pkg)} kholne ke liye $jawab chahiye."
            LockReason.Screen -> "Phone use karne ke liye $jawab chahiye."
            LockReason.Test -> "Ye sirf test hai. Jaisa asli lock mein dikhega, waisa hi."
        }
        val test = r == LockReason.Test
        val skipsLeft = store.skipsLeft(settings.dailySkips)
        skipBtn.text = "Skip ($skipsLeft bacha)"
        skipBtn.visibility = if (!test && skipsLeft > 0) View.VISIBLE else View.GONE
        homeBtn.visibility = if (r is LockReason.App) View.VISIBLE else View.GONE
        pinBtn.visibility = if (!test && settings.isParent) View.VISIBLE else View.GONE
        closeBtn.visibility = if (test) View.VISIBLE else View.GONE
    }

    private fun appLabel(pkg: String): String = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        "Ye app"
    }

    private fun buildViews(): View {
        val content = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(48), dp(20), dp(32))
        }
        content.addView(text("🔒 Pehle jawab do", 26f, bold = true))
        subtitle = text("", 16f, color = MUTED)
        content.addView(subtitle, matchWidth(top = 6))
        progress = text("", 14f, color = ACCENT, bold = true)
        content.addView(progress, matchWidth(top = 20))
        questionText = text("", 22f, bold = true)
        content.addView(questionText, matchWidth(top = 8))
        options = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.addView(options, matchWidth(top = 12))
        feedback = text("", 16f)
        content.addView(feedback, matchWidth(top = 16))

        skipBtn = button("Skip", CARD, Color.WHITE) {
            store.useSkip()
            reason?.let { listener.onUnlocked(it) }
        }
        content.addView(skipBtn, matchWidth(top = 28))
        homeBtn = button("🏠 Wapas jao", CARD, Color.WHITE) {
            (reason as? LockReason.App)?.let { listener.onGoHome(it) }
        }
        content.addView(homeBtn, matchWidth(top = 10))
        pinBtn = button("👨‍👩‍👧 Parent PIN se kholo", CARD, Color.WHITE) {
            pinRow.visibility = if (pinRow.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        content.addView(pinBtn, matchWidth(top = 10))
        pinRow = buildPinRow()
        content.addView(pinRow, matchWidth(top = 10))
        content.addView(button("📞 Emergency call (112)", Color.TRANSPARENT, RED, stroke = RED) {
            listener.onEmergency()
        }, matchWidth(top = 10))
        closeBtn = button("Test band karo", ACCENT, Color.WHITE) { listener.onTestClosed() }
        content.addView(closeBtn, matchWidth(top = 10))

        return object : FrameLayout(ctx) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode == KeyEvent.KEYCODE_BACK) true else super.dispatchKeyEvent(event)
        }.apply {
            setBackgroundColor(BG)
            addView(ScrollView(ctx).apply { addView(content) })
        }
    }

    private fun buildPinRow(): LinearLayout {
        val input = EditText(ctx).apply {
            hint = "PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED)
        }
        val error = text("", 14f, color = RED)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(input, matchWidth())
            addView(button("Kholo", ACCENT, Color.WHITE) {
                if (store.checkPin(input.text.toString())) {
                    reason?.let { listener.onUnlocked(it) }
                } else {
                    error.text = "Galat PIN"
                    input.text.clear()
                }
            }, matchWidth(top = 6))
            addView(error, matchWidth(top = 4))
        }
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

    private fun matchWidth(top: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top) }

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
