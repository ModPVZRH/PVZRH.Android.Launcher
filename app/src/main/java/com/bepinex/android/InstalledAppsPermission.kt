package com.bepinex.android

import android.app.Activity
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher

/**
 * Vendor "read installed apps" permission.
 *
 * Xiaomi exposes [PERMISSION] as a dangerous runtime permission. Honor MagicOS
 * keeps the same name as an AppOps toggle and ignores [Activity.requestPermissions],
 * so those devices open the system permission page instead.
 */
object InstalledAppsPermission {

    const val PERMISSION = "com.android.permission.GET_INSTALLED_APPS"

    private const val OP_GET_INSTALLED_APPS = 10022
    private val OP_NAMES = arrayOf("android:get_installed_apps", "GET_INSTALLED_APPS")

    enum class Request {
        NotNeeded,
        RuntimeDialog,
        Settings
    }

    fun needsRequest(context: Context): Boolean = !isGranted(context)

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return true
        if (context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) return true
        if (isAppOpsAllowed(context)) return true
        if (GameDetector.supportedPackageVisible(context)) return true
        if (!isHonor() && permissionInfo(context) == null) return true
        return false
    }

    fun request(
        activity: Activity,
        runtimeLauncher: ActivityResultLauncher<String>,
        settingsLauncher: ActivityResultLauncher<Intent>,
        allowSettings: Boolean
    ): Request {
        if (!needsRequest(activity)) return Request.NotNeeded
        logStatus(activity)
        if (canRequestRuntime(activity) && !isHonor()) {
            try {
                runtimeLauncher.launch(PERMISSION)
                return Request.RuntimeDialog
            } catch (error: Exception) {
                BepInExLog.e("GET_INSTALLED_APPS requestPermissions failed", error)
            }
        }
        if (!allowSettings) return Request.NotNeeded
        return if (openSettings(activity, settingsLauncher)) Request.Settings else Request.NotNeeded
    }

    private fun openSettings(
        activity: Activity,
        settingsLauncher: ActivityResultLauncher<Intent>
    ): Boolean {
        val candidates = listOf(
            ComponentName(
                "com.hihonor.permissionmanager",
                "com.hihonor.permissionmanager.ui.SingleAppActivity"
            ),
            ComponentName(
                "com.hihonor.systemmanager",
                "com.hihonor.permissionmanager.ui.SingleAppActivity"
            ),
            ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.permissionmanager.ui.SingleAppActivity"
            )
        )
        for (component in candidates) {
            val intent = Intent().apply {
                this.component = component
                putExtra("packageName", activity.packageName)
                putExtra("packagename", activity.packageName)
            }
            try {
                settingsLauncher.launch(intent)
                BepInExLog.i("Opened app list settings: ${component.packageName}/${component.className}")
                showSettingsHint(activity)
                return true
            } catch (error: ActivityNotFoundException) {
                BepInExLog.i("App list settings activity missing: ${component.className}")
            } catch (error: SecurityException) {
                BepInExLog.w("App list settings activity blocked: ${component.className}: ${error.message}")
            }
        }

        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", activity.packageName, null)
        }
        return try {
            settingsLauncher.launch(details)
            BepInExLog.i("Opened app details for app list permission")
            showSettingsHint(activity)
            true
        } catch (error: Exception) {
            BepInExLog.e("Failed to open app details for app list permission", error)
            false
        }
    }

    private fun showSettingsHint(activity: Activity) {
        Toast.makeText(
            activity,
            activity.getString(R.string.applist_permission_settings_hint),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun canRequestRuntime(context: Context): Boolean {
        val info = permissionInfo(context) ?: return false
        return info.protection == PermissionInfo.PROTECTION_DANGEROUS
    }

    private fun permissionInfo(context: Context): PermissionInfo? {
        return try {
            context.packageManager.getPermissionInfo(PERMISSION, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun isHonor(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("honor") || brand.contains("honor")
    }

    private fun isAppOpsAllowed(context: Context): Boolean {
        val mode = appOpsMode(context)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun appOpsMode(context: Context): Int? {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return null
        val uid = Process.myUid()
        val packageName = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            for (op in OP_NAMES) {
                val mode = try {
                    appOps.unsafeCheckOpNoThrow(op, uid, packageName)
                } catch (_: Exception) {
                    null
                }
                if (mode == AppOpsManager.MODE_ALLOWED || mode == AppOpsManager.MODE_IGNORED) return mode
            }
        }
        return try {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                String::class.java
            )
            method.invoke(appOps, OP_GET_INSTALLED_APPS, uid, packageName) as? Int
        } catch (_: Exception) {
            null
        }
    }

    private fun logStatus(context: Context) {
        val protection = permissionInfo(context)?.protection
        BepInExLog.i(
            "App list permission: manufacturer=${Build.MANUFACTURER} brand=${Build.BRAND} " +
                "protection=$protection honor=${isHonor()} " +
                "self=${context.checkSelfPermission(PERMISSION)} appOps=${appOpsMode(context)} " +
                "visible=${GameDetector.supportedPackageVisible(context)}"
        )
    }
}
