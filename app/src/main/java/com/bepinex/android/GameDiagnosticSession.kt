package com.bepinex.android

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import java.util.Properties

object GameDiagnosticSession {
    fun start(context: Context, packageName: String, modpack: String?) {
        runCatching {
            val memory = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
            val game = context.packageManager.getPackageInfo(packageName, 0)
            val data = Properties().apply {
                setProperty("startedAt", System.currentTimeMillis().toString())
                setProperty("pid", Process.myPid().toString())
                setProperty("process", "${context.packageName}:game")
                setProperty("game", packageName)
                setProperty("modpack", modpack ?: "vanilla")
                setProperty("gameVersion", "${game.versionName} (${game.longVersionCode})")
                setProperty("gameUpdatedAt", game.lastUpdateTime.toString())
                setProperty("launcherVersion", context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty())
                setProperty("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                setProperty("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                setProperty("abi", Build.SUPPORTED_ABIS.joinToString())
                setProperty("memoryAvailableAtStartBytes", memory.availMem.toString())
                setProperty("memoryTotalBytes", memory.totalMem.toString())
            }
            val file = BepInExPaths.getGameSessionFile(packageName)
            file.parentFile?.mkdirs()
            file.writer(Charsets.UTF_8).use { data.store(it, "Game diagnostic session") }
        }.onFailure { BepInExLog.w("Could not record game session: ${it.message}") }
    }

    fun read(packageName: String): Properties = Properties().apply {
        runCatching {
            BepInExPaths.getGameSessionFile(packageName).reader(Charsets.UTF_8).use { load(it) }
        }
    }
}
