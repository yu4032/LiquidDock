package com.hellovoid.liquiddock;

import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/**
 * Strict primary-lockscreen clock replacement.
 *
 * <p>Only displayType == 0 clocks created by MiuiClockController inside
 * NotificationShadeWindowView are eligible, and a renderer may exist only while the SystemUI
 * transition source reports LOCKSCREEN/FINISHED. Preview, AOD, full-AOD, notification,
 * secondary, bouncer, occluded and gone scenes fail closed.</p>
 */
final class LockScreenClockGlassHook {
    private static final String TAG = "[DC][LockScreenClockGlass]";
    private static final String CONTROLLER = "com.miui.clock.MiuiClockController";
    private static final String CLOCK_BEAN = "com.miui.clock.module.ClockBean";
    private static final String LOCKSCREEN_ROOT =
            "com.android.systemui.shade.NotificationShadeWindowView";

    private static final WeakHashMap<View, State> STATES = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> AUTHORIZED_CLOCKS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> PENDING_ATTACH = new WeakHashMap<>();
    private static boolean installed;

    private LockScreenClockGlassHook() {}

    private static final class State implements View.OnAttachStateChangeListener,
            LockScreenClockGlassSession.Listener {
        final ArrayList<View> clockViews;
        final View clockView;
        final ViewGroup outputHost;
        final LockScreenClockGlyphMaskSource glyphSource;
        final LockScreenClockGlassSession session;
        final LockScreenClockGlassView glassView;
        ViewTreeObserver observer;
        ViewTreeObserver.OnPreDrawListener preDraw;
        boolean disposed;

        State(
                List<View> clockViews,
                ViewGroup outputHost,
                LiquidDockConfig.Glass glassConfig,
                ThirdPartyGlassAppearance appearance) {
            if (clockViews == null || clockViews.isEmpty()) {
                throw new UnsupportedClockShapeException("clock group unavailable");
            }
            this.clockViews = new ArrayList<>(clockViews);
            this.clockView = this.clockViews.get(0);
            this.outputHost = outputHost;
            this.glyphSource = LockScreenClockGlyphMaskSource.resolve(this.clockViews);
            if (glyphSource == null || glyphSource.glyphCount() == 0) {
                throw new UnsupportedClockShapeException("native time glyph views unavailable");
            }
            this.session = new LockScreenClockGlassSession(
                    clockView, glyphSource, glassConfig, appearance, this);
            this.glassView = new LockScreenClockGlassView(clockView.getContext(), session);
        }

        void attach() {
            requireScope(clockView);
            for (View candidate : clockViews) {
                candidate.addOnAttachStateChangeListener(this);
            }
            outputHost.addView(
                    glassView,
                    outputHost.getChildCount(),
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));

            ViewTreeObserver next = outputHost.getViewTreeObserver();
            preDraw = () -> {
                if (disposed) return true;
                if (!isScopeAuthorized(clockView)) {
                    disposeNow(true, "scope-lost");
                    return true;
                }
                try {
                    session.updateGeometry();
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " geometry update failed; native clock retained", error);
                    disposeNow(true, "geometry-failure");
                }
                return true;
            };
            if (next.isAlive()) {
                next.addOnPreDrawListener(preDraw);
                observer = next;
            }
            runOnUi(clockView, this::refresh, "initial-refresh");
        }

        void refresh() {
            if (disposed || !clockView.isAttachedToWindow()) return;
            if (!isScopeAuthorized(clockView)) {
                disposeNow(true, "refresh-outside-scope");
                return;
            }
            try {
                session.updateGeometry();
                if (!session.hasRenderableGlyphGeometry()) {
                    glyphSource.restoreNativeGlyphs();
                    clockView.postOnAnimation(this::refresh);
                    return;
                }

                // Native digits must disappear before the fresh PassBlur frame is requested, so
                // the backdrop can never contain the pixels being replaced.
                glyphSource.suppressNativeGlyphs();
                session.reconcileRoot();
                session.requestFreshCapture();
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " refresh failed; native clock retained", error);
                disposeNow(true, "refresh-failure");
            }
        }

        @Override
        public void onPresented() {
            if (disposed) return;
            if (!isScopeAuthorized(clockView)) {
                runOnUi(clockView, () -> disposeNow(true, "present-outside-scope"),
                        "present-scope-dispose");
                return;
            }
            try {
                glyphSource.suppressNativeGlyphs();
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " presentation handoff failed", error);
                runOnUi(clockView, () -> disposeNow(true, "present-failure"),
                        "present-failure-dispose");
            }
        }

        @Override
        public void onFailure(Throwable error) {
            try { Api101Bridge.log(TAG + " session failed; native clock restored", error); }
            catch (Throwable ignored) {}
            runOnUi(clockView, () -> disposeNow(true, "session-failure"),
                    "session-failure-dispose");
        }

        @Override
        public void onViewAttachedToWindow(View v) {
            if (!disposed) runOnUi(clockView, this::refresh, "reattach-refresh");
        }

        @Override
        public void onViewDetachedFromWindow(View v) {
            boolean anyAttached = false;
            for (View candidate : clockViews) {
                if (candidate != null && candidate.isAttachedToWindow()) {
                    anyAttached = true;
                    break;
                }
            }
            if (anyAttached) return;
            synchronized (STATES) {
                for (View candidate : clockViews) {
                    AUTHORIZED_CLOCKS.remove(candidate);
                }
            }
            runOnUi(clockView, () -> disposeNow(true, "clock-detached"), "detach-dispose");
        }

        void disposeNow(boolean restoreNative, String reason) {
            if (disposed) return;
            disposed = true;
            for (View candidate : clockViews) {
                try { candidate.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            }
            if (observer != null && preDraw != null) {
                try {
                    if (observer.isAlive()) observer.removeOnPreDrawListener(preDraw);
                } catch (Throwable ignored) {}
            }
            observer = null;
            preDraw = null;
            synchronized (STATES) {
                for (View candidate : clockViews) {
                    if (STATES.get(candidate) == this) STATES.remove(candidate);
                    PENDING_ATTACH.remove(candidate);
                }
            }
            try { glassView.dispose(); } catch (Throwable ignored) {}
            try { session.shutdown(); } catch (Throwable ignored) {}
            if (restoreNative) {
                try { glyphSource.restoreNativeGlyphs(); } catch (Throwable ignored) {}
            }
            Api101Bridge.log(TAG + " disposed reason=" + reason);
        }
    }

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> controllerClass = Class.forName(CONTROLLER, false, classLoader);
            Class<?> beanClass = Class.forName(CLOCK_BEAN, false, classLoader);
            Field clockViewField = findField(controllerClass, "mClockView");
            Field containerField = findField(controllerClass, "mContainer");
            Field displayTypeField = findField(controllerClass, "mDisplayType");

            Method addClockBean = controllerClass.getDeclaredMethod(
                    "addClockView", beanClass, Boolean.TYPE);
            addClockBean.setAccessible(true);
            HookUtil.hook(addClockBean, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                inspectController(
                        chain.getThisObject(),
                        clockViewField,
                        containerField,
                        displayTypeField,
                        false);
                return result;
            });

            // Async construction posts the real View assignment from addClockView(View).
            Method addClockView = controllerClass.getDeclaredMethod("addClockView", View.class);
            addClockView.setAccessible(true);
            HookUtil.hook(addClockView, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object owner = chain.getThisObject();
                Object result = chain.proceed(args);
                inspectController(owner, clockViewField, containerField, displayTypeField, true);
                return result;
            });

            installed = true;
            Api101Bridge.log(TAG + " installed on primary MiuiClockController clock creation");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " unavailable; native clock retained", error);
            return false;
        }
    }

    static void onLockscreenSceneChanged(boolean lockscreen) {
        ArrayList<State> states = new ArrayList<>();
        ArrayList<View> authorized = new ArrayList<>();
        synchronized (STATES) {
            for (State state : STATES.values()) {
                if (state != null && !states.contains(state)) states.add(state);
            }
            authorized.addAll(AUTHORIZED_CLOCKS.keySet());
        }

        if (!lockscreen) {
            // Hard boundary: there is no lockscreen glass object outside LOCKSCREEN/FINISHED.
            for (State state : states) {
                if (state != null && !state.disposed) {
                    runOnUi(state.clockView,
                            () -> state.disposeNow(true, "scene-left-lockscreen"),
                            "scene-hard-dispose");
                }
            }
            synchronized (STATES) {
                PENDING_ATTACH.clear();
            }
            return;
        }

        for (View clock : authorized) {
            if (clock != null && clock.isAttachedToWindow() && isAuthorizedRoot(clock)) {
                scheduleAttach(clock);
            }
        }
    }

    private static void inspectController(
            Object owner,
            Field clockViewField,
            Field containerField,
            Field displayTypeField,
            boolean deferOneFrame) {
        if (owner == null) return;
        try {
            int displayType = displayTypeField.getInt(owner);
            // Decompiled ClockStyleInfo flags:
            // AOD=1, notification=2, preview=8, full-AOD=32, secondary=64.
            // Primary keyguard clock is exactly displayType 0.
            if (displayType != 0) return;

            Object containerValue = containerField.get(owner);
            if (!(containerValue instanceof ViewGroup)) return;
            ViewGroup container = (ViewGroup) containerValue;

            Runnable inspect = () -> {
                try {
                    Object candidate = clockViewField.get(owner);
                    if (!(candidate instanceof View)) return;
                    View clockView = (View) candidate;
                    if (!isAuthorizedRoot(clockView)) return;
                    synchronized (STATES) {
                        AUTHORIZED_CLOCKS.put(clockView, Boolean.TRUE);
                    }
                    if (SystemUiKeyguardGoneSource.isLockscreenScene()) {
                        scheduleAttach(clockView);
                    }
                } catch (Throwable error) {
                    try { Api101Bridge.log(TAG + " controller inspection failed", error); }
                    catch (Throwable ignored) {}
                }
            };

            if (deferOneFrame) {
                try { container.postOnAnimation(inspect); }
                catch (Throwable ignored) {}
            } else {
                inspect.run();
            }
        } catch (Throwable error) {
            try { Api101Bridge.log(TAG + " controller gate failed closed", error); }
            catch (Throwable ignored) {}
        }
    }

    private static void scheduleAttach(View clockView) {
        if (clockView == null || !clockView.isAttachedToWindow()) return;
        synchronized (STATES) {
            if (!Boolean.TRUE.equals(AUTHORIZED_CLOCKS.get(clockView))) return;
            State existing = STATES.get(clockView);
            if (existing != null && !existing.disposed) return;
            if (Boolean.TRUE.equals(PENDING_ATTACH.get(clockView))) return;
            PENDING_ATTACH.put(clockView, Boolean.TRUE);
        }
        try {
            clockView.postOnAnimation(() -> tryAttachFailClosed(clockView));
        } catch (Throwable error) {
            clearPending(clockView);
        }
    }

    private static void tryAttachFailClosed(View clockView) {
        try {
            if (clockView == null || !clockView.isAttachedToWindow()
                    || !Boolean.TRUE.equals(AUTHORIZED_CLOCKS.get(clockView))
                    || !isScopeAuthorized(clockView)) {
                clearPending(clockView);
                return;
            }

            ConfigReader reader = ConfigReader.load();
            LiquidDockConfig config = LiquidDockConfig.from(reader);
            ThirdPartyGlassAppearance appearance =
                    LockScreenClockGlassPreferences.resolve(reader, config.glass);
            if (!config.enabled || !config.glass.enabled || !appearance.enabled) {
                clearPending(clockView);
                return;
            }

            View root = clockView.getRootView();
            if (!(root instanceof ViewGroup) || !isAuthorizedRoot(clockView)) {
                clearPending(clockView);
                return;
            }
            RootPassBlurEndpointBridge.Endpoint endpoint =
                    RootPassBlurEndpointBridge.inspect(root);
            if (endpoint == null || !endpoint.isValid()) {
                postEndpointRetry(clockView);
                return;
            }

            ArrayList<View> group = collectAuthorizedClockGroup(root);
            if (group.isEmpty()) group.add(clockView);

            State next;
            synchronized (STATES) {
                State existing = null;
                for (View candidate : group) {
                    State mapped = STATES.get(candidate);
                    if (mapped != null && !mapped.disposed) {
                        existing = mapped;
                        break;
                    }
                }
                if (existing != null) {
                    for (View candidate : group) PENDING_ATTACH.remove(candidate);
                    return;
                }

                next = new State(group, (ViewGroup) root, config.glass, appearance);
                for (View candidate : group) {
                    STATES.put(candidate, next);
                    PENDING_ATTACH.remove(candidate);
                }
            }

            try {
                next.attach();
                Api101Bridge.log(TAG + " attached primary lockscreen glyphs="
                        + next.glyphSource.glyphCount());
            } catch (Throwable error) {
                next.disposeNow(true, "attach-failure");
                Api101Bridge.log(TAG + " attach failed; native clock retained", error);
            }
        } catch (UnsupportedClockShapeException unsupported) {
            clearPending(clockView);
            Api101Bridge.log(TAG + " unsupported clock shape; native clock retained");
        } catch (Throwable error) {
            clearPending(clockView);
            Api101Bridge.log(TAG + " attach path failed closed; native clock retained", error);
        }
    }

    private static ArrayList<View> collectAuthorizedClockGroup(View root) {
        ArrayList<View> result = new ArrayList<>();
        synchronized (STATES) {
            for (View candidate : AUTHORIZED_CLOCKS.keySet()) {
                if (candidate == null || !candidate.isAttachedToWindow()) continue;
                if (candidate.getRootView() != root) continue;
                if (!isAuthorizedRoot(candidate)) continue;
                result.add(candidate);
            }
        }
        return result;
    }

    private static void postEndpointRetry(View clockView) {
        if (clockView == null) return;
        try {
            clockView.postOnAnimation(() -> {
                if (!isScopeAuthorized(clockView)) {
                    clearPending(clockView);
                    return;
                }
                tryAttachFailClosed(clockView);
            });
        } catch (Throwable ignored) {
            clearPending(clockView);
        }
    }

    private static boolean isScopeAuthorized(View clockView) {
        return SystemUiKeyguardGoneSource.isLockscreenScene()
                && Boolean.TRUE.equals(AUTHORIZED_CLOCKS.get(clockView))
                && isAuthorizedRoot(clockView);
    }

    private static void requireScope(View clockView) {
        if (!isScopeAuthorized(clockView)) {
            throw new IllegalStateException("lockscreen scope not authorized");
        }
    }

    private static boolean isAuthorizedRoot(View clockView) {
        if (clockView == null || !clockView.isAttachedToWindow()) return false;
        View root = clockView.getRootView();
        if (root == null) return false;
        Class<?> type = root.getClass();
        while (type != null) {
            if (LOCKSCREEN_ROOT.equals(type.getName())) return true;
            type = type.getSuperclass();
        }
        return false;
    }

    private static void clearPending(View view) {
        if (view == null) return;
        synchronized (STATES) {
            PENDING_ATTACH.remove(view);
        }
    }

    private static void runOnUi(View view, Runnable action, String stage) {
        if (view == null || action == null) return;
        try {
            if (Looper.myLooper() == view.getHandler().getLooper()) {
                action.run();
                return;
            }
            if (!view.post(() -> {
                try { action.run(); }
                catch (Throwable error) {
                    Api101Bridge.log(TAG + " posted " + stage + " failed", error);
                }
            })) {
                Api101Bridge.log(TAG + " posted " + stage + " rejected");
            }
        } catch (Throwable error) {
            try { Api101Bridge.log(TAG + " scheduling " + stage + " failed", error); }
            catch (Throwable ignored) {}
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
