package com.bepinex.android.fusion

import android.content.Context
import com.bepinex.android.BepInExLog
import java.io.File

/** Writes [FusionConfig] to disk for native libmain.so to read. */
object FusionConfigStore {

    private const val CONFIG_DIR = "bootstrap"
    private const val CONFIG_FILE = "active.cfg"

    /** Write the fusion config to the staged location. */
    fun write(context: Context, config: FusionConfig): File {
        val dir = File(context.filesDir, CONFIG_DIR)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Failed to create config dir: ${dir.absolutePath}")
        }

        val file = File(dir, CONFIG_FILE)
        val content = config.toConfigFile()

        file.writeText(content)

        BepInExLog.i("FusionConfigStore: written to ${file.absolutePath}")
        BepInExLog.i("  content: ${content.replace("\n", " | ")}")

        return file
    }

    /** Get the expected config file path. */
    fun getConfigFile(context: Context): File =
        File(File(context.filesDir, CONFIG_DIR), CONFIG_FILE)
}
