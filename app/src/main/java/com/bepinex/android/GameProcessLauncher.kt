package com.bepinex.android

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Unity, CoreCLR and native detours have process lifetime. Never reuse :game. */
object GameProcessLauncher {
    private val launching = AtomicBoolean(false)
    val isRestarting: Boolean get() = launching.get()

    suspend fun launch(
        activity: Activity,
        packageName: String,
        modpackName: String?,
        prepareRuntime: suspend () -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main.immediate) {
        // MainActivity and shortcut launches share this gate in the launcher process.
        if (!launching.compareAndSet(false, true)) return@withContext false
        try {
            withContext(Dispatchers.IO) {
                stopPreviousGame(activity.applicationContext)
                prepareRuntime()
            }
            if (activity.isFinishing || activity.isDestroyed) return@withContext false
            activity.startActivity(Intent(activity, BootstrapActivity::class.java).apply {
                // Discard Android's saved activities for the previous native runtime.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(BootstrapActivity.EXTRA_TARGET_PACKAGE, packageName)
                modpackName?.let { putExtra(BootstrapActivity.EXTRA_ACTIVE_MODPACK, it) }
            })
            true
        } finally {
            launching.set(false)
        }
    }

    private suspend fun stopPreviousGame(context: Context) {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val gameProcessName = "${context.packageName}:game"
        fun gameProcesses() = checkNotNull(manager.runningAppProcesses) {
            "Cannot inspect the previous game process"
        }.filter { it.uid == Process.myUid() && it.processName == gameProcessName }

        // Kill only our own named child, never the launcher or the installed game.
        gameProcesses().forEach { Process.killProcess(it.pid) }
        repeat(100) {
            if (gameProcesses().isEmpty()) return
            delay(50)
        }
        error("Previous game process did not exit; refusing to reuse its runtime")
    }
}
