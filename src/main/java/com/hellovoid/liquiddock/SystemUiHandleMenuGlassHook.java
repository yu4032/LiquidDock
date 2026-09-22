package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces the stock Material background of WMShell HandleMenu's windowing pill with Prismal.
 *
 * <p>The decompiled creation path inflates {@code desktop_mode_window_decor_handle_menu} before
 * vendor tinting and before the menu-local SurfaceControlViewHost is attached. Constructor and
 * AssistContent hooks proved unreliable on-device, so the stable observation boundary is the
 * framework LayoutInflater call filtered by that exact SystemUI layout resource. The inflated root
 * is then bound only at the real attach/layout boundary.</p>
 */
final class SystemUiHandleMenuGlassHook {
    private static final String TAG = "[DC][SystemUiHandleMenuGlass]";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String HANDLE_MENU_LAYOUT = "desktop_mode_window_decor_handle_menu";
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
            HookUtil.hookMethod(
                    LayoutInflater.class,
                    "inflate",
                    new Class<?>[]{int.class, ViewGroup.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        LayoutInflater inflater = chain.getThisObject() instanceof LayoutInflater
                                ? (LayoutInflater) chain.getThisObject()
                                : null;
                        int resourceId = args.length > 0 && args[0] instanceof Integer
                                ? (Integer) args[0]
                                : 0;
                        boolean target = isTargetHandleMenuLayout(inflater, resourceId);
                        Object result = chain.proceed(args);
                        if (target) {
                            if (result instanceof View) {
                                View root = (View) result;
                                log("target layout inflated root=" + root.getClass().getName());
                                observeInflatedMenu(root);
                            } else {
                                log("target layout inflation returned no View; stock retained");
                            }
                        }
                        return result;
                    });
            installed = true;
            log("HandleMenu layout inflation hook installed");
        } catch (Throwable error) {
            glassConfig = null;
            log("hook unavailable: " + error);
        }
    }

    private static boolean isTargetHandleMenuLayout(LayoutInflater inflater, int resourceId) {
        if (inflater == null || resourceId == 0) return false;
        try {
            Resources resources = inflater.getContext().getResources();
            return SYSTEM_UI_PACKAGE.equals(resources.getResourcePackageName(resourceId))
                    && "layout".equals(resources.getResourceTypeName(resourceId))
                    && HANDLE_MENU_LAYOUT.equals(resources.getResourceEntryName(resourceId));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void observeInflatedMenu(View root) {
        LiquidDockConfig.Glass glass = glassConfig;
        if (root == null || glass == null || !glass.enabled
                || !glass.systemUiHandleMenuEnabled) return;

        releaseRoot(root, "menu-replaced");
        PendingBinding pending = new PendingBinding(root, glass);
        PENDING.put(root, pending);
        pending.start();
        log("HandleMenu root observed attached=" + root.isAttachedToWindow()
                + " size=" + root.getWidth() + "x" + root.getHeight());
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
