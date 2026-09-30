package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/**
 * Keeps Launcher 4.50 ShortcutMenu vertically aligned with the real ShortcutIcon body when
 * LiquidDock custom-grid geometry makes workspace cells non-square.
 *
 * <p>Vendor ShortcutIcon draws its icon at getPaddingTop(), derived from measuredHeight. However,
 * DeviceConfig.getIconImageViewPaddingTop(View) derives Y padding from ShortcutIcon.getCellWidth().
 * Those are equivalent only while the cell is square. LiquidDock's vertical-grid owner may change
 * cell height independently, so scope this correction to ShortcutMenuPosition.CalcPositionInfo()
 * and workspace ShortcutIcon instances only.</p>
 */
final class ShortcutMenuGridPositionHook {
    private static final String TAG = "[DC][ShortcutMenuPosition]";
    private static final String POSITION =
            "com.miui.home.launcher.shortcuts.ShortcutMenuPosition";
    private static final String DEVICE_CONFIG =
            "com.miui.home.launcher.DeviceConfig";
    private static final String SHORTCUT_ICON =
            "com.miui.home.launcher.ShortcutIcon";

    private static final ThreadLocal<Boolean> POSITION_TRANSACTION = new ThreadLocal<>();
    private static boolean installed;

    private ShortcutMenuGridPositionHook() {}

    static boolean install(ClassLoader classLoader, boolean customGridEnabled) {
        if (!customGridEnabled) return false;
        if (installed) return true;
        if (classLoader == null) return false;

        try {
            Class<?> positionClass = Class.forName(POSITION, false, classLoader);
            Class<?> deviceConfigClass = Class.forName(DEVICE_CONFIG, false, classLoader);
            Class<?> shortcutIconClass = Class.forName(SHORTCUT_ICON, false, classLoader);

            Method calcPosition = HookUtil.findMethodExact(
                    positionClass, "CalcPositionInfo", new Class<?>[0]);
            Method getPaddingTop = HookUtil.findMethodExact(
                    deviceConfigClass, "getIconImageViewPaddingTop",
                    new Class<?>[]{View.class});
            Method getWorkspaceScale = HookUtil.findMethodExact(
                    deviceConfigClass, "getWorkspaceScale", new Class<?>[0]);

            HookUtil.hook(calcPosition, chain -> {
                Boolean previous = POSITION_TRANSACTION.get();
                POSITION_TRANSACTION.set(Boolean.TRUE);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    if (previous == null) POSITION_TRANSACTION.remove();
                    else POSITION_TRANSACTION.set(previous);
                }
            });

            HookUtil.hook(getPaddingTop, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object vendorResult = chain.proceed(args);
                if (!Boolean.TRUE.equals(POSITION_TRANSACTION.get())
                        || !(vendorResult instanceof Integer)
                        || args.length != 1
                        || !(args[0] instanceof View)) {
                    return vendorResult;
                }

                View icon = (View) args[0];
                if (!shortcutIconClass.isInstance(icon)
                        || !LauncherGlassHierarchy.isWorkspace(icon)) {
                    return vendorResult;
                }

                float workspaceScale = 1f;
                try {
                    Object scale = getWorkspaceScale.invoke(null);
                    if (scale instanceof Number) {
                        workspaceScale = ((Number) scale).floatValue();
                    }
                } catch (Throwable ignored) {}

                int vendorPadding = (Integer) vendorResult;
                int corrected = ShortcutMenuGridPaddingPolicy.correctedTopPadding(
                        vendorPadding,
                        icon.getPaddingTop(),
                        workspaceScale,
                        true);
                if (corrected != vendorPadding) {
                    MainHook.log(TAG + " corrected workspace icon Y padding vendor="
                            + vendorPadding + " actual=" + corrected
                            + " view=" + icon.getMeasuredWidth() + "x" + icon.getMeasuredHeight()
                            + " paddingTop=" + icon.getPaddingTop()
                            + " scale=" + workspaceScale);
                }
                return corrected;
            });

            installed = true;
            MainHook.log(TAG + " custom-grid vertical padding correction installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " custom-grid vertical padding correction unavailable: " + error);
            return false;
        }
    }
}
