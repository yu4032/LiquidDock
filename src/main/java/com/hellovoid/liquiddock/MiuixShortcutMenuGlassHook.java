package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/** HyperOS 4.50 ShortcutMenu glass backed by a pre-show workspace capture window. */
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
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        try {
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

            HookUtil.hookMethod(classLoader, SHORTCUT_MENU, "show", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                bindShownPopup(chain.getThisObject());
                return result;
            });

            HookUtil.hookMethod(classLoader, SHORTCUT_MENU, "dismiss", chain -> {
                Object menu = chain.getThisObject();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                releaseIfAlreadyDetached(menu);
                return result;
            }, EDIT_STATE_CHANGE_REASON);

            installed = true;
            MainHook.log(TAG + " pre-show workspace capture hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void bindShownPopup(Object menu) {
        if (menu == null || !GlassRuntimeState.isEnabled()) return;
        try {
            Object decorObject = HookUtil.getField(menu, "mDecorView");
            Object popupObject = HookUtil.getField(menu, "mPopupView");
            if (!(decorObject instanceof View) || !(popupObject instanceof View)) return;
            Method getContentView = popupObject.getClass().getMethod("getContentView");
            Object contentObject = getContentView.invoke(popupObject);
            if (!(contentObject instanceof View)) return;
            boolean bound = ShortcutPopupGlassCoordinator.bindPopup(
                    (View) decorObject, (View) popupObject, (View) contentObject);
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
