package com.bepinex.android.fusion

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.bepinex.android.BepInExLog
import com.bepinex.android.settings.AppSettings
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.util.concurrent.CountDownLatch

/**
 * Pine hooks on UnityPlayer constructor(s).
 *
 * The Activity is wrapped at attachBaseContext. Constructor arguments remain
 * real Activity objects because Unity uses instanceof Activity before nativeInit.
 *
 * @see UnityPlayerHooks.java in FusionCore main branch
 */
object UnityPlayerHooks {

    private const val TAG = "UnityPlayerHooks"

    private val unityPlayerClassNames = arrayOf(
        "com.unity3d.player.UnityPlayer",
        "com.unity3d.player.UnityPlayerForGameActivity",
        "com.unity3d.player.UnityPlayerForActivityOrService"
    )

    /**
     * Install Pine hooks on all UnityPlayer constructors that take a Context.
     *
     * @param gameContext The game's package context (from createPackageContext)
     */
    fun installHooks(gameContext: Context, fusionApplication: Context) {
        val classLoader = gameContext.classLoader
            ?: throw IllegalStateException("Game ClassLoader is null")

        // Find UnityPlayer class
        var unityPlayerClass: Class<*>? = null
        for (className in unityPlayerClassNames) {
            try {
                unityPlayerClass = classLoader.loadClass(className)
                BepInExLog.i("Found UnityPlayer class: ${unityPlayerClass.name}")
                break
            } catch (e: ClassNotFoundException) {
                // Try next
            }
        }

        if (unityPlayerClass == null) {
            throw IllegalStateException(
                "UnityPlayer class not found. Tried: ${unityPlayerClassNames.joinToString()}")
        }

        // Find constructors that take Context as first parameter
        val constructors = unityPlayerClass.declaredConstructors.filter { ctor ->
            ctor.parameterTypes.isNotEmpty() &&
            Context::class.java.isAssignableFrom(ctor.parameterTypes[0])
        }

        if (constructors.isEmpty()) {
            throw IllegalStateException(
                "No UnityPlayer constructor taking Context found in ${unityPlayerClass.name}")
        }

        BepInExLog.i("Found ${constructors.size} UnityPlayer constructor(s) to hook")

        installActivityContextHook(classLoader, gameContext, fusionApplication)

        // Find activity field on UnityPlayer (usually m_Activity)
        val activityFields = unityPlayerClass.declaredFields.filter { field ->
            field.type == Activity::class.java
        }

        // Hook each constructor
        for (constructor in constructors) {
            BepInExLog.i("Hooking UnityPlayer constructor: $constructor")

            Pine.hook(constructor, object : MethodHook() {
                private var activity: Activity? = null
                private var loadingOverlay: View? = null

                override fun beforeCall(callFrame: Pine.CallFrame) {
                    try {
                        val firstArg = callFrame.args[0]
                        if (firstArg !is Activity) {
                            BepInExLog.w("First arg is not Activity: ${firstArg?.javaClass?.name}")
                            return
                        }

                        activity = firstArg
                        loadingOverlay = showLoadingOverlay(firstArg, "Injecting BepInEx...")

                        BepInExLog.i("UnityPlayer constructor argument preserved as Activity: ${firstArg.javaClass.name}")
                    } catch (e: Exception) {
                        BepInExLog.e("Failed to wrap context in UnityPlayer hook", e)
                    }
                }

                override fun afterCall(callFrame: Pine.CallFrame) {
                    val act = activity ?: return

                    // Hide loading overlay
                    hideLoadingOverlay(act, loadingOverlay)

                    // Set activity field on UnityPlayer instance
                    for (field in activityFields) {
                        try {
                            field.isAccessible = true
                            if (field.type == Activity::class.java) {
                                field.set(callFrame.thisObject, act)
                                BepInExLog.i("Set UnityPlayer.${field.name} = activity")
                            } else {
                                BepInExLog.d("Skip UnityPlayer.${field.name}: type ${field.type.name} not assignable from ${act.javaClass.name}")
                            }
                        } catch (e: Exception) {
                            BepInExLog.e("Failed to set UnityPlayer.${field.name}", e)
                        }
                    }

                    // Launch log overlay after game is fully loaded
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val enabled = com.bepinex.android.settings.AppSettings
                                .isFloatingLogInGameEnabled(act)
                            if (enabled) {
                                com.bepinex.android.log.GameLogOverlay.show(act, gameContext.packageName)
                                BepInExLog.i("Log overlay attached to game window")
                            }
                        } catch (e: Exception) {
                            BepInExLog.e("Failed to launch log overlay", e)
                        }
                    }, 2000)
                }
            })
        }
    }

    private fun installActivityContextHook(
        gameClassLoader: ClassLoader,
        gameContext: Context,
        fusionApplication: Context
    ) {
        val method = Activity::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
            .apply { isAccessible = true }
        Pine.hook(method, object : MethodHook() {
            override fun beforeCall(frame: Pine.CallFrame) {
                val activity = frame.thisObject as? Activity ?: return
                if (activity.javaClass.classLoader !== gameClassLoader || frame.args.isEmpty()) return
                val original = frame.args[0] as? Context ?: return
                if (original is CustomContextWrapper) return
                frame.args[0] = CustomContextWrapper(
                    gameContext, fusionApplication.applicationContext, original
                )
                BepInExLog.i("attachBaseContext wrapped ${activity.javaClass.name}")
            }
        })
        BepInExLog.i("Activity.attachBaseContext hook installed")
    }

    private fun showLoadingOverlay(activity: Activity, statusText: String): View? {
        var overlay: View? = null

        val createOverlay = Runnable {
            val decor = activity.window.decorView as ViewGroup

            val overlayView = FrameLayout(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                setBackgroundColor(0x88000000.toInt())
            }

            val container = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(48, 48, 48, 48)
            }

            val progressBar = ProgressBar(activity).apply {
                isIndeterminate = true
            }

            val status = TextView(activity).apply {
                text = statusText
                setTextColor(Color.WHITE)
                textSize = 16f
                setPadding(0, 24, 0, 0)
            }

            container.addView(progressBar)
            container.addView(status)

            val containerParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            overlayView.addView(container, containerParams)
            decor.addView(overlayView)
            overlay = overlayView
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            createOverlay.run()
        } else {
            val latch = CountDownLatch(1)
            activity.runOnUiThread {
                createOverlay.run()
                latch.countDown()
            }
            latch.await()
        }

        return overlay
    }

    private fun hideLoadingOverlay(activity: Activity, overlay: View?) {
        if (overlay == null) return
        activity.runOnUiThread {
            val parent = overlay.parent as? ViewGroup
            parent?.removeView(overlay)
        }
    }
}
