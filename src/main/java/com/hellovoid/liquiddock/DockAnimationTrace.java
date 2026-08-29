package com.hellovoid.liquiddock;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Short-lived frame trace for diagnosing the Dock icon handoff without changing animation behavior. */
final class DockAnimationTrace {
    private static final String TAG = "[DC][FlickerTrace]";
    private static final long TRACE_TAIL_MS = 1200L;
    private static final AtomicInteger NEXT_SESSION = new AtomicInteger();
    private static final AtomicLong NEXT_EVENT = new AtomicLong();

    private static volatile int activeSession;
    private static volatile long activeUntilUptimeMs;
    private static volatile WeakReference<View> activeTarget = new WeakReference<>(null);

    private DockAnimationTrace() {}

    static void proxyFrame(String phase, View proxy, View target, float proxyAlpha, float progress) {
        if (target == null) return;
        ensureSession(target);
        activeUntilUptimeMs = SystemClock.uptimeMillis() + TRACE_TAIL_MS;
        log(phase, target,
                "p=" + fmt(progress)
                        + " pa=" + fmt(proxyAlpha)
                        + " proxy=" + viewState(proxy));
    }

    static void sourceEvent(String event, View target, Integer requestedVisibility) {
        if (target == null || !isActiveFor(target)) return;
        activeUntilUptimeMs = SystemClock.uptimeMillis() + TRACE_TAIL_MS;
        log(event, target,
                requestedVisibility != null ? " req=" + requestedVisibility : "");
    }

    static void animationRegistry(String event, View target, float progress) {
        if (target == null || !isActiveFor(target)) return;
        log(event, target, " p=" + fmt(progress));
    }

    static void eglSwap(long renderedFrame, boolean fromProducerFrame, int sceneSize) {
        if (!isActive()) return;
        MainHook.log(prefix("egl-swap")
                + " frame=" + renderedFrame
                + " producerCb=" + fromProducerFrame
                + " scene=" + sceneSize);
    }

    static void rendererEvent(String event) {
        if (!isActive()) return;
        MainHook.log(prefix(event));
    }

    static boolean isActive() {
        return activeSession != 0 && SystemClock.uptimeMillis() <= activeUntilUptimeMs;
    }

    private static boolean isActiveFor(View target) {
        return isActive() && activeTarget.get() == target;
    }

    private static synchronized void ensureSession(View target) {
        if (isActiveFor(target)) return;
        activeSession = NEXT_SESSION.incrementAndGet();
        activeTarget = new WeakReference<>(target);
        activeUntilUptimeMs = SystemClock.uptimeMillis() + TRACE_TAIL_MS;
        installPreDrawTrace(target, activeSession);
        log("session-begin", target, "");
    }

    private static void installPreDrawTrace(View target, int session) {
        View root = target.getRootView();
        if (root == null) return;
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (activeSession == session && isActiveFor(target)) {
                    log("dock-preDraw", target,
                            " rootDraw=" + root.getDrawingTime()
                                    + " root=" + viewState(root));
                }
                return true;
            }
        };
        observer.addOnPreDrawListener(listener);
        target.postDelayed(() -> {
            try {
                ViewTreeObserver current = root.getViewTreeObserver();
                if (current.isAlive()) current.removeOnPreDrawListener(listener);
            } catch (Throwable ignored) {}
        }, TRACE_TAIL_MS + 400L);
    }

    private static void log(String event, View target, String extra) {
        long now = SystemClock.uptimeMillis();
        Object iconVisibility = HookUtil.invoke(target, "getIconVisibility");
        float glassOpacity;
        try {
            glassOpacity = DockGlassItemRegistry.animationOpacity(target, now);
        } catch (Throwable ignored) {
            glassOpacity = Float.NaN;
        }
        MainHook.log(prefix(event)
                + extra
                + " iconVis=" + String.valueOf(iconVisibility)
                + " glass=" + fmt(glassOpacity)
                + " target=" + viewState(target)
                + " draw=" + target.getDrawingTime());
    }

    private static String prefix(String event) {
        return TAG
                + " S=" + activeSession
                + " E=" + NEXT_EVENT.incrementAndGet()
                + " up=" + SystemClock.uptimeMillis()
                + " ns=" + System.nanoTime()
                + " th=" + Thread.currentThread().getName()
                + " " + event;
    }

    private static String viewState(View view) {
        if (view == null) return "null";
        return view.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(view))
                + "{vis=" + view.getVisibility()
                + ",win=" + view.getWindowVisibility()
                + ",a=" + fmt(view.getAlpha())
                + ",shown=" + view.isShown()
                + ",att=" + view.isAttachedToWindow()
                + ",xywh=" + view.getLeft() + "," + view.getTop()
                + "," + view.getWidth() + "x" + view.getHeight()
                + "}";
    }

    private static String fmt(float value) {
        if (!Float.isFinite(value)) return String.valueOf(value);
        return String.format(Locale.US, "%.4f", value);
    }
}
