package com.bepinex.android.fusion;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;

import com.bepinex.android.StubActivity;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

import java.lang.reflect.Method;

/** Keeps dynamically loaded game activities inside the launcher process. */
public final class InstrumentationHooks {
    private static final String LAUNCHER_PACKAGE = "com.pvzrh.android.launcher";
    public static final String EXTRA_IS_DYNAMIC_ACTIVITY = "fusioncore.is_dynamic_activity";
    public static final String EXTRA_ORIGINAL_INTENT = "fusioncore.original_intent";
    public static final String EXTRA_TARGET_ORIENTATION = "fusioncore.target_orientation";

    private static boolean installed;

    private InstrumentationHooks() { }

    public static void install() {
        if (installed) return;

        hookMethods("execStartActivity", new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                if (frame.args == null) return;
                for (int i = 0; i < frame.args.length; i++) {
                    if (!(frame.args[i] instanceof Intent)) continue;
                    Intent intent = (Intent) frame.args[i];
                    if (intent.getComponent() == null || isDynamic(intent)) return;
                    Intent wrapped = new Intent(intent);
                    wrapped.putExtra(EXTRA_IS_DYNAMIC_ACTIVITY, true);
                    wrapped.putExtra(EXTRA_ORIGINAL_INTENT, intent);
                    wrapped.setComponent(new ComponentName(
                            LAUNCHER_PACKAGE, StubActivity.class.getName()));
                    frame.args[i] = wrapped;
                    Log.i("InstrumentationHooks", "execStartActivity: redirected "
                            + intent.getComponent().getClassName() + " to StubActivity");
                    return;
                }
            }
        });

        hookMethods("newActivity", new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                if (frame.args == null) return;
                int intentIndex = -1;
                int classNameIndex = -1;
                for (int i = 0; i < frame.args.length; i++) {
                    if (frame.args[i] instanceof Intent) intentIndex = i;
                    if (frame.args[i] instanceof String) classNameIndex = i;
                }
                if (intentIndex < 0 || classNameIndex < 0) return;
                Intent current = (Intent) frame.args[intentIndex];
                if (!isDynamic(current)) return;
                Intent original = current.getParcelableExtra(EXTRA_ORIGINAL_INTENT);
                if (original == null || original.getComponent() == null) return;
                frame.args[intentIndex] = original;
                frame.args[classNameIndex] = original.getComponent().getClassName();
                Log.i("InstrumentationHooks", "newActivity: restored "
                        + original.getComponent().getClassName());
            }
        });

        MethodHook orientationHook = new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame frame) {
                if (!(frame.thisObject instanceof Activity)) return;
                Activity activity = (Activity) frame.thisObject;
                Intent intent = activity.getIntent();
                if (intent == null) return;
                int orientation = intent.getIntExtra(EXTRA_TARGET_ORIENTATION,
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                if (orientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    activity.setRequestedOrientation(orientation);
                }
            }
        };
        try {
            Pine.hook(Activity.class.getDeclaredMethod("onCreate", Bundle.class), orientationHook);
            Pine.hook(Activity.class.getDeclaredMethod("onResume"), orientationHook);
        } catch (Exception ignored) { }
        installed = true;
    }

    private static void hookMethods(String name, MethodHook hook) {
        for (Method method : Instrumentation.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) Pine.hook(method, hook);
        }
    }

    private static boolean isDynamic(Intent intent) {
        return intent != null && intent.getBooleanExtra(EXTRA_IS_DYNAMIC_ACTIVITY, false);
    }
}
