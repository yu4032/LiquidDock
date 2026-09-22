package com.hellovoid.liquiddock;

import android.app.assist.AssistContent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces the stock Material background of WMShell HandleMenu's windowing pill with Prismal.
 *
 * <p>HyperOS creates and theme-tints HandleMenuView inside
 * DesktopModeWindowDecoration.onAssistContentReceived(). We bind after that method returns, so the
 * vendor remains authoritative for layout, button actions, animation and theme selection. Only the
 * background of {@code windowing_pill} changes after a real Prismal frame is presented.</p>
 */
final class SystemUiHandleMenuGlassHook {
    private static final String TAG = "[DC][SystemUiHandleMenuGlass]";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String WINDOW_DECORATION =
            "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration";
    private static final String WINDOWING_PILL = "windowing_pill";

    private static final Map<Object, PendingBinding> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Binding> ACTIVE =
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
                    classLoader,
                    WINDOW_DECORATION,
                    "onAssistContentReceived",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        schedule(chain.getThisObject());
                        return result;
                    },
                    AssistContent.class);
            HookUtil.hookMethod(
                    classLoader,
                    WINDOW_DECORATION,
                    "closeHandleMenu",
                    chain -> {
                        releaseOwner(chain.getThisObject(), "vendor-close");
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            installed = true;
            log("windowing-pill lifecycle hooks installed");
        } catch (Throwable error) {
            glassConfig = null;
            log("hook unavailable: " + error);
        }
    }

    private static void schedule(Object decoration) {
        LiquidDockConfig.Glass glass = glassConfig;
        if (decoration == null || glass == null || !glass.enabled
                || !glass.systemUiHandleMenuEnabled) return;

        releaseOwner(decoration, "menu-replaced");
        final View root;
        try {
            Object handleMenu = HookUtil.getField(decoration, "mHandleMenu");
            if (handleMenu == null) return;
            Object handleMenuView = HookUtil.getField(handleMenu, "handleMenuView");
            if (handleMenuView == null) return;
            Object rootValue = HookUtil.getField(handleMenuView, "rootView");
            if (!(rootValue instanceof View)) return;
            root = (View) rootValue;
        } catch (Throwable error) {
            log("menu discovery failed: " + error);
            return;
        }

        PendingBinding pending = new PendingBinding(decoration, root, glass);
        PENDING.put(decoration, pending);
        pending.start();
    }

    private static void releaseOwner(Object decoration, String reason) {
        if (decoration == null) return;
        PendingBinding pending = PENDING.remove(decoration);
        if (pending != null) pending.release();
        Binding active = ACTIVE.remove(decoration);
        if (active != null) active.release();
        if (pending != null || active != null) log("released reason=" + reason);
    }

    private static final class PendingBinding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener {
        final Object owner;
        final View root;
        final LiquidDockConfig.Glass glass;
        boolean released;

        PendingBinding(Object owner, View root, LiquidDockConfig.Glass glass) {
            this.owner = owner;
            this.root = root;
            this.glass = glass;
        }

        void start() {
            root.addOnAttachStateChangeListener(this);
            root.addOnLayoutChangeListener(this);
            tryBind();
        }

        void tryBind() {
            if (released || PENDING.get(owner) != this) return;
            View sourceRoot = root.getRootView();
            if (!root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0
                    || sourceRoot == null || !sourceRoot.isAttachedToWindow()
                    || sourceRoot.getWidth() <= 0 || sourceRoot.getHeight() <= 0) {
                return;
            }

            View windowing = findByResourceName(root, WINDOWING_PILL);
            if (!(windowing instanceof ViewGroup)) {
                log("stable windowing_pill unavailable; stock retained");
                PENDING.remove(owner);
                release();
                return;
            }

            PENDING.remove(owner);
            release();
            try {
                Binding binding = new Binding(owner, root, sourceRoot, windowing, glass);
                ACTIVE.put(owner, binding);
                binding.start();
                log("windowing pill bind started root="
                        + sourceRoot.getWidth() + "x" + sourceRoot.getHeight());
            } catch (Throwable error) {
                log("glass bind failed; stock retained: " + error);
                Binding active = ACTIVE.remove(owner);
                if (active != null) active.release();
            }
        }

        void release() {
            if (released) return;
            released = true;
            root.removeOnAttachStateChangeListener(this);
            root.removeOnLayoutChangeListener(this);
        }

        @Override public void onViewAttachedToWindow(View view) { tryBind(); }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (PENDING.get(owner) == this) PENDING.remove(owner);
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
        final Object owner;
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
                Object owner,
                View root,
                View sourceRoot,
                View windowing,
                LiquidDockConfig.Glass glass) {
            this.owner = owner;
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

        @Override public void onViewAttachedToWindow(View view) { refreshGeometry(); }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (ACTIVE.get(owner) == this) ACTIVE.remove(owner);
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
