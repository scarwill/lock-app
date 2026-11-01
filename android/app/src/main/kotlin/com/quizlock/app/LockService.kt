package com.quizlock.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.telecom.TelecomManager
import java.util.Calendar

/**
 * Lock ka dil. Har aadhe second dekhti hai kaunsa app saamne hai, aur zaroorat
 * ho to quiz (ya Kids Lock) overlay dikhati hai. Foreground service hai taki
 * Android ise band na kare; phir bhi band ho jaye to GuardService, WatchdogJob
 * aur recents se hatane wala alarm ise wapas chalu karte hain.
 */
class LockService : Service(), QuizOverlay.Listener {

    private lateinit var store: Store
    private lateinit var overlay: QuizOverlay
    private lateinit var usm: UsageStatsManager
    private lateinit var audio: AudioManager
    private lateinit var power: PowerManager
    private val handler = Handler(Looper.getMainLooper())

    private var foreground: String? = null
    private var lastQuery = System.currentTimeMillis() - 10_000
    private var lastBeat = 0L
    private var notifiedKids: Boolean? = null

    private var emergencySince = 0L
    private var emergencySawDialer = false
    private val emergencyPackages = mutableSetOf(
        "com.android.server.telecom", "com.android.phone", "com.android.incallui",
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.incallui",
        "com.samsung.android.dialer", "com.android.emergency", "com.android.systemui",
    )

    /** "Wapas jao" ke baad ye app thodi der tak ignore, jab tak launcher saamne aaye. */
    private var ignorePkg: String? = null
    private var ignoreUntil = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                val s = store.settings()
                if (s != null && s.enabled && s.locksScreen && System.currentTimeMillis() >= store.cooldownUntil) {
                    store.lockscreenQuizPending = true
                }
            }
            poke()
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            check()
            handler.postDelayed(this, if (power.isInteractive) 500L else 3000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        overlay = QuizOverlay(this, store, this)
        usm = getSystemService(UsageStatsManager::class.java)
        audio = getSystemService(AudioManager::class.java)
        power = getSystemService(PowerManager::class.java)
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { emergencyPackages += it }
        registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        })
        store.noteServiceStart()
        WatchdogJob.schedule(this)
        instance = this
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {
            ACTION_BOOT -> {
                // Restart karke bachne ka rasta band: free time khatam, lock screen quiz turant.
                store.cooldownUntil = 0
                store.lockscreenQuizPending = store.settings()?.locksScreen == true
            }
            ACTION_TEST -> store.settings()?.let { overlay.start(LockReason.Test, it) }
        }
        poke()
        return START_STICKY
    }

    /** Recent apps se swipe karne par kai phone service bhi maar dete hain: 2 second baad wapas chalu. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (store.shouldRun()) {
            val pi = PendingIntent.getForegroundService(
                this, 7, Intent(this, LockService::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            getSystemService(AlarmManager::class.java)
                .set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 2_000, pi)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning = false
        if (instance === this) instance = null
        handler.removeCallbacksAndMessages(null)
        overlay.finish()
        unregisterReceiver(screenReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun check() {
        val s = store.settings()
        val kidsOn = store.kidsModeOn
        if (s == null || (!s.enabled && !kidsOn && overlay.reason != LockReason.Test)) {
            overlay.finish()
            stopSelf()
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastBeat > 60_000) {
            lastBeat = now
            store.beat()
        }
        if (notifiedKids != kidsOn) startInForeground()
        val fg = pollForeground()

        if (inCall()) {
            overlay.hide()
            return
        }
        if (emergencySince > 0) {
            val allowed = fg == null || fg in emergencyPackages
            if (allowed && fg != null) emergencySawDialer = true
            val timedOut = !emergencySawDialer && now - emergencySince > 5_000
            if ((emergencySawDialer && !allowed) || timedOut) emergencySince = 0 else return
        }
        if (overlay.reason == LockReason.Test) {
            overlay.show()
            return
        }

        val ignored = fg == ignorePkg && now < ignoreUntil
        val kidsLocked = kidsOn && fg != null && fg in store.kidsApps && now >= store.kidsFreeUntil && !ignored
        val cooling = now < store.cooldownUntil
        if (cooling) store.lockscreenQuizPending = false
        val quizOn = s.enabled
        val appLocked = quizOn && fg != null && fg in s.lockedApps && !cooling && !ignored
        when {
            quizOn && store.lockscreenQuizPending && s.locksScreen -> overlay.start(LockReason.Screen, s)
            overlay.reason == LockReason.Screen -> overlay.show()
            kidsLocked -> overlay.start(LockReason.Kids(fg!!), s)
            appLocked -> overlay.start(LockReason.App(fg!!), s)
            // User locked app se nikal gaya.
            overlay.reason is LockReason.App || overlay.reason is LockReason.Kids -> overlay.finish()
        }
    }

    /** Sabse naya "app saamne aaya" event. Naya event na ho to pichhla hi maano. */
    private fun pollForeground(): String? {
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastQuery - 3_000, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            @Suppress("DEPRECATION")
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) foreground = e.packageName
        }
        lastQuery = now
        return foreground
    }

    private fun inCall() = audio.mode == AudioManager.MODE_RINGTONE ||
        audio.mode == AudioManager.MODE_IN_CALL ||
        audio.mode == AudioManager.MODE_IN_COMMUNICATION

    // ---------- Overlay ke buttons ----------

    override fun onUnlocked(reason: LockReason) {
        if (reason != LockReason.Test) {
            val s = store.settings()
            store.cooldownUntil = System.currentTimeMillis() + (s?.cooldownMinutes ?: 30) * 60_000L
            store.lockscreenQuizPending = false
        }
        overlay.finish()
    }

    override fun onKidsUnlocked(minutes: Int) {
        store.kidsFreeUntil = System.currentTimeMillis() + minutes * 60_000L
        overlay.finish()
    }

    override fun onKidsFingerprint(minutes: Int) {
        overlay.hide()
        startActivity(Intent(this, BiometricActivity::class.java)
            .putExtra(BiometricActivity.EXTRA_MINUTES, minutes)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
    }

    override fun onEmergency() {
        overlay.hide()
        emergencySince = System.currentTimeMillis()
        emergencySawDialer = false
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onGoHome(pkg: String) {
        ignorePkg = pkg
        ignoreUntil = System.currentTimeMillis() + 2_000
        overlay.finish()
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onTestClosed() {
        overlay.finish()
        if (!store.shouldRun()) stopSelf()
    }

    /** Aaj (raat 12 baje se) locked apps kitne minute saamne rahe. */
    override fun lockedMinutesToday(apps: Set<String>): Int {
        if (apps.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val from = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val marks = mutableListOf<UsageMark>()
        try {
            val events = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                @Suppress("DEPRECATION")
                when (e.eventType) {
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> marks += UsageMark(e.packageName, true, e.timeStamp)
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> marks += UsageMark(e.packageName, false, e.timeStamp)
                }
            }
        } catch (e: Exception) {
            return 0
        }
        return (ScreenTime.foregroundMs(marks, apps, from, now) / 60_000L).toInt()
    }

    // ---------- Notification ----------

    private fun startInForeground() {
        val kids = store.kidsModeOn
        notifiedKids = kids
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lock service", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (kids) "Kids Lock is on" else "Quiz Lock is on")
            .setContentText(if (kids) "Games need a parent's permission" else "Locked apps will ask a quiz first")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    companion object {
        const val ACTION_BOOT = "com.quizlock.app.BOOT"
        const val ACTION_TEST = "com.quizlock.app.TEST"
        private const val CHANNEL = "lock"
        private const val NOTIFICATION_ID = 1

        /** Service is process mein abhi chal rahi hai ya nahi (Home ka asli status). */
        @Volatile
        var isRunning = false
            private set
        private var instance: LockService? = null

        /** Turant ek baar check karo (app khula, screen on hui...). */
        fun poke() {
            val s = instance ?: return
            s.handler.removeCallbacks(s.tick)
            s.handler.post(s.tick)
        }

        fun start(context: Context, action: String? = null) {
            context.startForegroundService(Intent(context, LockService::class.java).setAction(action))
        }

        /** Lock band hua; par Kids Lock chalu ho to service chalti rahegi. */
        fun stop(context: Context) {
            if (Store(context).shouldRun()) poke() else context.stopService(Intent(context, LockService::class.java))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!Store(context).shouldRun()) return
        val action = if (intent.action == Intent.ACTION_BOOT_COMPLETED) LockService.ACTION_BOOT else null
        try {
            LockService.start(context, action)
        } catch (e: Exception) {
            // Kuch phones par background start mana hai; WatchdogJob ya app kholna ise chalu karega.
        }
    }
}
