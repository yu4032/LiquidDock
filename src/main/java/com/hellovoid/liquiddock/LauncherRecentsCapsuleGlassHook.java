package com.hellovoid.liquiddock;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

/** Full Prismal replacement for the two stable HyperOS Pad Recents action capsules. */
final class LauncherRecentsCapsuleGlassHook {
    private static final String TAG = "[DC][RecentsCapsule]";
    private static final String LAUNCHER_PACKAGE = "com.miui.home";
    private static final String RECENTS_DECORATIONS =
            "com.miui.home.recents.views.RecentsDecorations";
    private static final String CLEAR_ALL_CAPSULE = "recent_clear_all_task_container_for_pad";
    private static final String WORLD_CAPSULE = "world_container";

    private static boolean installed;
    private static WeakReference<ViewGroup> lastDecorationsRef = new WeakReference<>(null);
    private static PendingBinding pendingBinding;
    private static Binding activeBinding;

    private LauncherRecentsCapsuleGlassHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DECORATIONS, "findAndSetupViews", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup) scheduleBinding((ViewGroup) owner);
                return result;
            });
            installed = true;
            MainHook.log(TAG + " stable RecentsDecorations Prismal hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " RecentsDecorations hook unavailable: " + error);
        }
    }

    static void onRuntimeStateChanged() {
        ViewGroup decorations = lastDecorationsRef.get();
        if (!GlassRuntimeState.isRecentsCapsuleEnabled()) {
            releasePending("runtime-disabled");
            releaseActive("runtime-disabled");
            return;
        }
        if (decorations != null) scheduleBinding(decorations);
    }

    static void onRecentsShown() {
        if (!GlassRuntimeState.isRecentsCapsuleEnabled()) return;
        Binding binding = activeBinding;
        if (binding != null) {
            binding.onRecentsShown();
            return;
        }
        ViewGroup decorations = lastDecorationsRef.get();
        if (decorations != null) scheduleBinding(decorations);
    }

    /**
     * findAndSetupViews runs during RecentsContainer inflation, before this subtree has a usable
     * ViewRoot/layout. Keep the stable discovery hook, but wait for real attach/layout authority
     * before constructing any native blur or Prismal producer.
     */
    private static void scheduleBinding(ViewGroup decorations) {
        if (decorations == null) return;
        lastDecorationsRef = new WeakReference<>(decorations);
        if (!GlassRuntimeState.isRecentsCapsuleEnabled()) {
            releasePending("setting-off");
            releaseActive("setting-off");
            return;
        }
        if (activeBinding != null && activeBinding.decorations == decorations) {
            activeBinding.refreshGeometry();
            return;
        }
        if (pendingBinding != null && pendingBinding.decorations == decorations) {
            pendingBinding.tryBind();
            return;
        }
        releasePending("owner-replaced");
        releaseActive("owner-replaced");
        PendingBinding pending = new PendingBinding(decorations);
        pendingBinding = pending;
        pending.start();
    }

    private static void bindReady(ViewGroup decorations) {
        if (decorations == null || !GlassRuntimeState.isRecentsCapsuleEnabled()) return;
        if (!decorations.isAttachedToWindow() || decorations.getWidth() <= 0
                || decorations.getHeight() <= 0) return;
        View sourceRoot = decorations.getRootView();
        if (sourceRoot == null || !sourceRoot.isAttachedToWindow()
                || sourceRoot.getWidth() <= 0 || sourceRoot.getHeight() <= 0) return;

        View clearAll = findByResourceName(decorations, CLEAR_ALL_CAPSULE);
        View world = findByResourceName(decorations, WORLD_CAPSULE);
        if (clearAll == null || world == null) {
            MainHook.log(TAG + " stable capsule resources unavailable; stock retained");
            return;
        }

        try {
            LiquidDockConfig.Glass glass = LiquidDockConfig.load().glass;
            Binding binding = new Binding(decorations, sourceRoot, clearAll, world, glass);
            activeBinding = binding;
            binding.start();
            MainHook.log(TAG + " lifecycle-ready bind started root="
                    + sourceRoot.getWidth() + "x" + sourceRoot.getHeight()
                    + " decorations=" + decorations.getWidth() + "x" + decorations.getHeight());
        } catch (Throwable error) {
            MainHook.log(TAG + " Prismal bind failed; native fallback retained: " + error);
            releaseActive("bind-failure");
        }
    }

    private static View findByResourceName(View root, String name) {
        int id = resourceId(root, name);
        return id != 0 ? root.findViewById(id) : null;
    }

    private static int resourceId(View owner, String name) {
        Resources resources = owner.getResources();
        return resources != null ? resources.getIdentifier(name, "id", LAUNCHER_PACKAGE) : 0;
    }

    private static void releasePending(String reason) {
        PendingBinding pending = pendingBinding;
        pendingBinding = null;
        if (pending != null) {
            pending.release();
            MainHook.log(TAG + " pending bind released reason=" + reason);
        }
    }

    private static void releaseActive(String reason) {
        Binding binding = activeBinding;
        activeBinding = null;
        if (binding != null) {
            binding.release();
            MainHook.log(TAG + " released reason=" + reason);
        }
    }

    private static final class PendingBinding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener {
        final ViewGroup decorations;
        boolean released;
        boolean waitingLogged;

        PendingBinding(ViewGroup decorations) {
            this.decorations = decorations;
        }

        void start() {
            decorations.addOnAttachStateChangeListener(this);
            decorations.addOnLayoutChangeListener(this);
            tryBind();
        }

        void tryBind() { tryBindIfReady(); }

        void tryBindIfReady() {
            if (released || pendingBinding != this) return;
            if (!GlassRuntimeState.isRecentsCapsuleEnabled()) {
                releasePending("pending-setting-off");
                return;
            }
            View root = decorations.getRootView();
            boolean ready = decorations.isAttachedToWindow()
                    && decorations.getWidth() > 0 && decorations.getHeight() > 0
                    && root != null && root.isAttachedToWindow()
                    && root.getWidth() > 0 && root.getHeight() > 0;
            if (!ready) {
                if (!waitingLogged) {
                    waitingLogged = true;
                    MainHook.log(TAG + " waiting for Recents attach/layout"
                            + " attached=" + decorations.isAttachedToWindow()
                            + " size=" + decorations.getWidth() + "x" + decorations.getHeight());
                }
                return;
            }
            pendingBinding = null;
            release();
            MainHook.log(TAG + " Recents attach/layout ready; binding glass");
            bindReady(decorations);
        }

        void release() {
            if (released) return;
            released = true;
            decorations.removeOnAttachStateChangeListener(this);
            decorations.removeOnLayoutChangeListener(this);
        }

        @Override public void onViewAttachedToWindow(View view) { tryBindIfReady(); }

        @Override public void onViewDetachedFromWindow(View view) {
            if (pendingBinding == this) pendingBinding = null;
            release();
        }

        @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            tryBindIfReady();
        }
    }

    private static final class Binding implements ViewTreeObserver.OnPreDrawListener,
            View.OnAttachStateChangeListener, RecentsCapsuleGlassSession.Listener {
        final ViewGroup decorations;
        final View sourceRoot;
        final View clearAll;
        final View world;
        final Drawable clearAllStockBackground;
        final Drawable worldStockBackground;
        final int nativeBlurRadiusPx;
        final RecentsCapsuleGlassSession session;
        RecentsCapsuleGlassSinkView clearAllSink;
        RecentsCapsuleGlassSinkView worldSink;
        boolean clearAllPrismalPresented;
        boolean worldPrismalPresented;
        boolean clearAllNativeFallback;
        boolean worldNativeFallback;
        boolean captureRequested;
        boolean prismalFailed;
        boolean released;
        boolean geometryLogged;

        Binding(ViewGroup decorations, View sourceRoot, View clearAll, View world,
                LiquidDockConfig.Glass glass) {
            this.decorations = decorations;
            this.sourceRoot = sourceRoot;
            this.clearAll = clearAll;
            this.world = world;
            clearAllStockBackground = clearAll.getBackground();
            worldStockBackground = world.getBackground();
            nativeBlurRadiusPx = Math.max(1, Math.round(glass.blur));
            session = new RecentsCapsuleGlassSession(sourceRoot, glass, this);
        }

        void start() {
            applyNativeFallback();
            clearAllSink = installSink(clearAll, RecentsCapsuleGlassSession.Target.CLEAR_ALL);
            worldSink = installSink(world, RecentsCapsuleGlassSession.Target.WORLD);
            decorations.addOnAttachStateChangeListener(this);
            decorations.getViewTreeObserver().addOnPreDrawListener(this);
            refreshGeometry();
            if (clearAllSink == null || worldSink == null) {
                prismalFailed = true;
                MainHook.log(TAG + " capsule local host unavailable; keeping native blur fallback");
                session.shutdown();
            }
        }

        void onRecentsShown() {
            if (!released && !prismalFailed) session.onRecentsShown();
        }

        private RecentsCapsuleGlassSinkView installSink(
                View target, RecentsCapsuleGlassSession.Target targetId) {
            return RecentsCapsuleGlassSinkView.attachInsideTarget(target, session, targetId);
        }

        @Override public boolean onPreDraw() {
            if (released) return true;
            if (!GlassRuntimeState.isRecentsCapsuleEnabled()) {
                releaseActive("pre-draw-setting-off");
                return true;
            }
            refreshGeometry();
            return true;
        }

        void refreshGeometry() {
            if (released || prismalFailed) return;
            if (clearAllSink != null) clearAllSink.syncFromTarget();
            if (worldSink != null) worldSink.syncFromTarget();
            LauncherGlassGeometry.Snapshot clearGeometry =
                    clearAllSink != null ? clearAllSink.captureGeometry(sourceRoot) : null;
            LauncherGlassGeometry.Snapshot worldGeometry =
                    worldSink != null ? worldSink.captureGeometry(sourceRoot) : null;
            RecentsCapsuleGlassSession.GeometrySet next =
                    new RecentsCapsuleGlassSession.GeometrySet(clearGeometry, worldGeometry);
            session.updateGeometry(next);
            if (!geometryLogged && clearGeometry != null && worldGeometry != null) {
                geometryLogged = true;
                MainHook.log(TAG + " geometry clearAll=" + Math.round(clearGeometry.left) + ","
                        + Math.round(clearGeometry.top) + " " + Math.round(clearGeometry.width)
                        + "x" + Math.round(clearGeometry.height)
                        + " world=" + Math.round(worldGeometry.left) + ","
                        + Math.round(worldGeometry.top) + " " + Math.round(worldGeometry.width)
                        + "x" + Math.round(worldGeometry.height)
                        + " localHosts=true parents="
                        + clearAll.getParent().getClass().getSimpleName() + "/"
                        + world.getParent().getClass().getSimpleName());
            }
            if (!captureRequested && (next.clearAll != null || next.world != null)) {
                captureRequested = true;
                session.requestInitialCapture();
            }
        }

        private void applyNativeFallback() {
            if (!clearAllPrismalPresented) {
                clearAllNativeFallback = MiBlurBridge.applyPassWindowBlur(clearAll, nativeBlurRadiusPx);
            }
            if (!worldPrismalPresented) {
                worldNativeFallback = MiBlurBridge.applyPassWindowBlur(world, nativeBlurRadiusPx);
            }
            MainHook.log(TAG + " native fallback clearAll=" + clearAllNativeFallback
                    + " world=" + worldNativeFallback + " blur=" + nativeBlurRadiusPx);
        }

        private void clearNativeFallback(RecentsCapsuleGlassSession.Target target) {
            if (target == RecentsCapsuleGlassSession.Target.CLEAR_ALL) {
                if (clearAllNativeFallback) MiBlurBridge.clearPassWindowBlur(clearAll);
                clearAllNativeFallback = false;
            } else {
                if (worldNativeFallback) MiBlurBridge.clearPassWindowBlur(world);
                worldNativeFallback = false;
            }
        }

        @Override public void onFirstFramePresented(RecentsCapsuleGlassSession.Target target) {
            if (released || prismalFailed) return;
            if (target == RecentsCapsuleGlassSession.Target.CLEAR_ALL) {
                if (clearAllPrismalPresented) return;
                clearAllPrismalPresented = true;
                clearNativeFallback(target);
                clearAll.setBackground(null);
                if (clearAllSink != null) clearAllSink.reveal();
            } else {
                if (worldPrismalPresented) return;
                worldPrismalPresented = true;
                clearNativeFallback(target);
                world.setBackground(null);
                if (worldSink != null) worldSink.reveal();
            }
            MainHook.log(TAG + " Prismal presented target=" + target);
        }

        @Override public void onFailure(Throwable error) {
            if (released || prismalFailed) return;
            prismalFailed = true;
            MainHook.log(TAG + " Prismal session failed; keeping native fallback: " + error);
            restoreStockBackground();
            if (clearAllSink != null) clearAllSink.dispose();
            if (worldSink != null) worldSink.dispose();
            clearAllSink = null;
            worldSink = null;
            clearAllPrismalPresented = false;
            worldPrismalPresented = false;
            applyNativeFallback();
            session.shutdown();
        }

        void restoreStockBackground() {
            if (clearAll.getBackground() == null) clearAll.setBackground(clearAllStockBackground);
            if (world.getBackground() == null) world.setBackground(worldStockBackground);
        }

        void release() {
            if (released) return;
            released = true;
            ViewTreeObserver observer = decorations.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            decorations.removeOnAttachStateChangeListener(this);
            clearNativeFallback(RecentsCapsuleGlassSession.Target.CLEAR_ALL);
            clearNativeFallback(RecentsCapsuleGlassSession.Target.WORLD);
            restoreStockBackground();
            if (clearAllSink != null) clearAllSink.dispose();
            if (worldSink != null) worldSink.dispose();
            clearAllSink = null;
            worldSink = null;
            session.shutdown();
        }

        @Override public void onViewAttachedToWindow(View view) { refreshGeometry(); }

        @Override public void onViewDetachedFromWindow(View view) {
            if (activeBinding == this) activeBinding = null;
            release();
        }
    }
}
