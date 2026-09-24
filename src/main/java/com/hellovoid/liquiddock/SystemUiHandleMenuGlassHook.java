package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces HyperOS app-caption popup backgrounds with zero-copy Prismal glass while preserving
 * native controls and native WMShell animation authority.
 *
 * <p>Xiaomi's primary path creates a {@code MiuiCaptionContainerView} through
 * {@code MiuiDecorationDot.addWindow(...)}. AOSP/WMShell builds use
 * {@code desktop_mode_window_decor_handle_menu}. The native pass-window material remains a
 * fail-open sampler until Prismal presents a real source frame.</p>
 */
final class SystemUiHandleMenuGlassHook {
    private static final String TAG = "[DC][SystemUiHandleMenuGlass]";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MIUI_DECORATION_DOT =
            "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot";
    private static final String HANDLE_MENU_LAYOUT = "desktop_mode_window_decor_handle_menu";
    private static final String CAPTION_MENU_CONTAINER = "caption_menu_container";
    private static final String WINDOWING_PILL = "windowing_pill";
    private static final String MIUI_CAPTION_CONTAINER_SIMPLE_NAME = "MiuiCaptionContainerView";

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

        int installedCount = 0;
        try {
            Class<?> miuiDecorationDot = Class.forName(MIUI_DECORATION_DOT, false, classLoader);
            HookUtil.hookMethod(
                    miuiDecorationDot,
                    "addWindow",
                    new Class<?>[]{
                            View.class,
                            int.class, int.class, int.class, int.class,
                            int.class, int.class,
                            boolean.class
                    },
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        View menuRoot = args.length > 0 && args[0] instanceof View
                                ? (View) args[0]
                                : null;
                        Object result = chain.proceed(args);
                        try {
                            SurfaceControl menuSurface =
                                    SystemUiHandleMenuSurfaceAnimationAuthority.windowSurface(result);
                            if (menuRoot != null) {
                                observeMenu(menuRoot, menuSurface, true);
                            }
                        } catch (Throwable error) {
                            log("MIUI captionMenu observation failed; original result preserved: "
                                    + error);
                        }
                        return result;
                    });
            installedCount++;
        } catch (Throwable error) {
            log("MIUI captionMenu hook unavailable: " + error);
        }

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
                        try {
                            if (target && result instanceof View) {
                                View root = (View) result;
                                observeMenu(root, null, false);
                            }
                        } catch (Throwable error) {
                            log("AOSP HandleMenu observation failed; original result preserved: "
                                    + error);
                        }
                        return result;
                    });
            installedCount++;
        } catch (Throwable error) {
            log("AOSP HandleMenu hook unavailable: " + error);
        }

        installed = installedCount > 0;
        if (!installed) {
            glassConfig = null;
            log("no supported HyperOS caption-menu hook available");
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

    private static void observeMenu(
            View root,
            SurfaceControl menuSurface,
            boolean trackNativeSurfaceAnimation) {
        LiquidDockConfig.Glass glass = glassConfig;
        if (root == null || glass == null || !glass.enabled
                || !glass.systemUiHandleMenuEnabled) return;

        releaseRoot(root);
        PendingBinding pending = new PendingBinding(
                root, glass, menuSurface, trackNativeSurfaceAnimation);
        PENDING.put(root, pending);
        pending.start();
    }

    private static void releaseRoot(View root) {
        if (root == null) return;
        PendingBinding pending = PENDING.remove(root);
        if (pending != null) pending.release();
        Binding active = ACTIVE.remove(root);
        if (active != null) active.release();
    }

    private static final class PendingBinding implements View.OnAttachStateChangeListener,
            View.OnLayoutChangeListener {
        final View root;
        final LiquidDockConfig.Glass glass;
        final SurfaceControl menuSurface;
        final boolean waitForNativeSurfaceScale;
        boolean released;

        PendingBinding(
                View root,
                LiquidDockConfig.Glass glass,
                SurfaceControl menuSurface,
                boolean waitForNativeSurfaceScale) {
            this.root = root;
            this.glass = glass;
            this.menuSurface = menuSurface;
            this.waitForNativeSurfaceScale = waitForNativeSurfaceScale;
        }

        void start() {
            try {
                root.addOnAttachStateChangeListener(this);
                root.addOnLayoutChangeListener(this);
                tryBind();
            } catch (Throwable error) {
                log("pending HandleMenu listener setup failed; stock retained: " + error);
                if (PENDING.get(root) == this) PENDING.remove(root);
                release();
            }
        }

        void tryBind() {
            try {
                tryBindInternal();
            } catch (Throwable error) {
                log("pending HandleMenu bind observation failed; stock retained: " + error);
                if (PENDING.get(root) == this) PENDING.remove(root);
                release();
            }
        }

        private void tryBindInternal() {
            if (released || PENDING.get(root) != this) return;
            View sourceRoot = root.getRootView();
            if (!root.isAttachedToWindow() || root.getWidth() <= 0 || root.getHeight() <= 0
                    || sourceRoot == null || !sourceRoot.isAttachedToWindow()
                    || sourceRoot.getWidth() <= 0 || sourceRoot.getHeight() <= 0) {
                return;
            }

            View target = resolveGlassTarget(root);
            if (!(root instanceof ViewGroup) || target == null) {
                log("caption menu blur host/target unavailable; stock retained"
                        + " rootClass=" + root.getClass().getName());
                PENDING.remove(root);
                release();
                return;
            }

            PENDING.remove(root);
            release();
            try {
                Binding binding = new Binding(
                        root,
                        sourceRoot,
                        target,
                        glass,
                        menuSurface,
                        waitForNativeSurfaceScale);
                ACTIVE.put(root, binding);
                binding.start();
            } catch (Throwable error) {
                log("blur replacement bind failed; stock retained: " + error);
                Binding active = ACTIVE.remove(root);
                if (active != null) active.release();
            }
        }

        void release() {
            if (released) return;
            released = true;
            try { root.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            try { root.removeOnLayoutChangeListener(this); } catch (Throwable ignored) {}
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            tryBind();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            try {
                if (PENDING.get(root) == this) PENDING.remove(root);
                release();
            } catch (Throwable error) {
                log("pending HandleMenu detach cleanup failed: " + error);
            }
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

    /**
     * Native pass-window blur remains the fail-open presentation. Prismal exports the same
     * caption-window ViewRoot compositor backdrop through a dedicated short-lived producer,
     * avoiding the generic RootPassBlurBackend lifecycle that previously proved unsafe here.
     */
    private static final class Binding implements View.OnAttachStateChangeListener {
        final View root;
        final View sourceRoot;
        final View target;
        final Drawable stockBackground;
        final int nativeBlurRadiusPx;
        final LiquidDockConfig.Glass glassConfig;
        final SurfaceControl menuSurface;
        final boolean trackNativeSurfaceAnimation;
        final SystemUiHandleMenuSurfaceAnimationAuthority.AlphaListener alphaListener;

        boolean replacementBlurApplied;
        boolean customPassBlurOwned;
        boolean prismalPresented;
        SystemUiHandleMenuPrismalSession prismalSession;
        SystemUiHandleMenuGlassOutputView prismalOutput;
        volatile float pendingSurfaceAlpha;
        boolean fadeUpdatePosted;
        int lastAppliedBlurRadius = -1;
        boolean released;

        Binding(
                View root,
                View sourceRoot,
                View target,
                LiquidDockConfig.Glass glass,
                SurfaceControl menuSurface,
                boolean waitForNativeSurfaceScale) {
            this.root = root;
            this.sourceRoot = sourceRoot;
            this.target = target;
            this.menuSurface = menuSurface;
            this.trackNativeSurfaceAnimation = waitForNativeSurfaceScale && menuSurface != null;
            stockBackground = target.getBackground();
            glassConfig = glass;
            nativeBlurRadiusPx = Math.max(1, Math.round(glass.blur));
            alphaListener = this::onNativeSurfaceAlpha;
        }

        void start() {
            root.addOnAttachStateChangeListener(this);
            pendingSurfaceAlpha = trackNativeSurfaceAnimation ? 0f : 1f;
            applyReplacementBlur();
            if (trackNativeSurfaceAnimation) {
                SystemUiHandleMenuSurfaceAnimationAuthority.registerAlphaListener(
                        menuSurface, alphaListener);
            }
            startPrismal();
        }

        private void onNativeSurfaceAlpha(float alpha) {
            if (released) return;
            pendingSurfaceAlpha = Math.max(0f, Math.min(1f, alpha));
            postFadeUpdate();
        }

        private void postFadeUpdate() {
            if (released || fadeUpdatePosted) return;
            fadeUpdatePosted = true;
            boolean posted = root.post(() -> {
                try {
                    if (!released) applyMaterialFade(pendingSurfaceAlpha);
                } catch (Throwable error) {
                    log("material fade failed; preserving SystemUI animation: " + error);
                    if (menuSurface != null) {
                        SystemUiHandleMenuSurfaceAnimationAuthority.unregisterAlphaListener(
                                menuSurface, alphaListener);
                    }
                } finally {
                    fadeUpdatePosted = false;
                }
            });
            if (!posted) {
                fadeUpdatePosted = false;
                log("material fade post rejected target=" + targetLabel(target));
            }
        }

        private float materialFade(float surfaceAlpha) {
            // Suppress material visibility during most of the outer Surface scale animation, then
            // reveal the finished glass near the settled size. Close naturally reverses this.
            float t = (surfaceAlpha - 0.80f) / 0.20f;
            t = Math.max(0f, Math.min(1f, t));
            return t * t * (3f - (2f * t));
        }

        private void applyMaterialFade(float surfaceAlpha) {
            float eased = materialFade(surfaceAlpha);
            SystemUiHandleMenuGlassOutputView output = prismalOutput;
            if (prismalPresented && output != null) {
                output.setMaterialAlpha(eased);
                return;
            }
            if (!replacementBlurApplied || !customPassBlurOwned) return;
            int radius = Math.round(nativeBlurRadiusPx * eased);
            if (radius == lastAppliedBlurRadius) return;
            if (MiBlurBridge.setPassWindowBlurRadius(target, radius)) {
                lastAppliedBlurRadius = radius;
            }
        }

        private void startPrismal() {
            if (released || menuSurface == null || !menuSurface.isValid()
                    || prismalSession != null || !(target instanceof ViewGroup)
                    || target.getWidth() <= 0 || target.getHeight() <= 0) return;
            try {
                SystemUiHandleMenuPrismalSession session =
                        new SystemUiHandleMenuPrismalSession(
                                target,
                                sourceRoot,
                                glassConfig,
                                new SystemUiHandleMenuPrismalSession.Listener() {
                                    @Override public void onFirstFramePresented() {
                                        postBindingCallback(
                                                "Prismal presentation",
                                                Binding.this::onPrismalPresented);
                                    }

                                    @Override public void onFailure(Throwable error) {
                                        postBindingCallback(
                                                "Prismal failure fallback",
                                                () -> onPrismalFailure(error));
                                    }
                                });
                // Queue EGL/source initialization before the output child publishes its
                // initial visual size. The session also tolerates an early resize callback, but
                // keeping initialization first makes the lifecycle deterministic.
                session.start(target.getWidth(), target.getHeight());
                SystemUiHandleMenuGlassOutputView output =
                        SystemUiHandleMenuGlassOutputView.attachInsideTarget(target, session);
                if (output == null) {
                    session.shutdown();
                    return;
                }
                prismalSession = session;
                prismalOutput = output;
                output.setMaterialAlpha(0f);
            } catch (Throwable error) {
                onPrismalFailure(error);
            }
        }

        private void postBindingCallback(String operation, Runnable action) {
            if (released || action == null) return;
            try {
                boolean posted = root.post(() -> {
                    try {
                        if (!released) action.run();
                    } catch (Throwable error) {
                        log(operation + " callback failed; SystemUI preserved: " + error);
                    }
                });
                if (!posted) {
                    log(operation + " callback rejected because menu root is no longer active");
                }
            } catch (Throwable error) {
                log(operation + " callback enqueue failed; SystemUI preserved: " + error);
            }
        }

        private void onPrismalPresented() {
            if (released || prismalSession == null || prismalOutput == null) return;
            prismalPresented = true;
            if (customPassBlurOwned) {
                // Keep the compositor pass-window gate alive: Prismal's exported OES producer
                // depends on the same native backdrop sampler. Only hide the visible fallback by
                // reducing its radius to zero; full disable is deferred until real detach.
                MiBlurBridge.setPassWindowBlurEnabled(target, true);
                MiBlurBridge.setPassWindowBlurRadius(target, 0);
                lastAppliedBlurRadius = 0;
            }
            target.setBackground(null);
            prismalOutput.setMaterialAlpha(materialFade(pendingSurfaceAlpha));
            target.invalidate();
        }

        private void onPrismalFailure(Throwable error) {
            if (released) return;
            log("Prismal fallback to native blur: " + error);
            SystemUiHandleMenuGlassOutputView output = prismalOutput;
            prismalOutput = null;
            if (output != null) output.dispose();
            SystemUiHandleMenuPrismalSession session = prismalSession;
            prismalSession = null;
            if (session != null) session.shutdown();
            prismalPresented = false;
            if (!replacementBlurApplied || !customPassBlurOwned) {
                applyReplacementBlur();
            }
            applyMaterialFade(pendingSurfaceAlpha);
        }

        private void applyReplacementBlur() {
            if (released || replacementBlurApplied || !root.isAttachedToWindow()
                    || !target.isAttachedToWindow()) return;

            int initialRadius = trackNativeSurfaceAnimation ? 0 : nativeBlurRadiusPx;
            boolean applied = MiBlurBridge.applyPassWindowBlur(target, initialRadius);
            if (!applied) {
                log("replacement pass-window blur unavailable; stock retained"
                        + " target=" + targetLabel(target));
                return;
            }
            replacementBlurApplied = true;
            customPassBlurOwned = true;
            lastAppliedBlurRadius = initialRadius;
            target.setBackground(null);
            target.invalidate();
        }

        private void restoreStockBackground() {
            if (customPassBlurOwned) {
                MiBlurBridge.clearPassWindowBlur(target);
                customPassBlurOwned = false;
            }
            lastAppliedBlurRadius = -1;
            replacementBlurApplied = false;
            if (target.getBackground() == null && stockBackground != null) {
                target.setBackground(stockBackground);
            }
        }

        void release() {
            if (released) return;
            released = true;
            try { root.removeOnAttachStateChangeListener(this); } catch (Throwable ignored) {}
            try {
                if (menuSurface != null) {
                    SystemUiHandleMenuSurfaceAnimationAuthority.unregisterAlphaListener(
                            menuSurface, alphaListener);
                }
            } catch (Throwable error) {
                log("Surface alpha listener cleanup failed: " + error);
            }
            SystemUiHandleMenuGlassOutputView output = prismalOutput;
            prismalOutput = null;
            if (output != null) {
                try { output.dispose(); } catch (Throwable error) {
                    log("Prismal output cleanup failed: " + error);
                }
            }
            SystemUiHandleMenuPrismalSession session = prismalSession;
            prismalSession = null;
            if (session != null) {
                try { session.shutdown(); } catch (Throwable error) {
                    log("Prismal session cleanup failed: " + error);
                }
            }
            prismalPresented = false;
            try { restoreStockBackground(); } catch (Throwable error) {
                log("stock background restore failed: " + error);
            }
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            try {
                applyReplacementBlur();
                startPrismal();
            } catch (Throwable error) {
                log("active HandleMenu attach failed; stock path retained: " + error);
            }
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            try {
                if (ACTIVE.get(root) == this) ACTIVE.remove(root);
                release();
            } catch (Throwable error) {
                log("active HandleMenu detach cleanup failed: " + error);
            }
        }
    }

    private static View resolveGlassTarget(View root) {
        View miui = findByResourceName(root, CAPTION_MENU_CONTAINER);
        if (miui instanceof ViewGroup) return miui;

        View aosp = findByResourceName(root, WINDOWING_PILL);
        if (aosp instanceof ViewGroup) return aosp;

        if (root instanceof ViewGroup
                && MIUI_CAPTION_CONTAINER_SIMPLE_NAME.equals(root.getClass().getSimpleName())) {
            return root;
        }
        return null;
    }

    private static String targetLabel(View target) {
        if (target == null) return "<null>";
        try {
            int id = target.getId();
            if (id != View.NO_ID) return target.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {}
        return target.getClass().getSimpleName();
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
