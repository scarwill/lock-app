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
import android.content.pm.ApplicationInfo
import android.hardware.Sensor
import android.hardware.SensorManager
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

    /** Notification tile se aaye to Flutter ko kaunsi screen kholni hai. */
    private var openRequest: String? = null

    companion object {
        const val EXTRA_OPEN = "open"
    }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        openRequest = intent?.getStringExtra(EXTRA_OPEN)
        super.onCreate(savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN)?.let { openRequest = it }
    }

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
                        "walksLeft" to store.walksLeft(),
                        "running" to LockService.isRunning,
                        "kills" to store.recentKills(),
                        "hasPin" to store.hasPin(),
                    ))
                }
                "clearKills" -> { store.clearKills(); result.success(null) }
                "deviceInfo" -> result.success(mapOf(
                    "brand" to brand(),
                    "sdk" to Build.VERSION.SDK_INT,
                    "stepSensor" to (getSystemService(SensorManager::class.java)
                        ?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null),
                    "fingerprint" to BiometricActivity.available(this),
                    "guard" to guardInstalled(),
                ))
                "flags" -> result.success(store.flags())
                "setFlag" -> {
                    store.setFlag(call.argument<String>("name")!!, call.argument<Boolean>("value")!!)
                    result.success(null)
                }
                "getMyQuestions" -> result.success(store.myQuestionsJson())
                "saveMyQuestions" -> { store.saveMyQuestions(call.arguments as String); result.success(null) }
                "getPremium" -> result.success(store.premium)
                "setPremium" -> { store.premium = call.arguments as Boolean; result.success(null) }
                "getKids" -> result.success(mapOf(
                    "apps" to store.kidsApps.toList(),
                    "on" to store.kidsModeOn,
                    "freeUntil" to store.kidsFreeUntil,
                ))
                "setKidsApps" -> {
                    store.kidsApps = (call.arguments as List<*>).map { it as String }.toSet()
                    result.success(null)
                }
                "setKidsMode" -> {
                    val on = call.arguments as Boolean
                    store.kidsModeOn = on
                    if (on) LockService.start(this) else LockService.stop(this)
                    result.success(null)
                }
                "takeOpenRequest" -> { result.success(openRequest); openRequest = null }
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
                    "isGame" to isGame(ri.activityInfo.applicationInfo),
                )
            }
    }

    @Suppress("DEPRECATION")
    private fun isGame(info: ApplicationInfo) =
        info.category == ApplicationInfo.CATEGORY_GAME || (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0

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
        "activity" to (Build.VERSION.SDK_INT < 29 ||
            checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED),
    )

    /** Seedhe APK wale build mein Accessibility service hoti hi nahi (Play Protect ki wajah se). */
    private fun guardInstalled() = try {
        packageManager.getServiceInfo(ComponentName(this, GuardService::class.java), 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Phone ki company, battery/autostart ki sahi screen aur steps ke liye. */
    private fun brand(): String {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf(
            "xiaomi" to listOf("xiaomi", "redmi", "poco"),
            "realme" to listOf("realme"),
            "oneplus" to listOf("oneplus"),
            "oppo" to listOf("oppo"),
            "vivo" to listOf("vivo", "iqoo"),
            "samsung" to listOf("samsung"),
            "huawei" to listOf("huawei", "honor"),
            "asus" to listOf("asus"),
        ).firstOrNull { (_, keys) -> keys.any { m.contains(it) } }?.first ?: "other"
    }

    /** Company ki apni battery saver screen (Android wale "battery optimization" se alag). */
    private fun batteryIntents(): List<Intent> {
        fun c(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
        return when (brand()) {
            "xiaomi" -> listOf(
                c("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                    .putExtra("package_name", packageName).putExtra("package_label", "Quiz Lock"),
            )
            "oppo", "realme", "oneplus" -> listOf(
                c("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                c("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity"),
            )
            "vivo" -> listOf(
                c("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                c("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"),
            )
            "samsung" -> listOf(c("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"))
            "huawei" -> listOf(c("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
            else -> emptyList()
        }
    }

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
            "activity" -> if (Build.VERSION.SDK_INT >= 29) {
                requestPermissions(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), 2)
            }
            "battery_brand" -> launch(*batteryIntents().toTypedArray(),
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri))
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
