package com.hellovoid.liquiddock;

import android.graphics.RectF;
import android.view.View;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Hides only the animated Dock icon glass, then restores it with a short fade. */
final class DockIconAnimationGlassHook {
    private static final String TAG = "[DC][DockIconAnimationGlass]";
    private static final float FINAL_PROGRESS = 1.0f;
    private static final Map<View, Boolean> TAIL_SOURCE_OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private DockIconAnimationGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        boolean shortcut = installShortcutVisibilityHook(classLoader);
        boolean iconTrace = installIconVisibilityTraceHook(classLoader);
        boolean backStop = installBackAnimStopHandoffGuard(classLoader);
        boolean view2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconView2");
        boolean layer2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconLayer2");
        installed = shortcut && backStop && (view2 || layer2);
        if (installed) MainHook.log(TAG + " hooks installed restoreProgress=0.90 fadeMs=450"
                + " sourcePrimeProgress=" + FINAL_PROGRESS
                + " directIconTrace=" + iconTrace);
        return installed;
    }

    private static boolean installShortcutVisibilityHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "setAnimTargetVisibility",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object owner = chain.getThisObject();
                        View host = owner instanceof View ? (View) owner : null;
                        Integer visibility = args.length > 0 && args[0] instanceof Number
                                ? ((Number) args[0]).intValue() : null;
                        if (host != null && visibility != null
                                && LauncherGlassHierarchy.isDock(host)) {
                            DockAnimationTrace.sourceEvent(
                                    "setAnimTargetVisibility-pre", host, visibility);
                        }

                        Object result = chain.proceed(args);

                        if (host != null && visibility != null
                                && LauncherGlassHierarchy.isDock(host)) {
                            DockAnimationTrace.sourceEvent(
                                    "setAnimTargetVisibility-post", host, visibility);
                            if (visibility == View.VISIBLE) {
                                TAIL_SOURCE_OWNERS.remove(host);
                                if (GlassRuntimeState.isIconEnabled()) {
                                    DockGlassItemRegistry.endLaunchAnimation(host);
                                }
                                DockAnimationTrace.sourceEvent(
                                        "formal-handoff-complete", host, visibility);
                            } else {
                                TAIL_SOURCE_OWNERS.remove(host);
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

    private static boolean installIconVisibilityTraceHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "setIconVisibility",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object owner = chain.getThisObject();
                        View host = owner instanceof View ? (View) owner : null;
                        Integer visibility = args.length > 0 && args[0] instanceof Number
                                ? ((Number) args[0]).intValue() : null;
                        if (host != null && visibility != null
                                && LauncherGlassHierarchy.isDock(host)) {
                            DockAnimationTrace.sourceEvent(
                                    "setIconVisibility-pre", host, visibility);
                        }
                        Object result = chain.proceed(args);
                        if (host != null && visibility != null
                                && LauncherGlassHierarchy.isDock(host)) {
                            DockAnimationTrace.sourceEvent(
                                    "setIconVisibility-post", host, visibility);
                        }
                        return result;
                    }, int.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " direct icon visibility trace unavailable: " + error);
            return false;
        }
    }

    private static boolean installBackAnimStopHandoffGuard(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "onBackAnimStop",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object owner = chain.getThisObject();
                        View host = owner instanceof View ? (View) owner : null;
                        if (host != null && LauncherGlassHierarchy.isDock(host)) {
                            DockAnimationTrace.sourceEvent("onBackAnimStop-pre", host, null);
                        }
                        Object result = chain.proceed(args);
                        if (host != null) {
                            if (LauncherGlassHierarchy.isDock(host)) {
                                DockAnimationTrace.sourceEvent(
                                        "onBackAnimStop-post-vendor", host, null);
                            }
                            restoreTailSourceAfterBackAnimStop(host);
                            if (LauncherGlassHierarchy.isDock(host)) {
                                DockAnimationTrace.sourceEvent(
                                        "onBackAnimStop-post-guard", host, null);
                            }
                        }
                        return result;
                    });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " back animation stop hook unavailable: " + error);
            return false;
        }
    }

    private static void restoreTailSourceAfterBackAnimStop(View host) {
        if (host == null || !GlassRuntimeState.isIconEnabled()
                || !LauncherGlassHierarchy.isDock(host)) return;
        synchronized (TAIL_SOURCE_OWNERS) {
            if (!TAIL_SOURCE_OWNERS.containsKey(host)) return;
        }
        try {
            HookUtil.findMethodExact(host.getClass(),
                    "setIconVisibility", new Class<?>[]{int.class})
                    .invoke(host, View.VISIBLE);
        } catch (Throwable error) {
            MainHook.log(TAG + " tail source restore unavailable target="
                    + host.getClass().getSimpleName() + ": " + error);
        }
    }

    private static boolean installFloatingProxyHook(ClassLoader classLoader, String className) {
        try {
            HookUtil.hookMethod(classLoader, className, "update",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        View proxy = chain.getThisObject() instanceof View
                                ? (View) chain.getThisObject() : null;
                        boolean traceCandidate = GlassRuntimeState.isIconEnabled()
                                && args.length == 10 && args[0] instanceof RectF
                                && args[1] instanceof RectF && args[2] instanceof Number
                                && args[3] instanceof Number && args[6] instanceof Boolean
                                && !((Boolean) args[6]);
                        float proxyAlpha = traceCandidate
                                ? ((Number) args[2]).floatValue() : Float.NaN;
                        float progress = traceCandidate
                                ? ((Number) args[3]).floatValue() : Float.NaN;

                        Object targetBefore = traceCandidate
                                ? HookUtil.invoke(chain.getThisObject(), "getAnimTarget") : null;
                        if (targetBefore instanceof View
                                && LauncherGlassHierarchy.isDock((View) targetBefore)) {
                            DockAnimationTrace.proxyFrame(
                                    "proxy-pre", proxy, (View) targetBefore, proxyAlpha, progress);
                        }

                        Object result = chain.proceed(args);

                        if (traceCandidate) {
                            Object target = HookUtil.invoke(chain.getThisObject(), "getAnimTarget");
                            if (target instanceof View
                                    && LauncherGlassHierarchy.isDock((View) target)) {
                                DockAnimationTrace.proxyFrame(
                                        "proxy-post", proxy, (View) target, proxyAlpha, progress);
                                primeNativeSourceForHandoff((View) target, progress);
                                DockGlassItemRegistry.observeLaunchAnimationFrame(
                                        (View) target, progress);
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

    private static void primeNativeSourceForHandoff(View target, float progress) {
        if (target == null || !target.isAttachedToWindow() || !Float.isFinite(progress)
                || progress < FINAL_PROGRESS) return;
        synchronized (TAIL_SOURCE_OWNERS) {
            if (TAIL_SOURCE_OWNERS.containsKey(target)) return;
            TAIL_SOURCE_OWNERS.put(target, Boolean.TRUE);
        }
        DockAnimationTrace.sourceEvent("tail-prime-pre", target, View.VISIBLE);
        try {
            HookUtil.findMethodExact(target.getClass(),
                    "setIconVisibility", new Class<?>[]{int.class})
                    .invoke(target, View.VISIBLE);
            DockAnimationTrace.sourceEvent("tail-prime-post", target, View.VISIBLE);
            MainHook.log(TAG + " native source pre-roll target="
                    + target.getClass().getSimpleName() + " progress=" + progress);
        } catch (Throwable error) {
            TAIL_SOURCE_OWNERS.remove(target);
            MainHook.log(TAG + " native source pre-roll unavailable target="
                    + target.getClass().getSimpleName() + ": " + error);
        }
    }
}
