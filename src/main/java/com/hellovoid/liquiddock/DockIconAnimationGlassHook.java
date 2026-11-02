package com.hellovoid.liquiddock;

import android.graphics.RectF;
import android.view.View;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Coordinates Dock icon glass and the native FloatingIcon source/proxy handoff. */
final class DockIconAnimationGlassHook {
    private static final String TAG = "[DC][DockIconAnimationGlass]";

    private static final Map<View, View> PROXY_TARGETS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> PROXY_CLOSE_TO_HOME =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private DockIconAnimationGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        boolean shortcut = installShortcutVisibilityHook(classLoader);
        boolean backStop = installBackAnimStopHandoffGuard(classLoader);
        boolean finish = installFloatingViewFinishHandoffHook(classLoader);
        boolean view2 =
                installFloatingProxyHook(
                        classLoader, "com.miui.home.recents.views.FloatingIconView2", false);
        boolean layer2 =
                installFloatingProxyHook(
                        classLoader, "com.miui.home.recents.views.FloatingIconLayer2", true);
        installed = shortcut && backStop && finish && view2;
        if (installed) {
            MainHook.log(TAG + " hooks installed nativeHandoff=true" + " layer2Observed=" + layer2);
        }
        return installed;
    }

    private static boolean installShortcutVisibilityHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(
                    classLoader,
                    "com.miui.home.launcher.ShortcutIcon",
                    "setAnimTargetVisibility",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object owner = chain.getThisObject();
                        View host = owner instanceof View ? (View) owner : null;
                        Integer visibility =
                                args.length > 0 && args[0] instanceof Number
                                        ? ((Number) args[0]).intValue()
                                        : null;
                        Object result = chain.proceed(args);

                        if (host != null
                                && visibility != null
                                && LauncherGlassHierarchy.isDock(host)
                                && GlassRuntimeState.isEnabled()) {
                            if (GlassRuntimeState.isAnyIconEnabled()) {
                                if (visibility == View.VISIBLE) {
                                    DockGlassItemRegistry.endLaunchAnimation(host);
                                } else {
                                    DockGlassItemRegistry.beginLaunchAnimation(host);
                                }
                            }
                            // Workspace widgets/folders still require the launch barrier even
                            // when Dock icon glass itself is disabled.
                            LauncherGlassStaticLayer.onDockNativeVisibilityChanged(
                                    visibility == View.VISIBLE);
                        }
                        return result;
                    },
                    int.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " visibility hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installBackAnimStopHandoffGuard(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(
                    classLoader,
                    "com.miui.home.launcher.ShortcutIcon",
                    "onBackAnimStop",
                    chain -> {
                        Object owner = chain.getThisObject();
                        View host = owner instanceof View ? (View) owner : null;
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        // OEM writes its hidden placeholder here, sometimes after showIcon().
                        // Once the proxy has retired the native source is the sole visual owner.
                        if (host != null
                                && LauncherGlassHierarchy.isDock(host)
                                && GlassRuntimeState.isAnyIconEnabled()
                                && !DockGlassItemRegistry.animationSample(host).proxyActive) {
                            HookUtil.tryInvoke(host, "setIconVisibility", View.VISIBLE);
                        }
                        return result;
                    });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " back animation stop hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installFloatingViewFinishHandoffHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(
                    classLoader,
                    "com.miui.home.recents.views.FloatingIconView2",
                    "finishImmediately",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object owner = chain.getThisObject();
                        View proxy = owner instanceof View ? (View) owner : null;
                        View target = proxy != null ? proxyTarget(proxy) : null;
                        if (proxy == null
                                || target == null
                                || !LauncherGlassHierarchy.isDock(target)) {
                            return chain.proceed(args);
                        }

                        boolean returning = isCloseToHomeProxy(proxy);
                        Object result = chain.proceed(args);
                        DockGlassItemRegistry.endProxyGeometry(target);
                        if (returning) {
                            HookUtil.tryInvoke(target, "setIconVisibility", View.VISIBLE);
                            LauncherGlassStaticLayer.onDockProxyReturnFinished();
                        }
                        forgetProxyTarget(proxy);
                        return result;
                    },
                    boolean.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " finish handoff hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installFloatingProxyHook(
            ClassLoader classLoader, String className, boolean useRotationRect) {
        try {
            HookUtil.hookMethod(
                    classLoader,
                    className,
                    "update",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        View proxy =
                                chain.getThisObject() instanceof View
                                        ? (View) chain.getThisObject()
                                        : null;
                        boolean validFrame =
                                args.length == 10
                                        && args[0] instanceof RectF
                                        && args[1] instanceof RectF
                                        && args[2] instanceof Number
                                        && args[3] instanceof Number
                                        && args[6] instanceof Boolean;
                        boolean closeToHome = validFrame && !((Boolean) args[6]);
                        float proxyAlpha = validFrame ? ((Number) args[2]).floatValue() : Float.NaN;
                        float progress = validFrame ? ((Number) args[3]).floatValue() : Float.NaN;

                        Object result = chain.proceed(args);

                        if (validFrame) {
                            HookUtil.InvocationResult<Object> targetResult =
                                    HookUtil.tryInvoke(chain.getThisObject(), "getAnimTarget");
                            Object target = targetResult.succeeded() ? targetResult.value() : null;
                            if (target instanceof View
                                    && LauncherGlassHierarchy.isDock((View) target)) {
                                View dockTarget = (View) target;
                                if (proxy != null) {
                                    rememberProxyTarget(proxy, dockTarget, closeToHome);
                                }

                                boolean drawIcon;
                                if (useRotationRect) {
                                    try {
                                        drawIcon =
                                                HookUtil.getBooleanField(
                                                        chain.getThisObject(), "mIsDrawIcon");
                                    } catch (Throwable ignored) {
                                        drawIcon = false;
                                    }
                                } else {
                                    HookUtil.InvocationResult<Object> drawResult =
                                            HookUtil.tryInvoke(chain.getThisObject(), "isDrawIcon");
                                    Object draw =
                                            drawResult.succeeded() ? drawResult.value() : null;
                                    drawIcon = draw instanceof Boolean && ((Boolean) draw);
                                }
                                boolean proxyVisible =
                                        useRotationRect
                                                ? LauncherGlassProxyVisibility.isLayer2Visible(
                                                        proxyAlpha, drawIcon)
                                                : LauncherGlassProxyVisibility.isView2Visible(
                                                        proxyAlpha, drawIcon);
                                boolean publishProxyGeometry =
                                        LauncherGlassProxyVisibility.shouldPublishGeometry(
                                                closeToHome, proxyVisible);

                                if (!publishProxyGeometry) {
                                    DockGlassItemRegistry.holdProxyHidden(dockTarget);
                                } else {
                                    RectF proxyRect = (RectF) args[useRotationRect ? 1 : 0];
                                    View animationRoot = proxy != null ? proxy.getRootView() : null;
                                    if (animationRoot == null
                                            && dockTarget.getContext()
                                                    instanceof android.app.Activity) {
                                        animationRoot =
                                                ((android.app.Activity) dockTarget.getContext())
                                                        .getWindow()
                                                        .getDecorView()
                                                        .getRootView();
                                    }
                                    if (animationRoot != null) {
                                        android.graphics.Matrix toScreen =
                                                new android.graphics.Matrix();
                                        animationRoot.transformMatrixToGlobal(toScreen);
                                        float[] origin = new float[] {0f, 0f};
                                        toScreen.mapPoints(origin);
                                        int[] screen = new int[2];
                                        animationRoot.getLocationOnScreen(screen);
                                        toScreen.postTranslate(
                                                screen[0] - origin[0], screen[1] - origin[1]);
                                        RectF screenRect = new RectF(proxyRect);
                                        toScreen.mapRect(screenRect);
                                        DockGlassItemRegistry.updateProxyGeometry(
                                                dockTarget,
                                                screenRect.left,
                                                screenRect.top,
                                                screenRect.right,
                                                screenRect.bottom);
                                    }
                                }

                                if (GlassRuntimeState.isEnabled()) {
                                    LauncherGlassStaticLayer.onDockProxyFrame(
                                            closeToHome, progress);
                                }
                                if (GlassRuntimeState.isAnyIconEnabled()) {
                                    if (closeToHome) {
                                        DockGlassItemRegistry.observeHomeReturnProgress(
                                                dockTarget, progress);
                                    } else {
                                        // Same retarget boundary as Workspace (#434).
                                        DockGlassItemRegistry.cancelHomeReturn(dockTarget);
                                    }
                                }
                            }
                        }
                        return result;
                    },
                    RectF.class,
                    RectF.class,
                    float.class,
                    float.class,
                    float.class,
                    boolean.class,
                    boolean.class,
                    boolean.class,
                    float.class,
                    boolean.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " proxy hook unavailable class=" + className + ": " + error);
            return false;
        }
    }

    private static void rememberProxyTarget(View proxy, View target, boolean closeToHome) {
        synchronized (PROXY_TARGETS) {
            PROXY_TARGETS.put(proxy, target);
        }
        synchronized (PROXY_CLOSE_TO_HOME) {
            PROXY_CLOSE_TO_HOME.put(proxy, closeToHome);
        }
    }

    private static View proxyTarget(View proxy) {
        synchronized (PROXY_TARGETS) {
            return PROXY_TARGETS.get(proxy);
        }
    }

    private static boolean isCloseToHomeProxy(View proxy) {
        synchronized (PROXY_CLOSE_TO_HOME) {
            return Boolean.TRUE.equals(PROXY_CLOSE_TO_HOME.get(proxy));
        }
    }

    private static void forgetProxyTarget(View proxy) {
        synchronized (PROXY_TARGETS) {
            PROXY_TARGETS.remove(proxy);
        }
        synchronized (PROXY_CLOSE_TO_HOME) {
            PROXY_CLOSE_TO_HOME.remove(proxy);
        }
    }
}
