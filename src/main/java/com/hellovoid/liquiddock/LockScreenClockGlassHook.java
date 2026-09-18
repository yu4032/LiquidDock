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
                throw new IllegalStateException("native time glyph views unavailable");
            }
            this.session = new MiuiSearchboxGlassSession(
                    clockView, glassConfig, appearance, 0f, this, PassBlurDomain.LOCKSCREEN_CLOCK);
            this.glassView = new MiuiSearchboxGlassView(clockView.getContext(), session);
            glassView.setAlpha(0f);
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
                if (!disposed) session.updateGeometry();
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
            glassView.setAlpha(0f);
            glyphMaskSource.restoreNativeGlyphs();
            session.updateGeometry();
            session.reconcileRoot();
            session.requestFreshCapture();
        }

        @Override
        public void onPresented() {
            if (disposed) return;
            glassView.setAlpha(1f);
            glyphMaskSource.suppressNativeGlyphs();
            Api101Bridge.log(TAG + " presented; native time glyphs suppressed");
        }

        @Override
        public void onFailure(Throwable error) {
            Api101Bridge.log(TAG + " failed; native clock restored", error);
            dispose(true);
        }

        @Override public void onViewAttachedToWindow(View v) {
            if (!disposed) clockView.post(this::refresh);
        }

        @Override public void onViewDetachedFromWindow(View v) {
            dispose(true);
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
            glassView.dispose();
            session.shutdown();
            if (restoreNative) glyphMaskSource.restoreNativeGlyphs();
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
                Object owner = chain.getThisObject();
                if (owner != null) {
                    tryAttach(clockViewField.get(owner));
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

    private static void tryAttach(Object candidate) {
        if (!(candidate instanceof View)) return;
        View clockView = (View) candidate;

        ConfigReader reader = ConfigReader.load();
        LiquidDockConfig config = LiquidDockConfig.from(reader);
        ThirdPartyGlassAppearance appearance =
                LockScreenClockGlassPreferences.resolve(reader, config.glass);
        if (!config.enabled || !config.glass.enabled || !appearance.enabled) return;

        View root = clockView.getRootView();
        if (!(root instanceof ViewGroup) || !root.isAttachedToWindow()) {
            clockView.post(() -> tryAttach(clockView));
            return;
        }
        ViewGroup parent = (ViewGroup) root;
        int index = parent.getChildCount() - 1;

        synchronized (STATES) {
            State old = STATES.get(clockView);
            if (old != null && !old.disposed) {
                old.refresh();
                return;
            }
            State next = new State(clockView, parent, index, config.glass, appearance);
            STATES.put(clockView, next);
            next.attach();
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
