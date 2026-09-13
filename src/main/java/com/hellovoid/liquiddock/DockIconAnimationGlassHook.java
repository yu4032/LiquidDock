package com.hellovoid.liquiddock;

import android.graphics.RectF;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Coordinates Dock icon glass and the native FloatingIcon source/proxy handoff. */
final class DockIconAnimationGlassHook {
    private static final String TAG = "[DC][DockIconAnimationGlass]";
    private static final long FRAME_COMMIT_FALLBACK_MS = 96L;

    private static final Map<View, View> PROXY_TARGETS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Boolean> PROXY_CLOSE_TO_HOME =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, PendingHandoff> PENDING_BY_PROXY =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, PendingHandoff> PENDING_BY_SOURCE =
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
        boolean finish = installFloatingViewFinishHandoffHook(classLoader);
        boolean view2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconView2", false);
        boolean layer2 = installFloatingProxyHook(classLoader,
                "com.miui.home.recents.views.FloatingIconLayer2", true);
        installed = shortcut && backStop && finish && view2;
        if (installed) {
            MainHook.log(TAG + " hooks installed frameCommitHandoff=true"
                    + " fallbackMs=" + FRAME_COMMIT_FALLBACK_MS
                    + " directIconTrace=" + iconTrace
                    + " layer2Observed=" + layer2);
        }
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
                            if (visibility == View.VISIBLE && GlassRuntimeState.isAnyIconEnabled()) {
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
                        Object owner = chain.getThisObject();
                        View host = owner instanceof View ? (View) owner : null;
                        if (host != null && pendingForSource(host) != null) {
                            DockAnimationTrace.sourceEvent(
                                    "onBackAnimStop-suppressed-pending-commit", host, null);
                            return null;
                        }
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " back animation stop hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installFloatingViewFinishHandoffHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.recents.views.FloatingIconView2",
                    "finishImmediately",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object owner = chain.getThisObject();
                        View proxy = owner instanceof View ? (View) owner : null;
                        boolean recycleSynchronously = args.length > 0
                                && args[0] instanceof Boolean && (Boolean) args[0];
                        View target = proxy != null ? proxyTarget(proxy) : null;
                        if (proxy == null || target == null
                                || !LauncherGlassHierarchy.isDock(target)) {
                            return chain.proceed(args);
                        }

                        if (isCloseToHomeProxy(proxy)) {
                            if (beginFrameCommitHandoff(proxy, target, recycleSynchronously)) {
                                return null;
                            }
                        }

                        Object result = chain.proceed(args);
                        DockGlassItemRegistry.endProxyGeometry(target);
                        forgetProxyTarget(proxy);
                        return result;
                    }, boolean.class);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " finish handoff hook unavailable: " + error);
            return false;
        }
    }

    private static boolean installFloatingProxyHook(
            ClassLoader classLoader, String className, boolean useRotationRect) {
        try {
            HookUtil.hookMethod(classLoader, className, "update",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        View proxy = chain.getThisObject() instanceof View
                                ? (View) chain.getThisObject() : null;
                        boolean validFrame = args.length == 10
                                && args[0] instanceof RectF
                                && args[1] instanceof RectF
                                && args[2] instanceof Number
                                && args[3] instanceof Number
                                && args[6] instanceof Boolean;
                        boolean closeToHome = validFrame && !((Boolean) args[6]);
                        float proxyAlpha = validFrame
                                ? ((Number) args[2]).floatValue() : Float.NaN;
                        float progress = validFrame
                                ? ((Number) args[3]).floatValue() : Float.NaN;

                        HookUtil.InvocationResult<Object> targetBeforeResult = validFrame
                                ? HookUtil.tryInvoke(chain.getThisObject(), "getAnimTarget") : null;
                        Object targetBefore = targetBeforeResult != null && targetBeforeResult.succeeded()
                                ? targetBeforeResult.value() : null;
                        if (targetBefore instanceof View
                                && LauncherGlassHierarchy.isDock((View) targetBefore)) {
                            DockAnimationTrace.proxyFrame(
                                    "proxy-pre", proxy, (View) targetBefore, proxyAlpha, progress);
                        }

                        Object result = chain.proceed(args);

                        if (validFrame) {
                            HookUtil.InvocationResult<Object> targetResult =
                                    HookUtil.tryInvoke(chain.getThisObject(), "getAnimTarget");
                            Object target = targetResult.succeeded() ? targetResult.value() : null;
                            if (target instanceof View
                                    && LauncherGlassHierarchy.isDock((View) target)) {
                                View dockTarget = (View) target;
                                DockAnimationTrace.proxyFrame(
                                        "proxy-post", proxy, dockTarget, proxyAlpha, progress);
                                if (proxy != null) {
                                    rememberProxyTarget(proxy, dockTarget, closeToHome);
                                }

                                boolean drawIcon;
                                if (useRotationRect) {
                                    try {
                                        drawIcon = HookUtil.getBooleanField(
                                                chain.getThisObject(), "mIsDrawIcon");
                                    } catch (Throwable ignored) {
                                        drawIcon = false;
                                    }
                                } else {
                                    HookUtil.InvocationResult<Object> drawResult =
                                            HookUtil.tryInvoke(chain.getThisObject(), "isDrawIcon");
                                    Object draw = drawResult.succeeded() ? drawResult.value() : null;
                                    drawIcon = draw instanceof Boolean && ((Boolean) draw);
                                }
                                boolean proxyVisible = useRotationRect
                                        ? LauncherGlassProxyVisibility.isLayer2Visible(
                                                proxyAlpha, drawIcon)
                                        : LauncherGlassProxyVisibility.isView2Visible(
                                                proxyAlpha, drawIcon);

                                if (!proxyVisible) {
                                    DockGlassItemRegistry.holdProxyHidden(dockTarget);
                                } else {
                                    RectF proxyRect = (RectF) args[useRotationRect ? 1 : 0];
                                    DockGlassItemRegistry.updateProxyGeometry(
                                            dockTarget, proxyRect.left, proxyRect.top,
                                            proxyRect.right, proxyRect.bottom);
                                }

                                if (closeToHome && proxy != null
                                        && proxyAlpha <= 0.1f && proxy.getAlpha() < 1.0f) {
                                    proxy.setAlpha(1.0f);
                                    DockAnimationTrace.proxyFrame(
                                            "proxy-tail-held", proxy, dockTarget,
                                            proxyAlpha, progress);
                                }
                                if (closeToHome && GlassRuntimeState.isAnyIconEnabled()) {
                                    DockGlassItemRegistry.observeLaunchAnimationFrame(
                                            dockTarget, progress);
                                }
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

    private static boolean beginFrameCommitHandoff(
            View proxy, View source, boolean recycleSynchronously) {
        PendingHandoff existing = pendingForProxy(proxy);
        if (existing != null) return true;
        if (!proxy.isAttachedToWindow() || !source.isAttachedToWindow()) return false;

        View root = source.getRootView();
        if (root == null) return false;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return false;

        PendingHandoff pending = new PendingHandoff(
                proxy, source, recycleSynchronously, root);
        rememberPending(pending);
        proxy.setVisibility(View.VISIBLE);
        proxy.setAlpha(1.0f);
        DockAnimationTrace.sourceEvent("frame-commit-arm", source, View.VISIBLE);

        try {
            observer.registerFrameCommitCallback(
                    () -> completeFrameCommitHandoff(pending, "frame-commit"));

            Method setAnimTargetVisibility = HookUtil.findMethodExact(
                    source.getClass(), "setAnimTargetVisibility",
                    new Class<?>[]{int.class});
            setAnimTargetVisibility.invoke(source, View.VISIBLE);
            source.postInvalidateOnAnimation();
            root.postInvalidateOnAnimation();
            proxy.postDelayed(
                    () -> completeFrameCommitHandoff(pending, "fallback-timeout"),
                    FRAME_COMMIT_FALLBACK_MS);
            DockAnimationTrace.sourceEvent("source-shown-awaiting-commit", source, View.VISIBLE);
            return true;
        } catch (Throwable error) {
            forgetPending(pending);
            MainHook.log(TAG + " frame-commit handoff unavailable: " + error);
            return false;
        }
    }

    private static void completeFrameCommitHandoff(PendingHandoff pending, String reason) {
        synchronized (pending) {
            if (pending.completed) return;
            pending.completed = true;
        }
        forgetPending(pending);
        DockGlassItemRegistry.endProxyGeometry(pending.source);
        forgetProxyTarget(pending.proxy);
        DockAnimationTrace.sourceEvent("handoff-complete-" + reason, pending.source, View.VISIBLE);

        try {
            Method recycleView = HookUtil.findMethodExact(
                    pending.proxy.getClass(), "recycleView",
                    new Class<?>[]{boolean.class});
            recycleView.invoke(pending.proxy, pending.recycleSynchronously);
            Method removeAnimationEndListener = HookUtil.findMethodExact(
                    pending.proxy.getClass(), "removeAnimationEndListener",
                    new Class<?>[0]);
            removeAnimationEndListener.invoke(pending.proxy);
        } catch (Throwable error) {
            MainHook.log(TAG + " committed proxy retire failed reason=" + reason + ": " + error);
            HookUtil.InvocationResult<Object> recycleResult = HookUtil.tryInvoke(
                    pending.proxy, "recycle", pending.recycleSynchronously, (Object) null);
            if (!recycleResult.succeeded()) {
                MainHook.log(TAG + " fallback proxy recycle failed reason=" + reason
                        + ": " + recycleResult.failure());
            }
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

    private static void rememberPending(PendingHandoff pending) {
        synchronized (PENDING_BY_PROXY) {
            PENDING_BY_PROXY.put(pending.proxy, pending);
        }
        synchronized (PENDING_BY_SOURCE) {
            PENDING_BY_SOURCE.put(pending.source, pending);
        }
    }

    private static PendingHandoff pendingForProxy(View proxy) {
        synchronized (PENDING_BY_PROXY) {
            return PENDING_BY_PROXY.get(proxy);
        }
    }

    private static PendingHandoff pendingForSource(View source) {
        synchronized (PENDING_BY_SOURCE) {
            return PENDING_BY_SOURCE.get(source);
        }
    }

    private static void forgetPending(PendingHandoff pending) {
        synchronized (PENDING_BY_PROXY) {
            if (PENDING_BY_PROXY.get(pending.proxy) == pending) {
                PENDING_BY_PROXY.remove(pending.proxy);
            }
        }
        synchronized (PENDING_BY_SOURCE) {
            if (PENDING_BY_SOURCE.get(pending.source) == pending) {
                PENDING_BY_SOURCE.remove(pending.source);
            }
        }
    }

    private static final class PendingHandoff {
        final View proxy;
        final View source;
        final boolean recycleSynchronously;
        @SuppressWarnings("unused")
        final View root;
        boolean completed;

        PendingHandoff(View proxy, View source, boolean recycleSynchronously, View root) {
            this.proxy = proxy;
            this.source = source;
            this.recycleSynchronously = recycleSynchronously;
            this.root = root;
        }
    }
}
