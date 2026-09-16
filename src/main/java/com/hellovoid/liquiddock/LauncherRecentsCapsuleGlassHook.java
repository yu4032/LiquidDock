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
    private static Binding activeBinding;

    private LauncherRecentsCapsuleGlassHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            HookUtil.hookMethod(classLoader, RECENTS_DECORATIONS, "findAndSetupViews", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup) bind((ViewGroup) owner);
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
            releaseActive("runtime-disabled");
            return;
        }
        if (decorations != null && decorations.isAttachedToWindow()) bind(decorations);
    }

    private static void bind(ViewGroup decorations) {
        lastDecorationsRef = new WeakReference<>(decorations);
        if (!GlassRuntimeState.isRecentsCapsuleEnabled()) {
            releaseActive("setting-off");
            return;
        }
        if (activeBinding != null && activeBinding.decorations == decorations) {
            activeBinding.refreshGeometry();
            return;
        }
        releaseActive("owner-replaced");

        View clearAll = findByResourceName(decorations, CLEAR_ALL_CAPSULE);
        View world = findByResourceName(decorations, WORLD_CAPSULE);
        if (clearAll == null || world == null) {
            MainHook.log(TAG + " stable capsule resources unavailable; stock retained");
            return;
        }
        View sourceRoot = decorations.getRootView();
        if (sourceRoot == null || sourceRoot.getWidth() <= 0 || sourceRoot.getHeight() <= 0) {
            MainHook.log(TAG + " Recents root unavailable; stock retained");
            return;
        }

        try {
            LiquidDockConfig.Glass glass = LiquidDockConfig.load().glass;
            Binding binding = new Binding(decorations, sourceRoot, clearAll, world, glass);
            activeBinding = binding;
            binding.start();
        } catch (Throwable error) {
            MainHook.log(TAG + " Prismal bind failed; stock retained: " + error);
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

    private static void releaseActive(String reason) {
        Binding binding = activeBinding;
        activeBinding = null;
        if (binding != null) {
            binding.release();
            MainHook.log(TAG + " released reason=" + reason);
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
        final RecentsCapsuleGlassSession session;
        final RecentsCapsuleGlassOverlay overlay;
        boolean captureRequested;
        boolean presented;
        boolean released;

        Binding(ViewGroup decorations, View sourceRoot, View clearAll, View world,
                LiquidDockConfig.Glass glass) {
            this.decorations = decorations;
            this.sourceRoot = sourceRoot;
            this.clearAll = clearAll;
            this.world = world;
            clearAllStockBackground = clearAll.getBackground();
            worldStockBackground = world.getBackground();
            session = new RecentsCapsuleGlassSession(sourceRoot, glass, this);
            overlay = new RecentsCapsuleGlassOverlay(decorations.getContext(), session);
        }

        void start() {
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            decorations.addView(overlay, 0, lp);
            decorations.addOnAttachStateChangeListener(this);
            decorations.getViewTreeObserver().addOnPreDrawListener(this);
            refreshGeometry();
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
            if (released) return;
            RecentsCapsuleGlassSession.GeometrySet next = new RecentsCapsuleGlassSession.GeometrySet(
                    geometryFor(clearAll), geometryFor(world));
            session.updateGeometry(next);
            if (!captureRequested && (next.clearAll != null || next.world != null)) {
                captureRequested = true;
                session.requestInitialCapture();
            }
        }

        private LauncherGlassGeometry.Snapshot geometryFor(View target) {
            if (target == null || target.getVisibility() != View.VISIBLE
                    || target.getAlpha() <= 0.01f || !target.isShown()
                    || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
            int[] targetLocation = new int[2];
            int[] rootLocation = new int[2];
            target.getLocationOnScreen(targetLocation);
            sourceRoot.getLocationOnScreen(rootLocation);
            float left = targetLocation[0] - rootLocation[0];
            float top = targetLocation[1] - rootLocation[1];
            float right = left + target.getWidth();
            float bottom = top + target.getHeight();
            float radius = Math.min(target.getWidth(), target.getHeight()) * 0.5f;
            return LauncherGlassGeometry.resolveStatic(
                    sourceRoot.getWidth(), sourceRoot.getHeight(),
                    left, top, right, bottom, radius);
        }

        @Override public void onFirstFramePresented() {
            if (released || presented) return;
            presented = true;
            clearAll.setBackground(null);
            world.setBackground(null);
            overlay.reveal();
            MainHook.log(TAG + " full Prismal presented targets=clear-all,device-interconnect");
        }

        @Override public void onFailure(Throwable error) {
            if (released) return;
            MainHook.log(TAG + " Prismal session failed; restoring stock: " + error);
            if (activeBinding == this) activeBinding = null;
            release();
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
            restoreStockBackground();
            overlay.dispose();
            session.shutdown();
        }

        @Override public void onViewAttachedToWindow(View view) { refreshGeometry(); }

        @Override public void onViewDetachedFromWindow(View view) {
            if (activeBinding == this) activeBinding = null;
            release();
        }
    }
}
