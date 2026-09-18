package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Unconditionally replaces the OS3 SystemUI lockscreen clock host with LiquidDock Prismal glass.
 *
 * <p>No vendor glass capability or effect flag is consulted. Native clock content is hidden only
 * after the LiquidDock output has presented successfully and is restored on any failure/teardown.</p>
 */
final class LockScreenClockGlassHook {
    private static final String TAG = "[DC][LockScreenClockGlass]";
    private static final String CONTROLLER = "com.miui.clock.MiuiClockController";
    private static final String CLOCK_BEAN = "com.miui.clock.module.ClockBean";

    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PENDING_ATTACH = new WeakHashMap<>();
    private static boolean installed;

    private LockScreenClockGlassHook() {}

    private static final class State implements View.OnAttachStateChangeListener,
            MiuiSearchboxGlassSession.Listener {
        final View clockView;
        final ViewGroup outputHost;
        final int outputIndex;
        final LockScreenClockGlyphMaskSource glyphMaskSource;
        final MiuiSearchboxGlassSession session;
        final MiuiSearchboxGlassView glassView;
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener preDraw;
        boolean disposed;

        State(
                View clockView,
                ViewGroup outputHost,
                int outputIndex,
                LiquidDockConfig.Glass glassConfig,
                ThirdPartyGlassAppearance appearance) {
            this.clockView = clockView;
            this.outputHost = outputHost;
            this.outputIndex = outputIndex;
            this.glyphMaskSource = LockScreenClockGlyphMaskSource.resolve(clockView);
            if (glyphMaskSource == null || glyphMaskSource.glyphCount() == 0) {
                throw new UnsupportedClockShapeException("native time glyph views unavailable");
            }
            this.session = new MiuiSearchboxGlassSession(
                    clockView, glassConfig, appearance, 0f, this, PassBlurDomain.LOCKSCREEN_CLOCK);
            this.glassView = new MiuiSearchboxGlassView(clockView.getContext(), session);
        }

        void attach() {
            clockView.addOnAttachStateChangeListener(this);
            outputHost.addView(
                    glassView,
                    Math.max(0, Math.min(outputIndex + 1, outputHost.getChildCount())),
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            ViewTreeObserver next = outputHost.getViewTreeObserver();
            preDraw = () -> {
                if (!disposed) {
                    try {
                        session.updateGeometry();
                    } catch (Throwable error) {
                        Api101Bridge.log(TAG + " pre-draw failed; native clock retained", error);
                        try { glyphMaskSource.restoreNativeGlyphs(); } catch (Throwable ignored) {}
                        safePost(clockView, () -> dispose(true), "pre-draw-dispose");
                    }
                }
                return true;
            };
            if (next.isAlive()) {
                next.addOnPreDrawListener(preDraw);
                observer = next;
            }
            clockView.post(this::refresh);
        }

        void refresh() {
            if (disposed || !clockView.isAttachedToWindow()) return;
            try {
                glyphMaskSource.restoreNativeGlyphs();
                session.updateGeometry();
                session.reconcileRoot();
                session.requestFreshCapture();
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " refresh failed; native clock retained", error);
                try { glyphMaskSource.restoreNativeGlyphs(); } catch (Throwable ignored) {}
                safePost(clockView, () -> dispose(true), "refresh-dispose");
            }
        }

        @Override
        public void onPresented() {
            if (disposed) return;
            try {
                glyphMaskSource.suppressNativeGlyphs();
                Api101Bridge.log(TAG + " presented; native time glyphs suppressed");
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " presentation handoff failed; native clock retained", error);
                try { glyphMaskSource.restoreNativeGlyphs(); } catch (Throwable ignored) {}
                safePost(clockView, () -> dispose(true), "present-dispose");
            }
        }

        @Override
        public void onFailure(Throwable error) {
            try {
                Api101Bridge.log(TAG + " failed; native clock restored", error);
            } catch (Throwable ignored) {}
            try { glyphMaskSource.restoreNativeGlyphs(); } catch (Throwable ignored) {}
            safePost(clockView, () -> dispose(true), "failure-dispose");
        }

        @Override public void onViewAttachedToWindow(View v) {
            if (!disposed) safePost(clockView, this::refresh, "reattach-refresh");
        }

        @Override public void onViewDetachedFromWindow(View v) {
            try { glyphMaskSource.restoreNativeGlyphs(); } catch (Throwable ignored) {}
            safePost(clockView, () -> dispose(true), "detach-dispose");
        }

        void dispose(boolean restoreNative) {
            if (disposed) return;
            disposed = true;
            try { clockView.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            if (observer != null && preDraw != null) {
                try { if (observer.isAlive()) observer.removeOnPreDrawListener(preDraw); }
                catch (Throwable ignored) {}
            }
            observer = null;
            preDraw = null;
            synchronized (STATES) {
                if (STATES.get(clockView) == this) STATES.remove(clockView);
            }
            try { glassView.dispose(); } catch (Throwable error) {
                Api101Bridge.log(TAG + " glass view dispose failed", error);
            }
            try { session.shutdown(); } catch (Throwable error) {
                Api101Bridge.log(TAG + " session shutdown failed", error);
            }
            if (restoreNative) {
                try { glyphMaskSource.restoreNativeGlyphs(); } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native glyph restore failed", error);
                }
            }
        }
    }

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> controllerClass = Class.forName(CONTROLLER, false, classLoader);
            Class<?> beanClass = Class.forName(CLOCK_BEAN, false, classLoader);
            Method addClockView = controllerClass.getDeclaredMethod(
                    "addClockView", beanClass, Boolean.TYPE);
            addClockView.setAccessible(true);
            Field clockViewField = findField(controllerClass, "mClockView");

            HookUtil.hook(addClockView, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                try {
                    Object owner = chain.getThisObject();
                    Object candidate = owner != null ? clockViewField.get(owner) : null;
                    scheduleAttach(candidate);
                } catch (Throwable error) {
                    // Never allow optional clock glass to escape into SystemUI's clock creation.
                    Api101Bridge.log(TAG + " post-create inspection failed; native clock retained", error);
                }
                return result;
            });
            installed = true;
            Api101Bridge.log(TAG + " installed on MiuiClockController#addClockView");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " unavailable; native OS3 clock retained", error);
            return false;
        }
    }

    private static void scheduleAttach(Object candidate) {
        if (!(candidate instanceof View)) return;
        View clockView = (View) candidate;
        synchronized (STATES) {
            State existing = STATES.get(clockView);
            if (existing != null && !existing.disposed) {
                safePost(clockView, existing::refresh, "refresh");
                return;
            }
            if (Boolean.TRUE.equals(PENDING_ATTACH.get(clockView))) return;
            PENDING_ATTACH.put(clockView, Boolean.TRUE);
        }
        safePost(clockView, () -> tryAttachFailClosed(clockView), "attach");
    }

    private static void tryAttachFailClosed(View clockView) {
        try {
            if (clockView == null || !clockView.isAttachedToWindow()) {
                clearPending(clockView);
                return;
            }

            ConfigReader reader = ConfigReader.load();
            LiquidDockConfig config = LiquidDockConfig.from(reader);
            ThirdPartyGlassAppearance appearance =
                    LockScreenClockGlassPreferences.resolve(reader, config.glass);
            if (!config.enabled || !config.glass.enabled || !appearance.enabled) {
                Api101Bridge.log(TAG + " skipped by config module=" + config.enabled
                        + " glass=" + config.glass.enabled
                        + " clock=" + appearance.enabled);
                clearPending(clockView);
                return;
            }

            View root = clockView.getRootView();
            if (!(root instanceof ViewGroup) || !root.isAttachedToWindow()) {
                Api101Bridge.log(TAG + " root unavailable; waiting for next frame");
                postEndpointRetry(clockView);
                return;
            }
            RootPassBlurEndpointBridge.Endpoint endpoint =
                    RootPassBlurEndpointBridge.inspect(root);
            if (endpoint == null || !endpoint.isValid()) {
                Api101Bridge.log(TAG + " root endpoint not ready; waiting for next frame");
                postEndpointRetry(clockView);
                return;
            }
            ViewGroup parent = (ViewGroup) root;
            int index = parent.getChildCount() - 1;
            Api101Bridge.log(TAG + " root endpoint ready surface="
                    + endpoint.surfaceWidth + "x" + endpoint.surfaceHeight
                    + " buffer=" + endpoint.bufferWidth + "x" + endpoint.bufferHeight
                    + " layerId=" + endpoint.rootLayerId
                    + " surfaceSeq=" + endpoint.surfaceSequenceId);

            State next;
            synchronized (STATES) {
                State old = STATES.get(clockView);
                if (old != null && !old.disposed) {
                    old.refresh();
                    return;
                }
                next = new State(clockView, parent, index, config.glass, appearance);
                STATES.put(clockView, next);
                PENDING_ATTACH.remove(clockView);
                Api101Bridge.log(TAG + " attach candidate class="
                        + clockView.getClass().getName()
                        + " glyphs=" + next.glyphMaskSource.glyphCount()
                        + " root=" + parent.getClass().getName());
            }

            try {
                next.attach();
            } catch (Throwable error) {
                synchronized (STATES) {
                    if (STATES.get(clockView) == next) STATES.remove(clockView);
                }
                try { next.dispose(true); } catch (Throwable ignored) {}
                Api101Bridge.log(TAG + " attach failed; native clock retained", error);
            }
        } catch (UnsupportedClockShapeException unsupported) {
            String className = clockView != null ? clockView.getClass().getName() : "";
            if (className.equals("com.miui.clock.classic.ClassicClockView")
                    || className.equals("com.miui.clock.classic.ClassicPlusClockView")) {
                Api101Bridge.log(TAG + " classic time glyph source not ready; waiting for next frame");
                postEndpointRetry(clockView);
                return;
            }
            clearPending(clockView);
            Api101Bridge.log(TAG + " unsupported clock shape class=" + className
                    + "; native clock retained");
        } catch (Throwable error) {
            clearPending(clockView);
            // Absolute process boundary: this feature must never kill SystemUI.
            Api101Bridge.log(TAG + " attach path failed closed; native clock retained", error);
        }
    }

    private static void postEndpointRetry(View clockView) {
        if (clockView == null) return;
        try {
            clockView.postOnAnimation(() -> {
                try {
                    if (!clockView.isAttachedToWindow()) {
                        clearPending(clockView);
                        return;
                    }
                    tryAttachFailClosed(clockView);
                } catch (Throwable error) {
                    clearPending(clockView);
                    Api101Bridge.log(TAG + " endpoint wait failed closed; native clock retained", error);
                }
            });
        } catch (Throwable error) {
            clearPending(clockView);
            Api101Bridge.log(TAG + " endpoint wait scheduling failed; native clock retained", error);
        }
    }

    private static void clearPending(View view) {
        if (view == null) return;
        synchronized (STATES) {
            PENDING_ATTACH.remove(view);
        }
    }

    private static void safePost(View view, Runnable action, String stage) {
        try {
            if (!view.post(() -> {
                try {
                    action.run();
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " posted " + stage
                            + " failed; native clock retained", error);
                }
            })) {
                synchronized (STATES) {
                    PENDING_ATTACH.remove(view);
                }
                Api101Bridge.log(TAG + " posted " + stage + " rejected; native clock retained");
            }
        } catch (Throwable error) {
            synchronized (STATES) {
                PENDING_ATTACH.remove(view);
            }
            Api101Bridge.log(TAG + " scheduling " + stage
                    + " failed; native clock retained", error);
        }
    }

    private static final class UnsupportedClockShapeException extends RuntimeException {
        UnsupportedClockShapeException(String message) {
            super(message);
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }
}
