package com.bepinex.android

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Auto-detects Unity IL2CPP games installed on the device.
 */
object GameDetector {

    private const val TAG = "GameDetector"

    /** Only packages in this list are shown as supported games. */
    private val SUPPORTED_PACKAGES = setOf(
        "com.LanPiaoPiao.PlantsVsZombiesRH",
        "com.LanPiaoPiao.PlantsVsZombiesRHMod"
    )

    const val PVZ_LAUNCHER_ACTIVITY = "com.unity3d.player.UnityPlayerActivity"

    /** ABI directories inside APKs (lib/<abi>/libil2cpp.so). */
    private val UNITY_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")

    /** Directory names used under nativeLibraryDir's parent. */
    private val NATIVE_LIB_DIR_NAMES = listOf(
        "arm64-v8a", "armeabi-v7a", "armeabi", "arm64", "arm", "x86_64", "x86"
    )

    /** Cached detection results */
    private var cachedGames: List<DetectedGame>? = null

    data class DetectedGame(
        val packageName: String,
        val label: String,
        val icon: Drawable?,
        val versionName: String,
        val versionCode: Long,
        val unityVersion: String?,
        val apkPath: String
    )

    private enum class Il2CppStatus {
        Found,
        Absent,
        Unreadable
    }

    /**
     * Probe known packages by name first, then scan launchable apps for any
     * still missing. Whitelist packages stay detected when split APKs hold
     * libil2cpp.so, or when Android 14+ blocks reading another app's APK.
     */
    suspend fun detectGames(context: Context): List<DetectedGame> = withContext(Dispatchers.IO) {
        cachedGames?.let { return@withContext it }

        val pm = context.packageManager
        val results = mutableListOf<DetectedGame>()
        val foundPackages = mutableSetOf<String>()
        val ownPackage = context.packageName

        fun addIfNew(packageName: String, via: String) {
            if (packageName == ownPackage || packageName in foundPackages) return
            val game = resolveGame(pm, packageName) ?: return
            BepInExLog.i("Detected ($via): ${game.label} ($packageName) v${game.versionName}")
            foundPackages.add(packageName)
            results.add(game)
        }

        BepInExLog.i("Probing ${SUPPORTED_PACKAGES.size} known game package(s)...")
        for (packageName in SUPPORTED_PACKAGES) {
            addIfNew(packageName, "package-name")
        }

        val launchIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(launchIntent, PackageManager.MATCH_ALL)
        BepInExLog.i("Scanning ${activities.size} launchable apps for remaining games...")
        for (resolveInfo in activities) {
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName !in SUPPORTED_PACKAGES) continue
            addIfNew(packageName, "launcher")
        }

        BepInExLog.i("Found ${results.size} Unity IL2CPP game(s)")

        cachedGames = results
        results
    }

    private fun resolveGame(pm: PackageManager, packageName: String): DetectedGame? {
        val appInfo = try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }

        if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return null

        when (probeIl2Cpp(appInfo)) {
            Il2CppStatus.Found -> Unit
            Il2CppStatus.Unreadable -> BepInExLog.i(
                "libil2cpp.so unreadable for $packageName (split or Android 14+), accepting package-name match"
            )
            Il2CppStatus.Absent -> BepInExLog.i(
                "libil2cpp.so not found for $packageName, accepting package-name match"
            )
        }

        val label = pm.getApplicationLabel(appInfo).toString()
        val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }

        var versionName = "Unknown"
        var versionCode = 0L
        try {
            @Suppress("DEPRECATION")
            val pkgInfo = pm.getPackageInfo(packageName, 0)
            versionName = pkgInfo.versionName ?: "Unknown"
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to get package info for $packageName: ${e.message}")
        }

        return DetectedGame(
            packageName = packageName,
            label = label,
            icon = icon,
            versionName = versionName,
            versionCode = versionCode,
            unityVersion = null,
            apkPath = appInfo.sourceDir ?: appInfo.publicSourceDir.orEmpty()
        )
    }

    /**
     * Look for libil2cpp.so in the base APK, split APKs, and extracted native lib dirs.
     * [Il2CppStatus.Unreadable] means the files exist but this process cannot open them.
     */
    private fun probeIl2Cpp(appInfo: ApplicationInfo): Il2CppStatus {
        val apkPaths = linkedSetOf<String>()
        appInfo.sourceDir?.let { apkPaths.add(it) }
        appInfo.publicSourceDir?.let { apkPaths.add(it) }
        appInfo.splitSourceDirs?.forEach { apkPaths.add(it) }
        appInfo.splitPublicSourceDirs?.forEach { apkPaths.add(it) }

        var openedAnApk = false
        var blocked = apkPaths.isEmpty()

        for (path in apkPaths) {
            when (apkContainsIl2Cpp(path)) {
                true -> {
                    BepInExLog.i("libil2cpp.so found in $path")
                    return Il2CppStatus.Found
                }
                false -> openedAnApk = true
                null -> blocked = true
            }
        }

        for (dir in nativeLibDirs(appInfo)) {
            val so = File(dir, "libil2cpp.so")
            if (so.exists()) {
                BepInExLog.i("libil2cpp.so found in ${so.path}")
                return Il2CppStatus.Found
            }
            if (dir.exists() && !dir.canRead()) blocked = true
        }

        return if (openedAnApk && !blocked) Il2CppStatus.Absent else Il2CppStatus.Unreadable
    }

    /** true = found, false = opened and missing, null = could not read. */
    private fun apkContainsIl2Cpp(apkPath: String): Boolean? {
        val file = File(apkPath)
        if (!file.exists()) return null
        return try {
            ZipFile(file).use { zip ->
                UNITY_ABIS.any { abi ->
                    val entry = zip.getEntry("lib/$abi/libil2cpp.so")
                    entry != null && !entry.isDirectory
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot scan APK: $apkPath — ${e.message}")
            null
        }
    }

    private fun nativeLibDirs(appInfo: ApplicationInfo): List<File> {
        val dirs = linkedSetOf<File>()
        val nativeDir = appInfo.nativeLibraryDir?.takeIf { it.isNotEmpty() }?.let { File(it) }
        nativeDir?.let { dirs.add(it) }
        nativeDir?.parentFile?.let { parent ->
            dirs.add(parent)
            NATIVE_LIB_DIR_NAMES.forEach { name -> dirs.add(File(parent, name)) }
            parent.listFiles()?.forEach { child ->
                if (child.isDirectory) dirs.add(child)
            }
        }
        return dirs.toList()
    }

    fun invalidateCache() {
        cachedGames = null
    }
}
