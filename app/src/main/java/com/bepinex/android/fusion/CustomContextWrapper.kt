package com.bepinex.android.fusion

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.app.Activity
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import java.io.File

/** Routes game resources, launcher storage, and Activity services separately. */
class CustomContextWrapper(
    gameContext: Context,
    private val filesContext: Context,
    private val windowContext: Context,
    private val ownerActivity: Activity
) : ContextWrapper(gameContext) {
    init {
        applicationInfo.dataDir = filesContext.applicationInfo.dataDir
        applicationInfo.nativeLibraryDir = ""
    }

    override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences =
        filesContext.getSharedPreferences(name, mode)

    override fun getFilesDir(): File = filesContext.filesDir
    override fun getCacheDir(): File = filesContext.cacheDir
    override fun getExternalCacheDir(): File? = filesContext.externalCacheDir
    override fun getExternalCacheDirs(): Array<File> = filesContext.externalCacheDirs
    override fun getExternalFilesDir(type: String?): File? = filesContext.getExternalFilesDir(type)
    override fun getExternalFilesDirs(type: String?): Array<File> = filesContext.getExternalFilesDirs(type)

    override fun getSystemService(name: String): Any? = windowContext.getSystemService(name)
    override fun getDisplay(): Display? {
        // Activity.getDisplay() and ContextWrapper.getDisplay() both forward to
        // the base context. attachBaseContext installs this wrapper as that base,
        // so calling the activity or super here recurses until the stack overflows.
        displayOf(windowContext)?.let { return it }
        val manager = windowContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        return manager?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    private fun displayOf(start: Context): Display? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        var current: Context? = start
        val seen = HashSet<Context>()
        while (current != null && seen.add(current)) {
            if (current === this || current === ownerActivity) return null
            if (current !is ContextWrapper) return current.display
            val base = current.baseContext
            if (base == null || base === current || base === this || base === ownerActivity) return null
            current = base
        }
        return null
    }

    override fun getApplicationContext(): Context = filesContext.applicationContext
    override fun getObbDir(): File? = filesContext.obbDir
    override fun getObbDirs(): Array<File> = filesContext.obbDirs
}
