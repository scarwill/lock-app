package com.quizlock.app

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings as AndroidSettings
import android.provider.Telephony
import android.telecom.TelecomManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.ByteArrayOutputStream

class MainActivity : FlutterActivity() {

    private val store by lazy { Store(this) }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "quizlock/native").setMethodCallHandler { call, result ->
            when (call.method) {
                "getSettings" -> result.success(store.settingsJson())
                "saveSettings" -> { store.saveSettings(call.arguments as String); result.success(null) }
                "getPending" -> result.success(store.pending()?.let { (json, at) -> mapOf("json" to json, "applyAt" to at) })
                "setPending" -> {
                    store.setPending(call.argument<String>("json")!!, call.argument<Number>("applyAt")!!.toLong())
                    result.success(null)
                }
                "cancelPending" -> { store.cancelPending(); result.success(null) }
                "installedApps" -> Thread {
                    val apps = installedApps()
                    runOnUiThread { result.success(apps) }
                }.start()
                "permissions" -> result.success(permissions())
                "openPermission" -> { openPermission(call.arguments as String); result.success(null) }
                "startLock" -> { LockService.start(this); result.success(null) }
                "stopLock" -> { LockService.stop(this); result.success(null) }
                "testQuiz" -> { LockService.start(this, LockService.ACTION_TEST); result.success(null) }
                "setPin" -> { store.setPin(call.arguments as String); result.success(null) }
                "checkPin" -> result.success(store.checkPin(call.arguments as String))
                "status" -> {
                    val s = store.settings()
                    result.success(mapOf(
                        "cooldownUntil" to store.cooldownUntil,
                        "todayCorrect" to store.todayCorrect(),
                        "todayWrong" to store.todayWrong(),
                        "skipsLeft" to store.skipsLeft(s?.dailySkips ?: 0),
                        "running" to (s?.enabled == true),
                    ))
                }
                else -> result.notImplemented()
            }
        }
    }

    // ---------- Apps ki list ----------

    /** Launcher wale apps, siwaye phone, SMS, settings, launcher aur khud is app ke. */
    private fun installedApps(): List<Map<String, Any?>> {
        val pm = packageManager
        val hidden = mutableSetOf(packageName, "com.android.settings")
        getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { hidden += it }
        Telephony.Sms.getDefaultSmsPackage(this)?.let { hidden += it }
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .forEach { hidden += it.activityInfo.packageName }

        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName !in hidden }
            .map { ri ->
                mapOf(
                    "package" to ri.activityInfo.packageName,
                    "label" to ri.loadLabel(pm).toString(),
                    "icon" to iconPng(ri.loadIcon(pm)),
                )
            }
    }

    private fun iconPng(d: android.graphics.drawable.Drawable): ByteArray? = try {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, size, size)
        d.draw(Canvas(bmp))
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    } catch (e: Exception) {
        null
    }

    // ---------- Permissions ----------

    private fun permissions(): Map<String, Boolean> = mapOf(
        "usage" to hasUsageAccess(this),
        "overlay" to AndroidSettings.canDrawOverlays(this),
        "battery" to getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
        "notifications" to (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED),
        "admin" to getSystemService(DevicePolicyManager::class.java).isAdminActive(adminComponent()),
        "accessibility" to GuardService.isEnabled(this),
    )

    private fun adminComponent() = ComponentName(this, AdminReceiver::class.java)

    private fun openPermission(name: String) {
        val pkgUri = Uri.parse("package:$packageName")
        when (name) {
            "usage" -> launch(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS).setData(pkgUri),
                Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
            "overlay" -> launch(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri))
            "battery" -> launch(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri),
                Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            "notifications" -> if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
            "admin" -> launch(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent())
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "This stops your child from deleting Quiz Lock without the parent PIN."))
            "accessibility" -> launch(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            "appinfo" -> launch(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
            "autostart" -> launch(*autostartIntents().toTypedArray(),
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
        }
    }

    /** Har company ki autostart setting alag jagah hoti hai. Jo pehli khule wahi sahi. */
    private fun autostartIntents() = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
        "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
    ).map { (pkg, cls) -> Intent().setComponent(ComponentName(pkg, cls)) }

    private fun launch(vararg intents: Intent) {
        for (i in intents) {
            try {
                startActivity(i)
                return
            } catch (e: Exception) {
                // Agla try karo.
            }
        }
    }
}

/** Device admin: active admin app seedha uninstall nahi hota. Lock band hone par Store ise hata deta hai. */
class AdminReceiver : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence =
        "This removes Quiz Lock's uninstall protection."
}

fun hasUsageAccess(context: Context): Boolean {
    val ops = context.getSystemService(AppOpsManager::class.java)
    val mode = if (Build.VERSION.SDK_INT >= 29) {
        ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    } else {
        @Suppress("DEPRECATION")
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    }
    // Kuch phones par toggle ON karne ke baad bhi mode DEFAULT rehta hai, tab permission se check karo.
    if (mode == AppOpsManager.MODE_DEFAULT) {
        return context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    }
    return mode == AppOpsManager.MODE_ALLOWED
}
