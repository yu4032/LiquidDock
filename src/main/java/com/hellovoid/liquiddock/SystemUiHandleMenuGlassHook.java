package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces the stock Material background of WMShell HandleMenu's windowing pill with Prismal.
 *
 * <p>The stable creation boundary is HandleMenu.HandleMenuView itself. HyperOS constructs this
 * object for every app-handle menu, then applies the vendor theme and later attaches rootView to
 * either an AdditionalViewHostViewContainer or AdditionalSystemViewContainer. We register the
 * freshly constructed root and wait for real attach/layout before binding glass, so AssistContent
 * caching and menu-creation branch selection cannot bypass this integration.</p>
 */
final class SystemUiHandleMenuGlassHook {
    private static final String TAG = "[DC][SystemUiHandleMenuGlass]";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String HANDLE_MENU_VIEW =
            "com.android.wm.shell.windowdecor.HandleMenu$HandleMenuView";
    private static final String WINDOWING_PILL = "windowing_pill";

    private static final Map<View, PendingBinding> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Binding> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static boolean installed;
    private static LiquidDockConfig.Glass glassConfig;

    private SystemUiHandleMenuGlassHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig.Glass glass) {
        if (installed || classLoader == null || glass == null
                || !glass.enabled || !glass.systemUiHandleMenuEnabled) return;
        glassConfig = glass;
        try {
            Class<?> type = Class.forName(HANDLE_MENU_VIEW, false, classLoader);
            int hooked = 0;
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (!isCanonicalHandleMenuViewConstructor(constructor)) continue;
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    observeConstructedMenu(chain.getThisObject());
                    return result;
                });
                hooked++;
            }
            if (hooked == 0) {
                throw new NoSuchMethodException("canonical HandleMenuView constructor");
            }
            installed = true;
            log("HandleMenuView constructor hook installed count=" + hooked);
        } catch (Throwable error) {
            glassConfig = null;
            log("hook unavailable: " + error);
        }
    }

    private static boolean isCanonicalHandleMenuViewConstructor(Constructor<?> constructor) {
        if (constructor == null) return false;
        Class<?>[] types = constructor.getParameterTypes();
        if (types.length != 12
                || types[0] != Context.class
                || types[2] != int.class
                || types[3] != int.class) {
            return false;
        }
        for (int i = 4; i < types.length; i++) {
            if (types[i] != boolean.class) return false;
        }
        return true;
    }

    private static void observeConstructedMenu(Object handleMenuView) {
        LiquidDockConfig.Glass glass = glassConfig;
        if (handleMenuView == null || glass == null || !glass.enabled
                || !glass.systemUiHandleMenuEnabled) return;

        final View root;
        try {
            Object value = HookUtil.getField(handleMenuView, "rootView");
            if (!(value instanceof View)) {
                log("constructed HandleMenuView has no rootView; stock retained");
                return;
            }
            root = (View) value;
        } catch (Throwable error) {
            log("constructed menu discovery failed: " + error);
            return;
        }

        releaseRoot(root, "menu-replaced");
        PendingBinding pending = new PendingBinding(root, glass);
        PENDING.put(root, pending);
        pending.start();
        log("HandleMenuView constructed root=" + root.getClass().getName());
    }

    private static void releaseRoot(View root, String reason) {
        if (root == null) return;
        PendingBinding pending = PENDING.remove(root);
        if (pending != null) pending.release();
        Binding active = ACTIVE.remove(root);
        if (active != null) active.release();
        if (pending != null || active != null) log("released reason=" + reason);
    }

    private static final class PendingBinding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener {
        final View root;
        final LiquidDockConfig.Glass glass;
        boolean released;

        PendingBinding(View root, LiquidDockConfig.Glass glass) {
            this.root = root;
            this.glass = glass;
        }

        void start() {
            root.addOnAttachStateChangeListener(this);
            root.addOnLayoutChangeListener(this);
            tryBind();
        }

        void tryBind() {
            if (released || PENDING.get(root) != this) return;
            View sourceRoot = root.getRootView();
            if (!root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0
                    || sourceRoot == null || !sourceRoot.isAttachedToWindow()
                    || sourceRoot.getWidth() <= 0 || sourceRoot.getHeight() <= 0) {
                return;
            }

            View windowing = findByResourceName(root, WINDOWING_PILL);
            if (!(windowing instanceof ViewGroup)) {
                log("stable windowing_pill unavailable; stock retained");
                PENDING.remove(root);
                release();
                return;
            }

            PENDING.remove(root);
            release();
            try {
                Binding binding = new Binding(root, sourceRoot, windowing, glass);
                ACTIVE.put(root, binding);
                binding.start();
                log("windowing pill bind started root="
                        + sourceRoot.getWidth() + "x" + sourceRoot.getHeight());
            } catch (Throwable error) {
                log("glass bind failed; stock retained: " + error);
                Binding active = ACTIVE.remove(root);
                if (active != null) active.release();
            }
        }

        void release() {
            if (released) return;
            released = true;
            root.removeOnAttachStateChangeListener(this);
            root.removeOnLayoutChangeListener(this);
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            log("HandleMenuView attached");
            tryBind();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (PENDING.get(root) == this) PENDING.remove(root);
            release();
        }

        @Override
        public void onLayoutChange(
                View view,
                int left,
                int top,
                int right,
                int bottom,
                int oldLeft,
                int oldTop,
                int oldRight,
                int oldBottom) {
            tryBind();
        }
    }

    private static final class Binding implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener, SystemUiHandleMenuGlassSession.Listener {
        final View root;
        final View sourceRoot;
        final View windowing;
        final Drawable stockBackground;
        final SystemUiHandleMenuGlassSession session;
        SystemUiHandleMenuGlassSinkView sink;

        boolean captureRequested;
        boolean presented;
        boolean failed;
        boolean released;

        Binding(
                View root,
                View sourceRoot,
                View windowing,
                LiquidDockConfig.Glass glass) {
            this.root = root;
            this.sourceRoot = sourceRoot;
            this.windowing = windowing;
            stockBackground = windowing.getBackground();
            session = new SystemUiHandleMenuGlassSession(sourceRoot, glass, this);
        }

        void start() {
            sink = SystemUiHandleMenuGlassSinkView.attachInsideTarget(windowing, session);
            root.addOnAttachStateChangeListener(this);
            root.getViewTreeObserver().addOnPreDrawListener(this);
            if (sink == null) {
                onFailure(new IllegalStateException("windowing_pill local host unavailable"));
                return;
            }
            refreshGeometry();
        }

        @Override
        public boolean onPreDraw() {
            if (!released) refreshGeometry();
            return true;
        }

        void refreshGeometry() {
            if (released || failed || sink == null) return;
            sink.syncFromTarget();
            LauncherGlassGeometry.Snapshot geometry = sink.captureGeometry(sourceRoot);
            if (geometry == null) return;
            session.updateGeometry(geometry);
            if (!captureRequested) {
                captureRequested = true;
                log("requesting first PassBlur frame");
                session.requestInitialCapture();
            }
        }

        @Override
        public void onFirstFramePresented() {
            if (released || failed || presented) return;
            presented = true;
            windowing.setBackground(null);
            if (sink != null) sink.reveal();
            log("Prismal presented windowing_pill");
        }

        @Override
        public void onFailure(Throwable error) {
            if (released || failed) return;
            failed = true;
            log("Prismal unavailable; stock retained: " + error);
            restoreStockBackground();
            if (sink != null) sink.dispose();
            sink = null;
            session.shutdown();
        }

        void restoreStockBackground() {
            if (windowing.getBackground() == null) windowing.setBackground(stockBackground);
            presented = false;
        }

        void release() {
            if (released) return;
            released = true;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            root.removeOnAttachStateChangeListener(this);
            restoreStockBackground();
            if (sink != null) sink.dispose();
            sink = null;
            session.shutdown();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            refreshGeometry();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (ACTIVE.get(root) == this) ACTIVE.remove(root);
            log("HandleMenuView detached");
            release();
        }
    }

    private static View findByResourceName(View root, String name) {
        if (root == null || name == null) return null;
        Resources resources = root.getResources();
        if (resources == null) return null;
        int id = resources.getIdentifier(name, "id", SYSTEM_UI_PACKAGE);
        return id != 0 ? root.findViewById(id) : null;
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
