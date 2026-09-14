package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Replaces the HyperOS Launcher ShortcutMenu PopupView material with a LiquidDock glass sink.
 *
 * <p>The popup owns a separate ViewRoot, but never owns a PassBlur producer. Its TextureView is
 * bound to the shared session acquired from ShortcutMenu.mDecorView in the main Launcher root.
 */
final class MiuixShortcutMenuGlassHook {
    private static final String TAG = "[DC][ShortcutMenuGlass]";
    private static final String SHORTCUT_MENU = "com.miui.home.launcher.shortcuts.ShortcutMenu";
    private static final String EDIT_STATE_CHANGE_REASON = "com.miui.home.launcher.EditStateChangeReason";
    private static final Map<Object, Binding> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private MiuixShortcutMenuGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        try {
            HookUtil.hookMethod(classLoader, SHORTCUT_MENU, "show", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                onShown(chain.getThisObject(), glassConfig);
                return result;
            });
            HookUtil.hookMethod(classLoader, SHORTCUT_MENU, "dismiss", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                releaseIfDetached(chain.getThisObject());
                return result;
            }, EDIT_STATE_CHANGE_REASON);
            installed = true;
            MainHook.log(TAG + " ShortcutMenu popup hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void onShown(Object menu, LiquidDockConfig.Glass glassConfig) {
        release(menu);
        if (menu == null || glassConfig == null || !GlassRuntimeState.isEnabled()) return;
        try {
            Object decorObject = HookUtil.getField(menu, "mDecorView");
            Object popupObject = HookUtil.getField(menu, "mPopupView");
            if (!(decorObject instanceof View) || popupObject == null) return;
            View decorView = (View) decorObject;
            Method getContentView = popupObject.getClass().getMethod("getContentView");
            Object contentObject = getContentView.invoke(popupObject);
            if (!(contentObject instanceof View)) return;
            View contentView = (View) contentObject;
            Binding binding = new Binding(contentView, decorView, glassConfig);
            ACTIVE.put(menu, binding);
            if (contentView.isAttachedToWindow() && contentView.getParent() instanceof ViewGroup) {
                bindNow(menu, binding);
                return;
            }
            WeakReference<Object> menuRef = new WeakReference<>(menu);
            View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    Object owner = menuRef.get();
                    if (owner != null) bindNow(owner, binding);
                }

                @Override public void onViewDetachedFromWindow(View v) {}
            };
            binding.pendingAttachListener = listener;
            contentView.addOnAttachStateChangeListener(listener);
        } catch (Throwable error) {
            MainHook.log(TAG + " popup discovery failed; stock material retained: " + error);
            release(menu);
        }
    }

    private static void bindNow(Object menu, Binding binding) {
        if (menu == null || binding == null || ACTIVE.get(menu) != binding) return;
        View contentView = binding.contentRef.get();
        View decorView = binding.decorRef.get();
        if (contentView == null || decorView == null
                || !(contentView.getParent() instanceof ViewGroup)) {
            release(menu);
            return;
        }
        if (binding.pendingAttachListener != null) {
            contentView.removeOnAttachStateChangeListener(binding.pendingAttachListener);
            binding.pendingAttachListener = null;
        }
        LauncherGlassSession shared = LauncherGlassSessionRegistry.acquire(
                decorView, binding.glassConfig);
        boolean sharedSessionLive = shared != null && !shared.isShutdown();
        if (!sharedSessionLive) {
            MainHook.log(TAG + " no Launcher producer session; stock material retained");
            release(menu);
            return;
        }
        float cornerRadiusPx = resolveShortcutMenuCornerRadius(contentView);
        LauncherGlassSinkView glassSink = LauncherGlassSinkView.attachToExternalMaterial(
                contentView, shared, cornerRadiusPx, binding.glassConfig);
        boolean sinkAttached = glassSink != null;
        if (!ShortcutPopupMaterialHandoffPolicy.mayReplaceVendorMaterial(
                sharedSessionLive, sinkAttached)) {
            if (glassSink != null) glassSink.dispose();
            MainHook.log(TAG + " external sink unavailable; stock material retained");
            release(menu);
            return;
        }
        binding.sink = glassSink;
        View.OnAttachStateChangeListener detachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                release(menu);
            }
        };
        binding.detachListener = detachListener;
        contentView.addOnAttachStateChangeListener(detachListener);

        clearVendorPopupMaterial(contentView);
        glassSink.setNodeKind(LauncherGlassNodeKind.LARGE_FOLDER);
        glassSink.requestLifecycleRefresh();
        MainHook.log(TAG + " bound PopupView sink to " + shared.debugLabel());
    }

    private static void clearVendorPopupMaterial(View contentView) {
        if (contentView == null) return;
        MiBlurBridge.clearContentBlur(contentView);
        contentView.setBackgroundColor(Color.TRANSPARENT);
        contentView.setElevation(0f);
    }

    private static float resolveShortcutMenuCornerRadius(View contentView) {
        if (contentView == null) return 0f;
        try {
            int id = contentView.getResources().getIdentifier(
                    "shortcut_menu_angle_radius", "dimen", "com.miui.home");
            if (id != 0) return contentView.getResources().getDimension(id);
        } catch (Throwable ignored) {}
        return 16f * contentView.getResources().getDisplayMetrics().density;
    }

    private static void releaseIfDetached(Object menu) {
        Binding binding = ACTIVE.get(menu);
        View content = binding != null ? binding.contentRef.get() : null;
        if (content == null || !content.isAttachedToWindow()) release(menu);
    }

    private static void release(Object menu) {
        if (menu == null) return;
        Binding binding = ACTIVE.remove(menu);
        if (binding == null) return;
        View content = binding.contentRef.get();
        if (content != null) {
            if (binding.pendingAttachListener != null) {
                content.removeOnAttachStateChangeListener(binding.pendingAttachListener);
            }
            if (binding.detachListener != null) {
                content.removeOnAttachStateChangeListener(binding.detachListener);
            }
        }
        LauncherGlassSinkView sink = binding.sink;
        binding.sink = null;
        if (sink != null) sink.dispose();
    }

    private static final class Binding {
        final WeakReference<View> contentRef;
        final WeakReference<View> decorRef;
        final LiquidDockConfig.Glass glassConfig;
        LauncherGlassSinkView sink;
        View.OnAttachStateChangeListener pendingAttachListener;
        View.OnAttachStateChangeListener detachListener;

        Binding(View content, View decor, LiquidDockConfig.Glass glassConfig) {
            contentRef = new WeakReference<>(content);
            decorRef = new WeakReference<>(decor);
            this.glassConfig = glassConfig;
        }
    }
}
