package com.hellovoid.liquiddock;

import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Method;

/** HyperOS 4.50 ShortcutMenu glass and optional dark-mode content adaptation. */
final class MiuixShortcutMenuGlassHook {
    private static final String TAG = "[DC][ShortcutMenuGlass]";
    private static final String SHORTCUT_MENU = "com.miui.home.launcher.shortcuts.ShortcutMenu";
    private static final String SHORTCUT_MENU_LAYER = "com.miui.home.launcher.ShortcutMenuLayer";
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";
    private static final String DOCK_CONTAINER_VIEW =
            "com.miui.home.launcher.dock.DockContainerView";
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
                installEarlyWorkspaceCaptureHook(classLoader, glassConfig);
                installEarlyDockCaptureHook(classLoader, glassConfig);
                HookUtil.hookMethod(classLoader, SHORTCUT_MENU_LAYER, "setRequestingItemInfo", chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object itemInfo = args.length > 0 ? args[0] : null;
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) {
                        View ownerView = (View) owner;
                        View launcherRoot = ownerView.getRootView();
                        if (itemInfo != null) {
                            ShortcutPopupGlassCoordinator.prepareIfNeeded(
                                    ownerView, launcherRoot, glassConfig);
                        }
                        Object result = chain.proceed(args);
                        if (itemInfo == null) {
                            launcherRoot.postOnAnimation(
                                    () -> ShortcutPopupGlassCoordinator.cancelPending(
                                            ownerView, launcherRoot));
                        }
                        return result;
                    }
                    return chain.proceed(args);
                }, ITEM_INFO);
            }

            HookUtil.hookMethod(classLoader, SHORTCUT_MENU, "show", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                bindShownPopup(chain.getThisObject(), popupGlassEnabled, darkModeEnabled);
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

    private static void installEarlyWorkspaceCaptureHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) throws Exception {
        Class<?> cellLayoutClass = Class.forName(CELL_LAYOUT, false, classLoader);
        Method dispatchTouchEvent =
                cellLayoutClass.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        Method lastDownOnOccupiedCell =
                cellLayoutClass.getDeclaredMethod("lastDownOnOccupiedCell");
        dispatchTouchEvent.setAccessible(true);
        lastDownOnOccupiedCell.setAccessible(true);

        HookUtil.hook(dispatchTouchEvent, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                    ? (MotionEvent) args[0] : null;
            Object owner = chain.getThisObject();

            Object result = chain.proceed(args);

            if (owner instanceof View && event != null) {
                View launcherRoot = ((View) owner).getRootView();
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    Object occupied = lastDownOnOccupiedCell.invoke(owner);
                    if (occupied instanceof Boolean && ((Boolean) occupied).booleanValue()) {
                        ShortcutPopupGlassCoordinator.prepareEarly(launcherRoot, glassConfig);
                    }
                } else if (action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL) {
                    ShortcutPopupGlassCoordinator.cancelEarlyIfUnused(launcherRoot);
                }
            }
            return result;
        });
    }

    private static void installEarlyDockCaptureHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) throws Exception {
        Class<?> dockContainerClass = Class.forName(DOCK_CONTAINER_VIEW, false, classLoader);
        Method dispatchTouchEventFromHome =
                dockContainerClass.getDeclaredMethod(
                        "dispatchTouchEventFromHome", MotionEvent.class);
        Method dispatchTouchEvent =
                dockContainerClass.getDeclaredMethod(
                        "dispatchTouchEvent", MotionEvent.class);
        dispatchTouchEventFromHome.setAccessible(true);
        dispatchTouchEvent.setAccessible(true);

        hookDockTouchRoute(dispatchTouchEventFromHome, glassConfig, "home-forwarded");
        hookDockTouchRoute(dispatchTouchEvent, glassConfig, "direct-dock");
    }

    private static void hookDockTouchRoute(
            Method method,
            LiquidDockConfig.Glass glassConfig,
            String route) {
        HookUtil.hook(method, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                    ? (MotionEvent) args[0] : null;
            Object owner = chain.getThisObject();

            Object result = chain.proceed(args);

            if (owner instanceof View && event != null) {
                View dockMenuOwner = (View) owner;
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    ShortcutPopupGlassCoordinator.prepareDockEarly(
                            dockMenuOwner, glassConfig);
                    MainHook.log(TAG + " dock early capture armed route=" + route);
                } else if (action == MotionEvent.ACTION_UP) {
                    ShortcutPopupGlassCoordinator.cancelDockEarlyIfUnused(dockMenuOwner);
                }
            }
            return result;
        });
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
