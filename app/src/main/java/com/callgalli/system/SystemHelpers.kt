package com.callgalli.system

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * What CallGalli needs while you keep using your phone's own dialer.
 * Required: the call permissions. Recommended: overlay (floating pill), call screening.
 */
object Setup {
    val CALL_PERMISSIONS = arrayOf(
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.ANSWER_PHONE_CALLS,
        @Suppress("DEPRECATION") Manifest.permission.PROCESS_OUTGOING_CALLS,
    )

    fun hasCallPermissions(context: Context): Boolean =
        CALL_PERMISSIONS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun hasOverlay(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        startFirst(
            context,
            listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null)),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            ),
        )
    }

    fun openAppSettings(context: Context) {
        startFirst(context, listOf(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))))
    }
}

/** Optional call screening role (silently rejects Stage 3 numbers that are not in contacts). */
object Roles {
    private fun roleManager(context: Context) = context.getSystemService(RoleManager::class.java)

    fun isCallScreener(context: Context): Boolean = roleManager(context)?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true

    fun screeningRequest(context: Context): Intent? =
        roleManager(context)?.takeIf { it.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) }
            ?.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)

    /** Fallback when the request dialog was dismissed with "don't ask again". */
    fun openDefaultAppsSettings(context: Context) {
        startFirst(context, listOf(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS), Intent(Settings.ACTION_SETTINGS)))
    }
}

/** Phone makers whose battery savers are known to kill background apps. */
enum class Oem { XIAOMI, SAMSUNG, OPPO, VIVO, OTHER }

/**
 * Battery saver and phone-maker restrictions can stop the call timer. This helper
 * detects them and opens the right settings page for the user.
 */
object BatteryHelper {

    fun oem(): Oem {
        val maker = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            listOf("xiaomi", "redmi", "poco").any { it in maker } -> Oem.XIAOMI
            "samsung" in maker -> Oem.SAMSUNG
            listOf("oppo", "realme", "oneplus").any { it in maker } -> Oem.OPPO
            listOf("vivo", "iqoo").any { it in maker } -> Oem.VIVO
            else -> Oem.OTHER
        }
    }

    fun isPowerSaveOn(context: Context): Boolean = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

    fun isIgnoringOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** True when something may stop the timer and the user should look at settings. */
    fun needsAttention(context: Context): Boolean = isPowerSaveOn(context) || !isIgnoringOptimizations(context)

    /** Opens the phone maker's own app-battery / auto-start page, falling back to Android's. */
    fun openBatterySettings(context: Context) {
        val oemPages = when (oem()) {
            Oem.XIAOMI -> listOf(
                component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
                    .putExtra("package_name", context.packageName),
            )
            Oem.SAMSUNG -> listOf(
                component("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                component("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            )
            Oem.OPPO -> listOf(
                component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                component("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
            )
            Oem.VIVO -> listOf(
                component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            )
            Oem.OTHER -> emptyList()
        }
        startFirst(context, oemPages + androidPages(context))
    }

    @SuppressLint("BatteryLife")
    private fun androidPages(context: Context) = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    fun openBatterySaver(context: Context) {
        startFirst(context, listOf(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) + androidPages(context))
    }

    private fun component(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))
}

/** Tries each intent until one opens. Phone makers move their settings pages around. */
fun startFirst(context: Context, intents: List<Intent>): Boolean {
    for (intent in intents) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        } catch (_: ActivityNotFoundException) {
        } catch (_: SecurityException) {
        }
    }
    return false
}
