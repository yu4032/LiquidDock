package com.hellovoid.liquiddock;

import android.graphics.RectF;
import android.view.View;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Hides only the animated Dock icon glass, then restores it with a short fade. */
final class DockIconAnimationGlassHook {
    private static final String TAG = "[DC][DockIconAnimationGlass]";
    private static final Map<View, Boolean> SOURCE_RETURNED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private DockIconAnimationGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        boolean shortcut = installShortcutVisibilityHook(classLoader);
        boolean lateStop = installLateBackAnimStopGuard(classLoader);
        boolean view2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconView2");
        boolean layer2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconLayer2");
        installed = shortcut && lateStop && (view2 || layer2);
        if (installed) MainHook.log(TAG + " hooks installed restoreProgress=0.90 fadeMs=450"
                + " lateBackAnimStopGuard=true");
        return installed;
    }

    private static boolean installShortcutVisibilityHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "setAnimTargetVisibility",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (GlassRuntimeState.isIconEnabled()
                                && owner instanceof View && args.length > 0 && args[0] instanceof Number
                                && LauncherGlassHierarchy.isDock((View) owner)) {
                            View host = (View) owner;
                            int visibility = ((Number) args[0]).intValue();
                            synchronized (SOURCE_RETURNED) {
                                if (visibility == View.VISIBLE) {
                                    SOURCE_RETURNED.put(host, Boolean.TRUE);
                                } else {
                                    SOURCE_RETURNED.remove(host);
                                }
                            }
                            if (visibility == View.VISIBLE) {
                                DockGlassItemRegistry.endLaunchAnimation(host);
                            }
                        }
                        return result;
                    }, int.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " visibility hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installLateBackAnimStopGuard(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "onBackAnimStop",
                    chain -> {
                        Object owner = chain.getThisObject();
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (!GlassRuntimeState.isIconEnabled() || !(owner instanceof View)) {
                            return result;
                        }
                        View host = (View) owner;
                        if (!LauncherGlassHierarchy.isDock(host)) return result;

                        boolean sourceWasReturned;
                        synchronized (SOURCE_RETURNED) {
                            sourceWasReturned = Boolean.TRUE.equals(SOURCE_RETURNED.remove(host));
                        }
                        if (!sourceWasReturned) return result;

                        try {
                            HookUtil.findMethodExact(host.getClass(),
                                    "setIconVisibility", new Class<?>[]{int.class})
                                    .invoke(host, View.VISIBLE);
                            MainHook.log(TAG + " rejected stale onBackAnimStop hide target="
                                    + host.getClass().getSimpleName());
                        } catch (Throwable error) {
                            MainHook.log(TAG + " late onBackAnimStop restore unavailable target="
                                    + host.getClass().getSimpleName() + ": " + error);
                        }
                        return result;
                    });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " late onBackAnimStop hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installFloatingProxyHook(ClassLoader classLoader, String className) {
        try {
            HookUtil.hookMethod(classLoader, className, "update",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        if (GlassRuntimeState.isIconEnabled()
                                && args.length == 10 && args[0] instanceof RectF
                                && args[1] instanceof RectF && args[2] instanceof Number
                                && args[6] instanceof Boolean
                                && !((Boolean) args[6])) {
                            Object target = HookUtil.invoke(chain.getThisObject(), "getAnimTarget");
                            if (target instanceof View
                                    && LauncherGlassHierarchy.isDock((View) target)) {
                                DockGlassItemRegistry.observeLaunchAnimationFrame(
                                        (View) target, ((Number) args[2]).floatValue());
                            }
                        }
                        return result;
                    }, RectF.class, RectF.class,
                    float.class, float.class, float.class,
                    boolean.class, boolean.class, boolean.class,
                    float.class, boolean.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " proxy hook unavailable class=" + className + ": " + error);
            return false;
        }
    }
}
