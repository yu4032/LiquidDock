package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** HyperOS 4.50 ShortcutMenu glass and optional dark-mode content adaptation. */
final class MiuixShortcutMenuGlassHook {
    private static final String TAG = "[DC][ShortcutMenuGlass]";
    private static final String SHORTCUT_MENU = "com.miui.home.launcher.shortcuts.ShortcutMenu";
    private static final String SHORTCUT_MENU_LAYER = "com.miui.home.launcher.ShortcutMenuLayer";
    private static final String ITEM_INFO = "com.miui.home.launcher.ItemInfo";
    private static final String EDIT_STATE_CHANGE_REASON = "com.miui.home.launcher.EditStateChangeReason";
    private static boolean installed;

    private MiuixShortcutMenuGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        ConfigReader preferences = ConfigReader.load();
        boolean popupGlassEnabled = preferences.b(
                com.hellovoid.liquiddock.config.ConfigSchema.Glass.SHORTCUT_POPUP_GLASS.name(),
                com.hellovoid.liquiddock.config.ConfigSchema.Glass.SHORTCUT_POPUP_GLASS.runtimeFallback());
        boolean darkModeEnabled = preferences.b(
                com.hellovoid.liquiddock.config.ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT.name(),
                com.hellovoid.liquiddock.config.ConfigSchema.Glass.SHORTCUT_POPUP_DARK_TEXT.runtimeFallback());
        if (!popupGlassEnabled && !darkModeEnabled) {
            MainHook.log(TAG + " disabled by shortcut popup settings");
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        try {
            if (popupGlassEnabled) {
                installShortcutMenuLayerPrewarm(classLoader, glassConfig);
                HookUtil.hookMethod(classLoader, SHORTCUT_MENU_LAYER, "setRequestingItemInfo", chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object itemInfo = args.length > 0 ? args[0] : null;
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) {
                        View ownerView = (View) owner;
                        View launcherRoot = ownerView.getRootView();
                        if (itemInfo != null) {
                            ShortcutPopupGlassCoordinator.prepare(launcherRoot, glassConfig);
                        }
                        Object result = chain.proceed(args);
                        if (itemInfo == null) {
                            launcherRoot.postOnAnimation(
                                    () -> ShortcutPopupGlassCoordinator.cancelPending(launcherRoot));
                        }
                        return result;
                    }
                    return chain.proceed(args);
                }, ITEM_INFO);
            }

            HookUtil.hookMethod(classLoader, SHORTCUT_MENU, "show", chain -> {
                boolean glassReady = !popupGlassEnabled
                        || ShortcutPopupGlassCoordinator.acceptPreShowBackdrop();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                bindShownPopup(
                        chain.getThisObject(),
                        popupGlassEnabled && glassReady,
                        darkModeEnabled);
                return result;
            });

            if (popupGlassEnabled) {
                HookUtil.hookMethod(classLoader, SHORTCUT_MENU, "dismiss", chain -> {
                    Object menu = chain.getThisObject();
                    ShortcutPopupGlassCoordinator.beginDismissFade(menu);
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    releaseIfAlreadyDetached(menu);
                    return result;
                }, EDIT_STATE_CHANGE_REASON);
            }

            installed = true;
            MainHook.log(TAG + " hook installed popupGlass=" + popupGlassEnabled
                    + " darkMode=" + darkModeEnabled);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void installShortcutMenuLayerPrewarm(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) throws Throwable {
        Class<?> layer = Class.forName(SHORTCUT_MENU_LAYER, false, classLoader);
        for (Constructor<?> constructor : layer.getDeclaredConstructors()) {
            HookUtil.hook(constructor, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof View) {
                    schedulePrewarm((View) owner, glassConfig);
                }
                return result;
            });
        }
    }

    private static void schedulePrewarm(View ownerView, LiquidDockConfig.Glass glassConfig) {
        if (ownerView == null || glassConfig == null) return;
        if (ownerView.isAttachedToWindow()) {
            ShortcutPopupGlassCoordinator.prewarm(ownerView.getRootView(), glassConfig);
            return;
        }
        View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                v.removeOnAttachStateChangeListener(this);
                ShortcutPopupGlassCoordinator.prewarm(v.getRootView(), glassConfig);
            }

            @Override public void onViewDetachedFromWindow(View v) {}
        };
        ownerView.addOnAttachStateChangeListener(listener);
    }

    private static void bindShownPopup(Object menu, boolean popupGlassEnabled,
                                       boolean darkModeEnabled) {
        if (menu == null) return;
        try {
            Object decorObject = HookUtil.getField(menu, "mDecorView");
            Object popupObject = HookUtil.getField(menu, "mPopupView");
            if (!(decorObject instanceof View) || !(popupObject instanceof View)) return;
            Method getContentView = popupObject.getClass().getMethod("getContentView");
            Object contentObject = getContentView.invoke(popupObject);
            if (!(contentObject instanceof View)) return;
            View contentView = (View) contentObject;
            if (darkModeEnabled) {
                ShortcutMenuDarkModeController.attach(contentView);
            }
            if (!popupGlassEnabled || !GlassRuntimeState.isEnabled()) return;
            boolean bound = ShortcutPopupGlassCoordinator.bindPopup(
                    (View) decorObject, (View) popupObject, contentView);
            if (!bound) {
                MainHook.log(TAG + " pre-show source unavailable; stock material retained");
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " popup bind failed; stock material retained: " + error);
        }
    }

    private static void releaseIfAlreadyDetached(Object menu) {
        if (menu == null) return;
        try {
            Object decorObject = HookUtil.getField(menu, "mDecorView");
            Object popupObject = HookUtil.getField(menu, "mPopupView");
            if (decorObject instanceof View) {
                ShortcutPopupGlassCoordinator.releasePopupIfDetached(
                        (View) decorObject, popupObject instanceof View ? (View) popupObject : null);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " dismiss cleanup deferred: " + error);
        }
    }
}
