package com.bepinex.android.fusion

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.view.Display
import java.io.File

/** Routes game resources, launcher storage, and Activity services separately. */
class CustomContextWrapper(
    gameContext: Context,
    private val filesContext: Context,
    private val windowContext: Context
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
    override fun getDisplay(): Display? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) windowContext.display else null
    override fun getApplicationContext(): Context = filesContext.applicationContext
    override fun getObbDir(): File? = filesContext.obbDir
    override fun getObbDirs(): Array<File> = filesContext.obbDirs
}
