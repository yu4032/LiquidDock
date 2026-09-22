package com.hellovoid.liquiddock;

import android.app.assist.AssistContent;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces the stock Material backgrounds of the WMShell app-handle popup pills with Prismal glass.
 *
 * <p>HyperOS creates and styles HandleMenuView inside
 * DesktopModeWindowDecoration.onAssistContentReceived(). Hook after that vendor method returns so
 * surfaceBright tinting is already complete. The menu's native layout, click listeners, animation
 * and AdditionalViewContainer remain untouched.</p>
 */
final class SystemUiHandleMenuGlassHook {
    private static final String TAG = "[DC][SystemUiHandleMenuGlass]";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String WINDOW_DECORATION =
            "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration";

    private static final String APP_INFO_PILL = "app_info_pill";
    private static final String WINDOWING_PILL = "windowing_pill";
    private static final String MORE_ACTIONS_PILL = "more_actions_pill";
    private static final String OPEN_IN_APP_PILL = "open_in_app_or_browser_pill";

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
            log("vendor HandleMenu lifecycle hooks installed");
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
        final Object handleMenu;
        final Object handleMenuView;
        final View root;
        try {
            handleMenu = HookUtil.getField(decoration, "mHandleMenu");
            if (handleMenu == null) return;
            handleMenuView = HookUtil.getField(handleMenu, "handleMenuView");
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

            View appInfo = findByResourceName(root, APP_INFO_PILL);
            View windowing = findByResourceName(root, WINDOWING_PILL);
            View moreActions = findByResourceName(root, MORE_ACTIONS_PILL);
            View openInApp = findByResourceName(root, OPEN_IN_APP_PILL);
            if (appInfo == null || windowing == null || moreActions == null
                    || openInApp == null) {
                log("stable HandleMenu pill ids unavailable; stock retained");
                release();
                PENDING.remove(owner);
                return;
            }

            release();
            PENDING.remove(owner);
            try {
                Binding binding = new Binding(
                        owner,
                        root,
                        sourceRoot,
                        appInfo,
                        windowing,
                        moreActions,
                        openInApp,
                        glass);
                ACTIVE.put(owner, binding);
                binding.start();
                log("menu attached root=" + sourceRoot.getWidth() + "x" + sourceRoot.getHeight());
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

        @Override public void onViewDetachedFromWindow(View view) {
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
        final View[] targets = new View[SystemUiHandleMenuGlassSession.Target.values().length];
        final Drawable[] stockBackgrounds = new Drawable[targets.length];
        final SystemUiHandleMenuGlassSinkView[] sinks =
                new SystemUiHandleMenuGlassSinkView[targets.length];
        final boolean[] presented = new boolean[targets.length];
        final SystemUiHandleMenuGlassSession session;

        boolean captureRequested;
        boolean failed;
        boolean released;

        Binding(
                Object owner,
                View root,
                View sourceRoot,
                View appInfo,
                View windowing,
                View moreActions,
                View openInApp,
                LiquidDockConfig.Glass glass) {
            this.owner = owner;
            this.root = root;
            this.sourceRoot = sourceRoot;
            targets[SystemUiHandleMenuGlassSession.Target.APP_INFO.ordinal()] = appInfo;
            targets[SystemUiHandleMenuGlassSession.Target.WINDOWING.ordinal()] = windowing;
            targets[SystemUiHandleMenuGlassSession.Target.MORE_ACTIONS.ordinal()] = moreActions;
            targets[SystemUiHandleMenuGlassSession.Target.OPEN_IN_APP.ordinal()] = openInApp;
            for (int i = 0; i < targets.length; i++) {
                stockBackgrounds[i] = targets[i].getBackground();
            }
            session = new SystemUiHandleMenuGlassSession(sourceRoot, glass, this);
        }

        void start() {
            for (SystemUiHandleMenuGlassSession.Target target
                    : SystemUiHandleMenuGlassSession.Target.values()) {
                View nativePill = targets[target.ordinal()];
                sinks[target.ordinal()] = SystemUiHandleMenuGlassSinkView.attachInsideTarget(
                        nativePill, session, target);
                if (sinks[target.ordinal()] == null) {
                    failed = true;
                    break;
                }
            }
            root.addOnAttachStateChangeListener(this);
            root.getViewTreeObserver().addOnPreDrawListener(this);
            if (failed) {
                onFailure(new IllegalStateException("HandleMenu pill host unavailable"));
                return;
            }
            refreshGeometry();
        }

        @Override
        public boolean onPreDraw() {
            if (released) return true;
            refreshGeometry();
            return true;
        }

        void refreshGeometry() {
            if (released || failed) return;
            SystemUiHandleMenuGlassSession.GeometrySet geometry =
                    new SystemUiHandleMenuGlassSession.GeometrySet();
            for (SystemUiHandleMenuGlassSession.Target target
                    : SystemUiHandleMenuGlassSession.Target.values()) {
                SystemUiHandleMenuGlassSinkView sink = sinks[target.ordinal()];
                if (sink != null) {
                    sink.syncFromTarget();
                    geometry.put(target, sink.captureGeometry(sourceRoot));
                }
            }
            session.updateGeometry(geometry);
            if (!captureRequested && geometry.hasVisibleTarget()) {
                captureRequested = true;
                session.requestInitialCapture();
            }
        }

        @Override
        public void onFirstFramePresented(SystemUiHandleMenuGlassSession.Target target) {
            if (released || failed || target == null) return;
            int index = target.ordinal();
            if (presented[index]) return;
            presented[index] = true;
            targets[index].setBackground(null);
            if (sinks[index] != null) sinks[index].reveal();
            log("Prismal presented target=" + target);
        }

        @Override
        public void onFailure(Throwable error) {
            if (released || failed) return;
            failed = true;
            log("Prismal unavailable; stock retained: " + error);
            restoreStockBackgrounds();
            disposeSinks();
            session.shutdown();
        }

        void restoreStockBackgrounds() {
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] != null && targets[i].getBackground() == null) {
                    targets[i].setBackground(stockBackgrounds[i]);
                }
                presented[i] = false;
            }
        }

        void disposeSinks() {
            for (int i = 0; i < sinks.length; i++) {
                if (sinks[i] != null) sinks[i].dispose();
                sinks[i] = null;
            }
        }

        void release() {
            if (released) return;
            released = true;
            ViewTreeObserver observer = root.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            root.removeOnAttachStateChangeListener(this);
            restoreStockBackgrounds();
            disposeSinks();
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
