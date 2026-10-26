package com.quizlock.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

/**
 * Uninstall protection. Jab lock chalu ho, to Quiz Lock ko uninstall, force
 * stop ya uski permissions band karne wali screens khulte hi Home par bhej deta
 * hai. Lock band karne ke baad (Student: 24 ghante, Parent: PIN) ye rukta hai.
 */
class GuardService : AccessibilityService() {

    private lateinit var store: Store
    private lateinit var appLabel: String
    private var launchers = emptySet<String>()
    private var lastBlock = 0L

    override fun onServiceConnected() {
        store = Store(this)
        appLabel = applicationInfo.loadLabel(packageManager).toString()
        launchers = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0,
        ).map { it.activityInfo.packageName }.toSet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || !GuardRules.isWatched(pkg, launchers)) return
        if (!protectionActive()) return
        val now = System.currentTimeMillis()
        if (now - lastBlock < 1_000) return

        val root = rootInActiveWindow ?: return
        if (root.findAccessibilityNodeInfosByText(appLabel).isNullOrEmpty()) return
        if (!GuardRules.shouldBlock(collectText(root), appLabel)) return

        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(
            this,
            "$appLabel is protected. Turn the lock off in $appLabel settings first.",
            Toast.LENGTH_LONG,
        ).show()
    }

    override fun onInterrupt() {}

    /** Lock chalu hai aur sach mein kaam kar raha hai. Permission hi missing ho to settings kholne do, taki theek ho sake. */
    private fun protectionActive(): Boolean {
        val s = store.settings() ?: return false
        return s.enabled && hasUsageAccess(this) && AndroidSettings.canDrawOverlays(this)
    }

    private fun collectText(root: AccessibilityNodeInfo): List<String> {
        val out = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        while (queue.isNotEmpty() && out.size < 500) {
            val n = queue.removeFirst()
            n.text?.let { out += it.toString() }
            n.contentDescription?.let { out += it.toString() }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return out
    }

    companion object {
        fun isEnabled(context: Context): Boolean {
            val enabled = AndroidSettings.Secure.getString(
                context.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = "${context.packageName}/${GuardService::class.java.name}"
            val short = "${context.packageName}/.${GuardService::class.java.simpleName}"
            return enabled.split(':').any { it.equals(me, true) || it.equals(short, true) }
        }
    }
}

/** Kaunsi screen block karni hai. Android se alag, taki JVM test chal sake. */
object GuardRules {
    private val systemPackages = setOf(
        "com.android.settings", "com.android.vending", "com.miui.securitycenter",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.coloros.safecenter", "com.oplus.safecenter", "com.vivo.permissionmanager",
        "com.iqoo.secure", "com.huawei.systemmanager",
    )
    private val packageHints = listOf("settings", "packageinstaller", "securitycenter", "safecenter", "permission")

    /** Screen par app ke naam ke saath inme se kuch bhi ho to wo Quiz Lock ko hatane/band karne ki screen hai. */
    private val dangerWords = listOf(
        "uninstall", "force stop", "deactivat", "device admin", "admin app",
        "usage access", "usage data", "usage tracking",
        "display over", "appear on top", "draw over",
        "clear storage", "clear data", "autostart", "auto-start", "auto start",
    )

    fun isWatched(pkg: String, launchers: Set<String>) =
        pkg in systemPackages || pkg in launchers || packageHints.any { pkg.contains(it) }

    fun shouldBlock(texts: List<String>, appLabel: String): Boolean {
        val all = texts.joinToString("\n").lowercase()
        val label = appLabel.lowercase()
        if (!all.contains(label)) return false
        // Accessibility ki apni detail screen ("Use Quiz Lock ..." toggle).
        if (all.contains("use $label")) return true
        return dangerWords.any { all.contains(it) }
    }
}
