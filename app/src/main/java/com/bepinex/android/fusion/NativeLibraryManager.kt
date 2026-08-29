package com.bepinex.android.fusion

import android.util.Log
import com.bepinex.android.BepInExLog
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.lang.reflect.Method

/** Hooks ClassLoader.findLibrary() to redirect native library loading to the correct paths. */
object NativeLibraryManager {

    private const val TAG = "NativeLibraryManager"

    /** Libraries provided by OUR APK (loaded in game ClassLoader namespace) */
    private val fusionLibraries = mutableListOf<String>()

    /** Libraries from the GAME's APK (redirected to gameLibraryDirectory) */
    private val gameLibraries = mutableListOf<String>()

    /** Unity data libraries (il2cpp, unity) from appDataDirectory */
    private val dataLibraries = mutableListOf<String>()

    /**
     * Register a library name that should be loaded from OUR APK's lib directory.
     */
    fun addFusionLibrary(name: String) {
        fusionLibraries.add(name)
        BepInExLog.i("Fusion library registered: $name")
    }

    /**
     * Register a library name that should be loaded from the GAME's lib directory.
     */
    fun addGameLibrary(name: String) {
        gameLibraries.add(name)
    }

    /** Register a data library (il2cpp, unity) that should be loaded from appDataDirectory. */
    fun addDataLibrary(name: String) {
        dataLibraries.add(name)
        BepInExLog.i("Data library registered: $name")
    }

    /**
     * Install the findLibrary Pine hook.
     * Must be called before UnityPlayer is constructed.
     */
    fun setupLibraryHooks(config: FusionConfig) {
        val findLibraryMethod = findFindLibraryMethod()
            ?: throw IllegalStateException("Cannot find ClassLoader.findLibrary method for hooking")

        BepInExLog.i("Hooking findLibrary: ${findLibraryMethod.declaringClass.name}.${findLibraryMethod.name}")

        Pine.hook(findLibraryMethod, object : MethodHook() {
            override fun beforeCall(callFrame: Pine.CallFrame) {
                val libName = callFrame.args[0].toString()
                BepInExLog.i("findLibrary: $libName")  // INFO level for debugging

                // Check fusion libraries first
                for (fusionLib in fusionLibraries) {
                    if (libName == fusionLib) {
                        val path = "${config.appLibraryDirectory}/lib${libName}.so"
                        BepInExLog.i("findLibrary REDIRECT [$libName] → fusion: $path")
                        callFrame.result = path
                        return
                    }
                }

                // Check data libraries
                for (dataLib in dataLibraries) {
                    if (libName == dataLib) {
                        val path = "${config.appDataDirectory}/lib${libName}.so"
                        BepInExLog.i("findLibrary REDIRECT [$libName] → data: $path")
                        callFrame.result = path
                        return
                    }
                }

                // Check game libraries
                for (gameLib in gameLibraries) {
                    if (libName == gameLib) {
                        val path = "${config.gameLibraryDirectory}/lib${libName}.so"
                        BepInExLog.i("findLibrary REDIRECT [$libName] → game: $path")
                        callFrame.result = path
                        return
                    }
                }
            }

            override fun afterCall(callFrame: Pine.CallFrame) {
                if (callFrame.hasThrowable()) {
                    Log.w(TAG, "findLibrary threw for ${callFrame.args[0]}", callFrame.throwable)
                }
            }
        })
    }

    /**
     * Find ClassLoader.findLibrary(String) via reflection.
     */
    private fun findFindLibraryMethod(): Method? {
        var clazz: Class<*>? = NativeLibraryManager::class.java.classLoader?.javaClass

        while (clazz != null) {
            try {
                val method = clazz.getDeclaredMethod("findLibrary", String::class.java)
                method.isAccessible = true
                BepInExLog.i("Found findLibrary in ${clazz.name}")
                return method
            } catch (e: NoSuchMethodException) {
                clazz = clazz.superclass
            }
        }

        return null
    }
}
