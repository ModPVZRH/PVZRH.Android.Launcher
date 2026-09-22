package com.bepinex.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.bepinex.android.modpack.ModpackManager
import com.bepinex.android.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Transparent activity that handles desktop shortcut intents.
 * Selects the modpack, applies it, and launches the game.
 */
class ShortcutLauncherActivity : Activity() {
    private val launchScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        const val EXTRA_SHORTCUT_PACKAGE = "shortcut_package"
        const val EXTRA_SHORTCUT_MODPACK = "shortcut_modpack"

        fun createIntent(
            context: Context,
            packageName: String,
            modpackName: String
        ): Intent {
            return Intent(context, ShortcutLauncherActivity::class.java).apply {
                putExtra(EXTRA_SHORTCUT_PACKAGE, packageName)
                putExtra(EXTRA_SHORTCUT_MODPACK, modpackName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent?.getStringExtra(EXTRA_SHORTCUT_PACKAGE)
        val modpackName = intent?.getStringExtra(EXTRA_SHORTCUT_MODPACK)

        if (packageName.isNullOrEmpty() || modpackName.isNullOrEmpty()) {
            finish()
            return
        }

        val modpackManager = ModpackManager()
        val previous = AppSettings.getActiveModpack(this, packageName)

        launchScope.launch {
            try {
                GameProcessLauncher.launch(this@ShortcutLauncherActivity, packageName, modpackName) {
                    check(modpackManager.switchRuntime(packageName, previous, modpackName)) {
                        "Failed to switch modpack"
                    }
                    AppSettings.setActiveModpack(this@ShortcutLauncherActivity, packageName, modpackName)
                }
            } catch (error: Exception) {
                BepInExLog.e("Shortcut modpack switch failed", error)
            } finally {
                finish()
            }
        }
    }

    override fun onDestroy() {
        launchScope.cancel()
        super.onDestroy()
    }
}
