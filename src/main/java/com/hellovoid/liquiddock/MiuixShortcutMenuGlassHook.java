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
    private static final String HOTSEATS_LIST_CONTENT =
            "com.miui.home.launcher.hotseats.HotSeatsListContent";
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String CELL_INFO = "com.miui.home.launcher.CellLayout$CellInfo";
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
                installPreDragCaptureHooks(classLoader, glassConfig);

                // Query state is no longer allowed to start capture. It is already downstream of
                // Launcher drag/edit-state mutation. Keep this hook only as the semantic cancel
                // boundary for an abandoned async ShortcutMenu query.
                HookUtil.hookMethod(classLoader, SHORTCUT_MENU_LAYER, "setRequestingItemInfo", chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object itemInfo = args.length > 0 ? args[0] : null;
                    Object owner = chain.getThisObject();
                    Object result = chain.proceed(args);
                    if (itemInfo == null && owner instanceof View) {
                        View launcherRoot = ((View) owner).getRootView();
                        launcherRoot.postOnAnimation(
                                () -> ShortcutPopupGlassCoordinator.cancelPending(launcherRoot));
                    }
                    return result;
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
                    // Preserve MIUIX PopupAnimHelper as the sole dismiss presentation authority.
                    // The local glass child inherits mContentView animation automatically.
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

    private static void installPreDragCaptureHooks(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) throws Exception {
        Class<?> cellLayoutClass = Class.forName(CELL_LAYOUT, false, classLoader);
        Method cellDispatch =
                cellLayoutClass.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        Method lastDownOnOccupiedCell =
                cellLayoutClass.getDeclaredMethod("lastDownOnOccupiedCell");
        cellDispatch.setAccessible(true);
        lastDownOnOccupiedCell.setAccessible(true);
        HookUtil.hook(cellDispatch, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                    ? (MotionEvent) args[0] : null;
            Object owner = chain.getThisObject();

            // Let CellLayout perform its authoritative hit-test first. HyperOS writes
            // mCellInfo.cell and mLastDownOnOccupiedCell before forwarding DOWN to OnLongClickAgent.
            Object result = chain.proceed(args);

            if (owner instanceof View && event != null) {
                View launcherRoot = ((View) owner).getRootView();
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    Object occupied = lastDownOnOccupiedCell.invoke(owner);
                    if (occupied instanceof Boolean && ((Boolean) occupied).booleanValue()) {
                        ShortcutPopupGlassCoordinator.armTouch(launcherRoot, glassConfig);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    ShortcutPopupGlassCoordinator.cancelTouchIfUnlatched(
                            launcherRoot, action == MotionEvent.ACTION_UP
                                    ? "touch-up-before-drag" : "touch-cancel-before-drag");
                }
            }
            return result;
        });

        // HyperOS Launcher.onLongClick resolves the real occupied cell and then enters
        // dragSingleItem(); the first statement inside dragSingleItem() is Workspace.startDrag().
        // Latch immediately before that call, while the prepared source still represents the
        // untouched Workspace. This is earlier and semantically stronger than setRequestingItemInfo.
        Class<?> launcherClass = Class.forName(LAUNCHER, false, classLoader);
        Class<?> cellInfoClass = Class.forName(CELL_INFO, false, classLoader);
        Method dragSingleItem =
                launcherClass.getDeclaredMethod("dragSingleItem", cellInfoClass, View.class);
        dragSingleItem.setAccessible(true);
        HookUtil.hook(dragSingleItem, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            View draggedView = args.length > 1 && args[1] instanceof View ? (View) args[1] : null;
            if (draggedView != null) {
                ShortcutPopupGlassCoordinator.latchBeforeDrag(draggedView.getRootView());
            }
            return chain.proceed(args);
        });

        installDockCaptureHooks(classLoader, glassConfig);
        MainHook.log(TAG + " occupied-cell + dock pre-drag capture hooks installed");
    }

    private static void installDockCaptureHooks(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) throws Exception {
        Class<?> dockContainerClass = Class.forName(DOCK_CONTAINER_VIEW, false, classLoader);
        Method dispatchFromHome =
                dockContainerClass.getDeclaredMethod("dispatchTouchEventFromHome", MotionEvent.class);
        dispatchFromHome.setAccessible(true);
        HookUtil.hook(dispatchFromHome, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            MotionEvent event = args.length > 0 && args[0] instanceof MotionEvent
                    ? (MotionEvent) args[0] : null;
            Object owner = chain.getThisObject();

            Object result = chain.proceed(args);

            if (owner instanceof View && event != null) {
                View dockRoot = ((View) owner).getRootView();
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    // DockControllerImpl only routes this method after isTouchInHotSeatArea()
                    // succeeds, so this is the Dock equivalent of CellLayout's occupied-cell DOWN.
                    ShortcutPopupGlassCoordinator.armTouch(dockRoot, glassConfig);
                    MainHook.log(TAG + " dock touch prewarm armed");
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    ShortcutPopupGlassCoordinator.cancelTouchIfUnlatched(
                            dockRoot, action == MotionEvent.ACTION_UP
                                    ? "dock-touch-up-before-long-press"
                                    : "dock-touch-cancel-before-long-press");
                }
            }
            return result;
        });

        Class<?> hotSeatsListContentClass =
                Class.forName(HOTSEATS_LIST_CONTENT, false, classLoader);
        Method onLongClick = hotSeatsListContentClass.getDeclaredMethod("onLongClick", View.class);
        onLongClick.setAccessible(true);
        HookUtil.hook(onLongClick, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            View pressedView = args.length > 0 && args[0] instanceof View ? (View) args[0] : null;
            View dockRoot = pressedView != null ? pressedView.getRootView() : null;

            boolean latched = dockRoot != null
                    && ShortcutPopupGlassCoordinator.latchBeforeDrag(dockRoot);
            if (dockRoot != null) {
                MainHook.log(TAG + " dock pre-show backdrop latch=" + latched);
            }

            Object result = chain.proceed(args);

            if (!(result instanceof Boolean) || !((Boolean) result).booleanValue()) {
                if (dockRoot != null) {
                    ShortcutPopupGlassCoordinator.cancelAttemptIfPopupNotBound(
                            dockRoot, "dock-long-click-rejected");
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
