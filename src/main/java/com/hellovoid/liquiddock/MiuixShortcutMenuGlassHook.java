package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

import java.lang.reflect.Method;

/** HyperOS 4.50 ShortcutMenu glass and optional dark-mode content adaptation. */
final class MiuixShortcutMenuGlassHook {
    private static final String TAG = "[DC][ShortcutMenuGlass]";
    private static final String SHORTCUT_MENU = "com.miui.home.launcher.shortcuts.ShortcutMenu";
    private static final String SHORTCUT_MENU_LAYER = "com.miui.home.launcher.ShortcutMenuLayer";
    private static final String SHORTCUT_MENU_POSITION =
            "com.miui.home.launcher.shortcuts.ShortcutMenuPosition";
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
                installPositionDiagnostics(classLoader);
                installCoordinateAuthorityDiagnostics(classLoader);
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

    private static void installPositionDiagnostics(ClassLoader classLoader) throws Exception {
        Class<?> positionClass = Class.forName(SHORTCUT_MENU_POSITION, false, classLoader);
        Method calcPosition = positionClass.getDeclaredMethod("CalcPositionInfo");
        Method getPositionX = positionClass.getMethod("getPositionInfoX");
        Method getPositionY = positionClass.getMethod("getPositionInfoY");
        Method getVisualHeight = positionClass.getMethod("getVisualHeight");
        Method getGravity = positionClass.getMethod("getGravity");
        calcPosition.setAccessible(true);

        Log.e("LiquidDockBisect2", "INSTALLED ShortcutMenuPosition.CalcPositionInfo");
        HookUtil.hook(calcPosition, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            Object owner = chain.getThisObject();
            try {
                Object dragLocation = HookUtil.getField(
                        owner, "mDragViewLocationInShortcutMenuLayer");
                Object fingerLocation = HookUtil.getField(owner, "mFingerDragLocation");
                Object itemInfo = HookUtil.getField(owner, "mItemInfo");
                View buddy = null;
                try {
                    Object buddyObject = HookUtil.requireInvoke(itemInfo, "getBuddyIconView");
                    if (buddyObject instanceof View) buddy = (View) buddyObject;
                } catch (Throwable ignored) {}
                int probeX = ((Number) getPositionX.invoke(owner)).intValue();
                int probeY = ((Number) getPositionY.invoke(owner)).intValue();
                int probeW = HookUtil.getIntField(owner, "mMenuVisualWidth");
                int probeH = ((Number) getVisualHeight.invoke(owner)).intValue();
                int probeGravity = ((Number) getGravity.invoke(owner)).intValue();
                ShortcutMenuPositionProbe.record(
                        probeX, probeY, probeW, probeH, probeGravity);
                MainHook.log(TAG + " [YDIAG] position"
                        + " drag=" + describeArray(dragLocation)
                        + " finger=" + describeArray(fingerLocation)
                        + " result=" + getPositionX.invoke(owner)
                        + "," + getPositionY.invoke(owner)
                        + " visualHeight=" + getVisualHeight.invoke(owner)
                        + " gravity=" + getGravity.invoke(owner)
                        + " shadowPadding=" + safeIntField(owner, "mShadowPadding")
                        + " navBarHeight=" + safeIntField(owner, "mNavigationBarHeight")
                        + " dockWindowHeight=" + safeIntField(owner, "mDockWindowHeight")
                        + " item=" + (itemInfo != null ? itemInfo.getClass().getName() : "null"));
                logViewGeometry("position/buddy-icon", buddy);
                if (buddy != null) {
                    String iconSize = safeInvokeNoArg(buddy, "getIconSize");
                    String cellWidth = safeInvokeNoArg(buddy, "getCellWidth");
                    MainHook.log(TAG + " [YDIAG] buddy-metrics"
                            + " class=" + buddy.getClass().getName()
                            + " measured=" + buddy.getMeasuredWidth() + "x"
                            + buddy.getMeasuredHeight()
                            + " padding=" + buddy.getPaddingLeft() + ","
                            + buddy.getPaddingTop() + ","
                            + buddy.getPaddingRight() + ","
                            + buddy.getPaddingBottom()
                            + " cellWidth=" + cellWidth
                            + " iconSize=" + iconSize);
                }
                logAncestorChain(buddy, null, "buddy-chain");
            } catch (Throwable error) {
                MainHook.log(TAG + " [YDIAG] position read failed: " + error);
            }
            return result;
        });
    }

    private static void installCoordinateAuthorityDiagnostics(ClassLoader classLoader)
            throws Exception {
        Class<?> layerClass = Class.forName(SHORTCUT_MENU_LAYER, false, classLoader);
        Method resolve = HookUtil.findMethodExact(
                layerClass,
                "getDragViewLocationAndScaleInShortcutMenuLayer",
                new Class<?>[]{View.class});

        Class<?> applicationClass =
                Class.forName("com.miui.home.launcher.Application", false, classLoader);
        Method getLauncher = HookUtil.findMethodExact(
                applicationClass, "getLauncher", new Class<?>[0]);

        Class<?> deviceConfigClass =
                Class.forName("com.miui.home.launcher.DeviceConfig", false, classLoader);
        Method getWorkspaceScale = HookUtil.findMethodExact(
                deviceConfigClass, "getWorkspaceScale", new Class<?>[0]);
        Method isInHalfSoscSplitMode = HookUtil.findMethodExact(
                deviceConfigClass, "isInHalfSoscSplitMode", new Class<?>[0]);
        Method getCellWidth = HookUtil.findMethodExact(
                deviceConfigClass, "getCellWidth", new Class<?>[0]);
        Method getCellHeight = HookUtil.findMethodExact(
                deviceConfigClass, "getCellHeight", new Class<?>[0]);
        Method getCellCountX = HookUtil.findMethodExact(
                deviceConfigClass, "getCellCountX", new Class<?>[0]);
        Method getCellCountY = HookUtil.findMethodExact(
                deviceConfigClass, "getCellCountY", new Class<?>[0]);
        Method getHotSeatsHeight = HookUtil.findMethodExact(
                deviceConfigClass, "getHotSeatsHeight", new Class<?>[0]);
        Method getHotSeatsMarginBottom = HookUtil.findMethodExact(
                deviceConfigClass, "getHotSeatsMarginBottom", new Class<?>[0]);

        Class<?> soscControllerClass =
                Class.forName("com.miui.home.launcher.LauncherSoscController", false, classLoader);
        Method getSoscController = HookUtil.findMethodExact(
                soscControllerClass, "getInstance", new Class<?>[0]);

        Class<?> launcherModeControllerClass =
                Class.forName("com.miui.home.launcher.allapps.LauncherModeController",
                        false, classLoader);
        Method isLaptopMode = HookUtil.findMethodExact(
                launcherModeControllerClass, "isLaptopMode", new Class<?>[0]);

        HookUtil.hook(resolve, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            View source = args.length > 0 && args[0] instanceof View ? (View) args[0] : null;
            View ancestor = chain.getThisObject() instanceof View
                    ? (View) chain.getThisObject() : null;

            logLauncherState(
                    getLauncher,
                    getWorkspaceScale,
                    isInHalfSoscSplitMode,
                    getCellWidth,
                    getCellHeight,
                    getCellCountX,
                    getCellCountY,
                    getHotSeatsHeight,
                    getHotSeatsMarginBottom,
                    getSoscController,
                    isLaptopMode);
            logAncestorChain(source, ancestor, "coord-before");

            Object result = chain.proceed(args);

            MainHook.log(TAG + " [YDIAG] coord-result source="
                    + (source != null ? source.getClass().getName() : "null")
                    + " result=" + describeArray(result));
            logAncestorChain(source, ancestor, "coord-after");
            return result;
        });
    }

    private static void logLauncherState(
            Method getLauncher,
            Method getWorkspaceScale,
            Method isInHalfSoscSplitMode,
            Method getCellWidth,
            Method getCellHeight,
            Method getCellCountX,
            Method getCellCountY,
            Method getHotSeatsHeight,
            Method getHotSeatsMarginBottom,
            Method getSoscController,
            Method isLaptopMode) {
        try {
            Object launcher = getLauncher.invoke(null);
            String stateName = "null";
            int editingState = Integer.MIN_VALUE;
            String flags = "";
            if (launcher != null) {
                try {
                    Object stateManager = HookUtil.getField(launcher, "mStateManager");
                    Object state = HookUtil.requireInvoke(stateManager, "getState");
                    stateName = state != null ? state.getClass().getSimpleName() : "null";
                } catch (Throwable error) {
                    stateName = "ERR:" + error.getClass().getSimpleName();
                }
                try {
                    editingState = HookUtil.getIntField(launcher, "mEditingState");
                } catch (Throwable ignored) {}
                flags = " shortcut=" + invokeBoolean(launcher, "isInShortcutMenuState")
                        + " editing=" + invokeBoolean(launcher, "isInEditing")
                        + " normalEditing=" + invokeBoolean(launcher, "isInNormalEditing")
                        + " folder=" + invokeBoolean(launcher, "isFolderShowing");
            }

            Object soscController = getSoscController.invoke(null);
            Object soscEvent = soscController != null
                    ? HookUtil.requireInvoke(soscController, "getSoscEvent") : null;
            String sosc = describeSoscEvent(soscEvent);

            MainHook.log(TAG + " [YDIAG] launcher-state"
                    + " state=" + stateName
                    + " editingState=" + editingState
                    + flags
                    + " workspaceScale=" + getWorkspaceScale.invoke(null)
                    + " halfSosc=" + isInHalfSoscSplitMode.invoke(null)
                    + " laptopMode=" + isLaptopMode.invoke(null)
                    + " cell=" + getCellWidth.invoke(null) + "x" + getCellHeight.invoke(null)
                    + " count=" + getCellCountX.invoke(null) + "x" + getCellCountY.invoke(null)
                    + " hotseatHeight=" + getHotSeatsHeight.invoke(null)
                    + " hotseatMarginBottom=" + getHotSeatsMarginBottom.invoke(null)
                    + " sosc=" + sosc);
        } catch (Throwable error) {
            MainHook.log(TAG + " [YDIAG] launcher-state read failed: " + error);
        }
    }

    private static String invokeBoolean(Object target, String methodName) {
        if (target == null) return "null";
        try {
            Object value = HookUtil.requireInvoke(target, methodName);
            return String.valueOf(value);
        } catch (Throwable error) {
            return "ERR";
        }
    }

    private static String describeSoscEvent(Object event) {
        if (event == null) return "null";
        StringBuilder out = new StringBuilder(event.getClass().getSimpleName());
        try { out.append("{state=").append(HookUtil.getIntField(event, "state")); }
        catch (Throwable ignored) { out.append("{state=?"); }
        try { out.append(",bounds=").append(HookUtil.getField(event, "bounds")); }
        catch (Throwable ignored) {}
        try { out.append(",rootBounds=").append(HookUtil.getField(event, "rootBounds")); }
        catch (Throwable ignored) {}
        try { out.append(",halfByState=").append(HookUtil.requireInvoke(event, "isHalfSoscSplitByState")); }
        catch (Throwable ignored) {}
        try { out.append(",topBottom=").append(HookUtil.requireInvoke(event, "isTopAndBottomSplit")); }
        catch (Throwable ignored) {}
        out.append('}');
        return out.toString();
    }

    private static void logAncestorChain(View source, View stop, String stage) {
        if (source == null) return;
        try {
            View current = source;
            int depth = 0;
            while (current != null && depth < 16) {
                float[] matrix = new float[9];
                current.getMatrix().getValues(matrix);
                int[] screen = new int[2];
                current.getLocationOnScreen(screen);
                String idName = "no-id";
                int id = current.getId();
                if (id != View.NO_ID) {
                    try { idName = current.getResources().getResourceEntryName(id); }
                    catch (Throwable ignored) { idName = String.valueOf(id); }
                }
                MainHook.log(TAG + " [YDIAG] " + stage
                        + " depth=" + depth
                        + " class=" + current.getClass().getName()
                        + " id=" + idName
                        + " frame=" + current.getLeft() + "," + current.getTop()
                        + "-" + current.getRight() + "," + current.getBottom()
                        + " measured=" + current.getMeasuredWidth() + "x"
                        + current.getMeasuredHeight()
                        + " screen=" + screen[0] + "," + screen[1]
                        + " scroll=" + current.getScrollX() + "," + current.getScrollY()
                        + " translation=" + current.getTranslationX() + ","
                        + current.getTranslationY()
                        + " scale=" + current.getScaleX() + "," + current.getScaleY()
                        + " pivot=" + current.getPivotX() + "," + current.getPivotY()
                        + " matrix=[" + matrix[0] + "," + matrix[1] + "," + matrix[2]
                        + ";" + matrix[3] + "," + matrix[4] + "," + matrix[5] + "]");
                if (current == stop) break;
                ViewParent parent = current.getParent();
                current = parent instanceof View ? (View) parent : null;
                depth++;
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " [YDIAG] " + stage + " chain failed: " + error);
        }
    }

    private static String safeInvokeNoArg(Object target, String methodName) {
        if (target == null) return "null";
        try {
            return String.valueOf(HookUtil.requireInvoke(target, methodName));
        } catch (Throwable error) {
            return "ERR";
        }
    }

    private static String safeIntField(Object target, String fieldName) {
        try {
            return String.valueOf(HookUtil.getIntField(target, fieldName));
        } catch (Throwable error) {
            return "ERR";
        }
    }

    private static String describeArray(Object value) {
        if (value instanceof float[]) {
            float[] array = (float[]) value;
            if (array.length >= 2) return array[0] + "," + array[1];
        }
        if (value instanceof int[]) {
            int[] array = (int[]) value;
            if (array.length >= 2) return array[0] + "," + array[1];
        }
        return String.valueOf(value);
    }

    private static void logViewGeometry(String stage, View view) {
        if (view == null) return;
        try {
            int[] screen = new int[2];
            view.getLocationOnScreen(screen);
            Rect global = new Rect();
            boolean globallyVisible = view.getGlobalVisibleRect(global);
            MainHook.log(TAG + " [YDIAG] " + stage
                    + " class=" + view.getClass().getName()
                    + " attached=" + view.isAttachedToWindow()
                    + " screen=" + screen[0] + "," + screen[1]
                    + " xy=" + view.getX() + "," + view.getY()
                    + " translation=" + view.getTranslationX() + "," + view.getTranslationY()
                    + " scale=" + view.getScaleX() + "," + view.getScaleY()
                    + " pivot=" + view.getPivotX() + "," + view.getPivotY()
                    + " size=" + view.getWidth() + "x" + view.getHeight()
                    + " globalVisible=" + globallyVisible + ":" + global.toShortString());
        } catch (Throwable error) {
            MainHook.log(TAG + " [YDIAG] " + stage + " read failed: " + error);
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

        hookDockTouchRoute(dispatchTouchEventFromHome, glassConfig);
        hookDockTouchRoute(dispatchTouchEvent, glassConfig);
    }

    private static void hookDockTouchRoute(
            Method method,
            LiquidDockConfig.Glass glassConfig) {
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
            View decorView = (View) decorObject;
            View popupView = (View) popupObject;
            Object anchorObject = HookUtil.getField(menu, "mAnchor");
            View anchorView = anchorObject instanceof View ? (View) anchorObject : null;
            logViewGeometry("show/anchor-immediate", anchorView);
            logViewGeometry("show/decor-immediate", decorView);
            logViewGeometry("show/popup-immediate", popupView);
            logViewGeometry("show/content-immediate", contentView);
            contentView.postOnAnimation(() -> {
                logViewGeometry("show/anchor-first-frame", anchorView);
                logViewGeometry("show/decor-first-frame", decorView);
                logViewGeometry("show/popup-first-frame", popupView);
                logViewGeometry("show/content-first-frame", contentView);
            });
            contentView.postDelayed(() -> {
                logViewGeometry("show/anchor-100ms", anchorView);
                logViewGeometry("show/popup-100ms", popupView);
                logViewGeometry("show/content-100ms", contentView);
                logAncestorChain(contentView, null, "popup-chain-100ms");
            }, 100L);
            contentView.postDelayed(() -> {
                logViewGeometry("show/anchor-250ms", anchorView);
                logViewGeometry("show/popup-250ms", popupView);
                logViewGeometry("show/content-250ms", contentView);
                logAncestorChain(contentView, null, "popup-chain-250ms");
            }, 250L);
            contentView.postDelayed(() -> {
                logViewGeometry("show/anchor-500ms", anchorView);
                logViewGeometry("show/popup-500ms", popupView);
                logViewGeometry("show/content-500ms", contentView);
                logAncestorChain(contentView, null, "popup-chain-500ms");
            }, 500L);
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
