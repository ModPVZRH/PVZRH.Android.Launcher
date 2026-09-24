package com.bepinex.android.fusion

import android.content.Context
import android.os.Looper
import android.widget.Toast
import com.bepinex.android.BepInExLog
import top.canyie.pine.Pine
import top.canyie.pine.callback.MethodHook

/**
 * Keeps Toast window attribution inside our own UID.
 *
 * Game activities run in our process behind [CustomContextWrapper], which extends
 * ContextWrapper(gameContext) and therefore reports the GAME's package from
 * getPackageName()/getOpPackageName() (Unity needs it for Resources.getIdentifier()).
 * When the game shows a Toast with that context, Android 14 validates that the
 * window's package belongs to the calling UID and rejects it:
 *
 *   SecurityException: Package <game> not in UID <our uid>
 *
 * The exception escapes from Toast$TN.handleShow inside Looper.loop, so it cannot be
 * caught by the game and kills the whole process.
 *
 * Fix: whenever a Toast is created with a context whose package is not ours, redirect
 * it to the real host context so the window is attributed to a package we own.
 */
object ToastHooks {

    @Volatile
    private var hostContext: Context? = null

    private var installed = false

    /** Which makeText overload is being hooked, and where its payload sits. */
    private class Variant(
        val params: Array<Class<*>>,
        val hasLooper: Boolean,
        val resIdVariant: Boolean
    ) {
        /** Index of the text/resId argument; duration follows it. */
        val contentIndex: Int get() = if (hasLooper) 2 else 1
    }

    private fun variants() = arrayOf(
        Variant(
            arrayOf(Context::class.java, CharSequence::class.java, Int::class.javaPrimitiveType!!),
            hasLooper = false, resIdVariant = false
        ),
        Variant(
            arrayOf(Context::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            hasLooper = false, resIdVariant = true
        ),
        Variant(
            arrayOf(Context::class.java, Looper::class.java, CharSequence::class.java, Int::class.javaPrimitiveType!!),
            hasLooper = true, resIdVariant = false
        ),
        Variant(
            arrayOf(Context::class.java, Looper::class.java, Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!),
            hasLooper = true, resIdVariant = true
        )
    )

    fun installHooks(context: Context) {
        if (installed) {
            BepInExLog.d("ToastHooks already installed")
            return
        }
        val host = context.applicationContext ?: context
        hostContext = host
        val hostPackage = host.packageName

        var hooked = 0
        for (variant in variants()) {
            val method = try {
                Toast::class.java.getDeclaredMethod("makeText", *variant.params)
            } catch (e: NoSuchMethodException) {
                continue // overload absent on this API level
            }
            try {
                Pine.hook(method, object : MethodHook() {
                    override fun beforeCall(callFrame: Pine.CallFrame) {
                        redirect(callFrame, variant, hostPackage)
                    }
                })
                hooked++
            } catch (t: Throwable) {
                BepInExLog.w("Toast hook failed for ${variant.params.size}-arg overload: ${t.message}")
            }
        }

        installed = hooked > 0
        BepInExLog.i("ToastHooks installed: $hooked overload(s), host=$hostPackage")
    }

    private fun redirect(callFrame: Pine.CallFrame, variant: Variant, hostPackage: String) {
        try {
            val host = hostContext ?: return
            val ctx = callFrame.args[0] as? Context ?: return
            if (ctx.packageName == hostPackage) return // already ours, nothing to do

            if (!variant.resIdVariant) {
                // Text is already resolved: swapping the context is enough, and the
                // modified args are passed straight to the original method.
                callFrame.args[0] = host
                BepInExLog.d("Toast context rewritten: ${ctx.packageName} -> $hostPackage")
                return
            }

            // resId variant: the id only resolves against the GAME's resources, so
            // resolve it here and hand back a Toast built on the host context.
            val contentIndex = variant.contentIndex
            val resId = callFrame.args[contentIndex] as? Int ?: return
            val duration = callFrame.args[contentIndex + 1] as? Int ?: Toast.LENGTH_SHORT
            val text = ctx.getString(resId)
            val looper = if (variant.hasLooper) callFrame.args[1] as? Looper else null

            val rebuilt = if (looper != null) {
                makeTextWithLooper(host, looper, text, duration)
                    ?: Toast.makeText(host, text, duration)
            } else {
                Toast.makeText(host, text, duration)
            }
            callFrame.setResult(rebuilt)
            BepInExLog.d("Toast rebuilt for ${ctx.packageName}: \"$text\"")
        } catch (t: Throwable) {
            // Never let our hook break the game — fall back to original behaviour.
            BepInExLog.w("Toast redirect failed: ${t.message}")
        }
    }

    /**
     * The (Context, Looper, CharSequence, int) overload is API 30+ and is not
     * visible at compile time here, so reach it reflectively to preserve the
     * caller's Looper. Returns null when unavailable.
     */
    private fun makeTextWithLooper(
        host: Context,
        looper: Looper,
        text: CharSequence,
        duration: Int
    ): Toast? = try {
        val method = Toast::class.java.getDeclaredMethod(
            "makeText",
            Context::class.java,
            Looper::class.java,
            CharSequence::class.java,
            Int::class.javaPrimitiveType
        )
        method.invoke(null, host, looper, text, duration) as? Toast
    } catch (t: Throwable) {
        null
    }
}
