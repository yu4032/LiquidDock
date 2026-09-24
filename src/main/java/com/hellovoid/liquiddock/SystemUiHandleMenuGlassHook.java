package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces HyperOS app-caption popup backgrounds with compositor-backed native glass while
 * preserving native controls.
 *
 * <p>HyperOS has two menu implementations in the SystemUI/WMShell stack. Xiaomi's primary path
 * creates a {@code MiuiCaptionContainerView} and passes it through
 * {@code MiuiDecorationDot.addWindow(...)} into a dedicated captionMenu SurfaceControlViewHost.
 * AOSP/WMShell builds use {@code desktop_mode_window_decor_handle_menu}. Both creation boundaries
 * are observed; glass binding waits for real attach/layout authority and never binds a
 * RootPassBlur producer to the menu-local Windowless ViewRoot.</p>
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
                        if (menuRoot != null) {
                            log("MIUI caption menu window created root="
                                    + menuRoot.getClass().getName()
                                    + " attached=" + menuRoot.isAttachedToWindow());
                            observeMenu(menuRoot, "miui-caption-window");
                        }
                        return result;
                    });
            installedCount++;
            log("MIUI captionMenu addWindow hook installed");
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
                        if (target && result instanceof View) {
                            View root = (View) result;
                            log("AOSP HandleMenu layout inflated root=" + root.getClass().getName());
                            observeMenu(root, "aosp-handle-menu");
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

    private static void observeMenu(View root, String source) {
        LiquidDockConfig.Glass glass = glassConfig;
        if (root == null || glass == null || !glass.enabled
                || !glass.systemUiHandleMenuEnabled) return;

        releaseRoot(root, "menu-replaced");
        PendingBinding pending = new PendingBinding(root, glass);
        PENDING.put(root, pending);
        pending.start();
        log("menu root observed source=" + source
                + " class=" + root.getClass().getName()
                + " attached=" + root.isAttachedToWindow()
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

            View target = resolveGlassTarget(root);
            if (!(root instanceof ViewGroup) || target == null) {
                log("caption menu glass host/target unavailable; stock retained"
                        + " rootClass=" + root.getClass().getName());
                PENDING.remove(root);
                release();
                return;
            }

            PENDING.remove(root);
            release();
            SystemUiHandleMenuSurfaceProbe.trackRoot(sourceRoot);
            try {
                Binding binding = new Binding(root, sourceRoot, target, glass);
                ACTIVE.put(root, binding);
                binding.start();
                log("caption menu native glass bind started target=" + targetLabel(target)
                        + " popupRoot=" + sourceRoot.getWidth() + "x" + sourceRoot.getHeight());
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

    /**
     * Windowless caption menus are not safe RootPassBlur producer roots on this HyperOS build.
     * Use HyperOS' own pass-window backdrop material directly on the menu target. This still
     * samples compositor content behind the popup, but never calls SetPassBlurSurface on the
     * menu-local 758x147 ViewRoot that was observed to kill SystemUI.
     */
    private static final class Binding implements View.OnAttachStateChangeListener {
        final View root;
        final View sourceRoot;
        final View target;
        final Drawable stockBackground;
        final int nativeBlurRadiusPx;
        final SystemUiHandleMenuAnimationProbe animationProbe;

        boolean nativeGlassApplied;
        boolean released;

        Binding(
                View root,
                View sourceRoot,
                View target,
                LiquidDockConfig.Glass glass) {
            this.root = root;
            this.sourceRoot = sourceRoot;
            this.target = target;
            stockBackground = target.getBackground();
            nativeBlurRadiusPx = Math.max(1, Math.round(glass.blur));
            animationProbe = new SystemUiHandleMenuAnimationProbe(root, sourceRoot, target);
        }

        void start() {
            root.addOnAttachStateChangeListener(this);
            animationProbe.start();
            applyNativeGlass();
        }

        private void applyNativeGlass() {
            if (released || nativeGlassApplied || !root.isAttachedToWindow()
                    || !target.isAttachedToWindow()) return;
            boolean applied = MiBlurBridge.applyPassWindowBlur(target, nativeBlurRadiusPx);
            if (!applied) {
                log("native pass-window glass unavailable; stock retained"
                        + " target=" + targetLabel(target));
                return;
            }
            nativeGlassApplied = true;
            target.setBackground(null);
            target.invalidate();
            log("native pass-window glass presented target=" + targetLabel(target)
                    + " blur=" + nativeBlurRadiusPx
                    + " popupRoot=" + sourceRoot.getWidth() + "x" + sourceRoot.getHeight());
        }

        private void restoreStockBackground() {
            if (nativeGlassApplied) {
                MiBlurBridge.clearPassWindowBlur(target);
                nativeGlassApplied = false;
            }
            if (target.getBackground() == null) target.setBackground(stockBackground);
        }

        void release() {
            if (released) return;
            released = true;
            root.removeOnAttachStateChangeListener(this);
            animationProbe.stop();
            restoreStockBackground();
        }

        @Override
        public void onViewAttachedToWindow(View view) {
            applyNativeGlass();
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
