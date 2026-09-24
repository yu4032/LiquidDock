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
 * Replaces HyperOS app-caption popup backgrounds with compositor-backed pass-window blur while
 * preserving native controls.
 *
 * <p>HyperOS has two menu implementations in the SystemUI/WMShell stack. Xiaomi's primary path
 * creates a {@code MiuiCaptionContainerView} and passes it through
 * {@code MiuiDecorationDot.addWindow(...)} into a dedicated captionMenu SurfaceControlViewHost.
 * AOSP/WMShell builds use {@code desktop_mode_window_decor_handle_menu}. Both creation boundaries
 * are observed; blur replacement waits for real attach/layout authority and never binds a
 * RootPassBlur producer to the menu-local Windowless ViewRoot.</p>
 */
final class SystemUiHandleMenuGlassHook {
    private static final String TAG = "[DC][SystemUiHandleMenuGlass]";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String MIUI_DECORATION_DOT =
            "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot";
    private static final String MIUI_WINDOW_CONTROLLER =
            "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.handlemenu.MiuiWindowController";
    private static final String HANDLE_MENU_LAYOUT = "desktop_mode_window_decor_handle_menu";
    private static final String CAPTION_MENU_CONTAINER = "caption_menu_container";
    private static final String WINDOWING_PILL = "windowing_pill";
    private static final String MIUI_CAPTION_CONTAINER_SIMPLE_NAME = "MiuiCaptionContainerView";

    private static final Map<View, PendingBinding> PENDING =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Binding> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Binding> CONTROLLERS =
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
                        int menuX = intArg(args, 1);
                        int menuY = intArg(args, 2);
                        int menuWidth = intArg(args, 3);
                        int menuHeight = intArg(args, 4);
                        Object result = chain.proceed(args);
                        SurfaceControl menuSurface =
                                SystemUiHandleMenuSurfaceProbe.trackController(result);
                        if (menuRoot != null) {
                            log("MIUI caption menu window created root="
                                    + menuRoot.getClass().getName()
                                    + " attached=" + menuRoot.isAttachedToWindow()
                                    + " surface=" + menuSurface);
                            observeMenu(
                                    menuRoot,
                                    "miui-caption-window",
                                    result,
                                    menuSurface,
                                    true,
                                    menuX,
                                    menuY,
                                    menuWidth,
                                    menuHeight);
                        }
                        return result;
                    });
            installedCount++;
            log("MIUI captionMenu addWindow hook installed");
        } catch (Throwable error) {
            log("MIUI captionMenu hook unavailable: " + error);
        }

        try {
            Class<?> controller = Class.forName(MIUI_WINDOW_CONTROLLER, false, classLoader);
            HookUtil.hookMethod(
                    controller,
                    "releaseViewWithAnim",
                    new Class<?>[0],
                    chain -> {
                        Object owner = chain.getThisObject();
                        Binding binding = CONTROLLERS.get(owner);
                        if (binding != null) binding.prepareForNativeClose();
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    });
            installedCount++;
            log("MIUI captionMenu close boundary hook installed");
        } catch (Throwable error) {
            log("MIUI captionMenu close hook unavailable: " + error);
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
                        if (target && result instanceof View) {
                            View root = (View) result;
                            log("AOSP HandleMenu layout inflated root=" + root.getClass().getName());
                            observeMenu(root, "aosp-handle-menu", null, null, false,
                                    0, 0, 0, 0);
                        }
                        return result;
                    });
            installedCount++;
            log("AOSP HandleMenu layout inflation hook installed");
        } catch (Throwable error) {
            log("AOSP HandleMenu hook unavailable: " + error);
        }

        installed = installedCount > 0;
        if (!installed) {
            glassConfig = null;
            log("no supported HyperOS caption-menu hook available");
        }
    }

    private static int intArg(Object[] args, int index) {
        return args != null && index >= 0 && index < args.length && args[index] instanceof Integer
                ? (Integer) args[index]
                : 0;
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
            String source,
            Object controller,
            SurfaceControl menuSurface,
            boolean waitForNativeSurfaceScale,
            int menuX,
            int menuY,
            int menuWidth,
            int menuHeight) {
        LiquidDockConfig.Glass glass = glassConfig;
        if (root == null || glass == null || !glass.enabled
                || !glass.systemUiHandleMenuEnabled) return;

        releaseRoot(root, "menu-replaced");
        PendingBinding pending = new PendingBinding(
                root, glass, controller, menuSurface, waitForNativeSurfaceScale,
                menuX, menuY, menuWidth, menuHeight);
        PENDING.put(root, pending);
        pending.start();
        log("menu root observed source=" + source
                + " class=" + root.getClass().getName()
                + " attached=" + root.isAttachedToWindow()
                + " size=" + root.getWidth() + "x" + root.getHeight()
                + " nativePlacement=" + menuX + "," + menuY + ","
                + menuWidth + "x" + menuHeight);
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
        final Object controller;
        final SurfaceControl menuSurface;
        final boolean waitForNativeSurfaceScale;
        final int menuX;
        final int menuY;
        final int menuWidth;
        final int menuHeight;
        boolean released;

        PendingBinding(
                View root,
                LiquidDockConfig.Glass glass,
                Object controller,
                SurfaceControl menuSurface,
                boolean waitForNativeSurfaceScale,
                int menuX,
                int menuY,
                int menuWidth,
                int menuHeight) {
            this.root = root;
            this.glass = glass;
            this.controller = controller;
            this.menuSurface = menuSurface;
            this.waitForNativeSurfaceScale = waitForNativeSurfaceScale;
            this.menuX = menuX;
            this.menuY = menuY;
            this.menuWidth = menuWidth;
            this.menuHeight = menuHeight;
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
            SystemUiHandleMenuSurfaceProbe.trackRoot(sourceRoot);
            try {
                Binding binding = new Binding(
                        root,
                        sourceRoot,
                        target,
                        glass,
                        controller,
                        menuSurface,
                        waitForNativeSurfaceScale,
                        menuX,
                        menuY,
                        menuWidth,
                        menuHeight);
                ACTIVE.put(root, binding);
                binding.start();
                log("caption menu blur replacement bind started target=" + targetLabel(target)
                        + " popupRoot=" + sourceRoot.getWidth() + "x" + sourceRoot.getHeight());
            } catch (Throwable error) {
                log("blur replacement bind failed; stock retained: " + error);
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
        final SystemUiHandleMenuAnimationProbe animationProbe;
        final Object controller;
        final SurfaceControl menuSurface;
        final boolean trackNativeSurfaceAnimation;
        final int menuX;
        final int menuY;
        final int menuWidth;
        final int menuHeight;
        final SystemUiHandleMenuSurfaceProbe.AlphaListener alphaListener;

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
                Object controller,
                SurfaceControl menuSurface,
                boolean waitForNativeSurfaceScale,
                int menuX,
                int menuY,
                int menuWidth,
                int menuHeight) {
            this.root = root;
            this.sourceRoot = sourceRoot;
            this.target = target;
            this.controller = controller;
            this.menuSurface = menuSurface;
            this.trackNativeSurfaceAnimation = waitForNativeSurfaceScale && menuSurface != null;
            this.menuX = menuX;
            this.menuY = menuY;
            this.menuWidth = menuWidth;
            this.menuHeight = menuHeight;
            stockBackground = target.getBackground();
            glassConfig = glass;
            nativeBlurRadiusPx = Math.max(1, Math.round(glass.blur));
            animationProbe = new SystemUiHandleMenuAnimationProbe(root, sourceRoot, target);
            alphaListener = this::onNativeSurfaceAlpha;
        }

        void start() {
            root.addOnAttachStateChangeListener(this);
            animationProbe.start();
            if (controller != null) CONTROLLERS.put(controller, this);
            pendingSurfaceAlpha = trackNativeSurfaceAnimation ? 0f : 1f;
            applyReplacementBlur();
            if (trackNativeSurfaceAnimation) {
                SystemUiHandleMenuSurfaceProbe.registerAlphaListener(menuSurface, alphaListener);
                log("replacement blur tracking native menu surface alpha surface=" + menuSurface);
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
                                        root.post(Binding.this::onPrismalPresented);
                                    }

                                    @Override public void onFailure(Throwable error) {
                                        root.post(() -> onPrismalFailure(error));
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
                RootPassBlurEndpointBridge.Endpoint endpoint =
                        RootPassBlurEndpointBridge.inspect(sourceRoot);
                log("Prismal pipeline armed sourceViewRoot="
                        + (endpoint != null ? endpoint.rootSurface : "<unavailable>")
                        + " target=" + targetLabel(target)
                        + " size=" + target.getWidth() + "x" + target.getHeight());
            } catch (Throwable error) {
                onPrismalFailure(error);
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
            log("full Prismal glass presented; native sampler retained at blur=0"
                    + " target=" + targetLabel(target));
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

        void prepareForNativeClose() {
            if (released) return;
            log("native close boundary; glass material retained through surface scale-out");
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
            log("replacement pass-window blur armed before surface animation target="
                    + targetLabel(target)
                    + " maxBlur=" + nativeBlurRadiusPx
                    + " initialBlur=" + initialRadius
                    + " fadeStartAlpha=0.80"
                    + " popupRoot=" + sourceRoot.getWidth() + "x" + sourceRoot.getHeight());
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
            root.removeOnAttachStateChangeListener(this);
            animationProbe.stop();
            if (controller != null && CONTROLLERS.get(controller) == this) {
                CONTROLLERS.remove(controller);
            }
            if (menuSurface != null) {
                SystemUiHandleMenuSurfaceProbe.unregisterAlphaListener(
                        menuSurface, alphaListener);
            }
            SystemUiHandleMenuGlassOutputView output = prismalOutput;
            prismalOutput = null;
            if (output != null) output.dispose();
            SystemUiHandleMenuPrismalSession session = prismalSession;
            prismalSession = null;
            if (session != null) session.shutdown();
            prismalPresented = false;
            restoreStockBackground();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            applyReplacementBlur();
            startPrismal();
        }

        @Override
        public void onViewDetachedFromWindow(View view) {
            if (ACTIVE.get(root) == this) ACTIVE.remove(root);
            log("caption menu detached");
            release();
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
