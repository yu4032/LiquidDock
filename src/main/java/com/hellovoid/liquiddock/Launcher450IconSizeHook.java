package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/**
 * HyperOS Launcher 4.50 icon-size hook.
 *
 * <p>Launcher 4.50 derives Workspace/Dock ShortcutIcon and FolderIcon1x1 geometry from
 * GridConfig during onMeasure(). We scope the override to those vendor measurement transactions
 * with a ThreadLocal, so shared GridConfig instances are never mutated and All Apps/search keep
 * the vendor size.</p>
 */
final class Launcher450IconSizeHook {
    private static final String TAG = "[DC][IconSize450]";
    private static final String SHORTCUT_ICON = "com.miui.home.launcher.ShortcutIcon";
    private static final String SMALL_FOLDER = "com.miui.home.launcher.folder.FolderIcon1x1";
    private static final String GRID_CONFIG = "com.miui.home.launcher.grid.GridConfig";

    private enum MeasureDomain { WORKSPACE, DOCK, FOLDER }

    private static final ThreadLocal<MeasureDomain> ACTIVE_DOMAIN = new ThreadLocal<>();
    private static volatile boolean enabled;
    private static volatile int percent = Launcher450IconSizePolicy.DEFAULT_PERCENT;
    private static boolean installed;

    private Launcher450IconSizeHook() {}

    static boolean install(ClassLoader classLoader, boolean iconSizeEnabled, int iconSizePercent) {
        enabled = iconSizeEnabled;
        percent = Math.max(Launcher450IconSizePolicy.MIN_PERCENT,
                Math.min(Launcher450IconSizePolicy.MAX_PERCENT, iconSizePercent));
        if (installed) return true;
        if (classLoader == null) return false;

        try {
            Class<?> shortcutIcon = Class.forName(SHORTCUT_ICON, false, classLoader);
            Class<?> smallFolder = Class.forName(SMALL_FOLDER, false, classLoader);
            Class<?> gridConfig = Class.forName(GRID_CONFIG, false, classLoader);

            Method shortcutMeasure = HookUtil.findMethodExact(
                    shortcutIcon, "onMeasure", new Class<?>[]{int.class, int.class});
            Method folderMeasure = HookUtil.findMethodExact(
                    smallFolder, "onMeasure", new Class<?>[]{int.class, int.class});
            Method getIconSize = HookUtil.findMethodExact(
                    gridConfig, "getIconSize", new Class<?>[0]);
            Method getDockIconWidth = HookUtil.findMethodExact(
                    gridConfig, "getDockIconWidth", new Class<?>[0]);

            HookUtil.hook(shortcutMeasure, chain -> {
                Object owner = chain.getThisObject();
                MeasureDomain domain = owner instanceof View ? shortcutDomain((View) owner) : null;
                return proceedInDomain(chain, domain);
            });
            HookUtil.hook(folderMeasure, chain -> {
                Object owner = chain.getThisObject();
                MeasureDomain domain = owner instanceof View
                        && LauncherGlassHierarchy.isWorkspace((View) owner)
                        ? MeasureDomain.FOLDER : null;
                return proceedInDomain(chain, domain);
            });
            HookUtil.hook(getIconSize, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                MeasureDomain domain = ACTIVE_DOMAIN.get();
                if (!enabled || domain == null || !(result instanceof Integer)) return result;
                return Launcher450IconSizePolicy.scaledPx((Integer) result, true, percent);
            });
            HookUtil.hook(getDockIconWidth, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (!enabled || ACTIVE_DOMAIN.get() != MeasureDomain.DOCK) return result;
                Object grid = chain.getThisObject();
                if (grid == null) return result;
                try {
                    int baseIcon = HookUtil.getIntField(grid, "iconSize");
                    int dockBarHeight = HookUtil.getIntField(grid, "dockBarHeight");
                    int scaledIcon = Launcher450IconSizePolicy.scaledPx(baseIcon, true, percent);
                    return scaledIcon + ((dockBarHeight - scaledIcon) / 2);
                } catch (Throwable error) {
                    MainHook.log(TAG + " getDockIconWidth fallback: " + error);
                    return result;
                }
            });

            installed = true;
            MainHook.log(TAG + " installed enabled=" + enabled + " percent=" + percent);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable on target Launcher: " + error);
            return false;
        }
    }

    private static MeasureDomain shortcutDomain(View view) {
        if (!enabled || view == null) return null;
        LauncherGlassHierarchy.Domain domain = LauncherGlassHierarchy.classify(view);
        if (domain == LauncherGlassHierarchy.Domain.WORKSPACE) return MeasureDomain.WORKSPACE;
        if (domain == LauncherGlassHierarchy.Domain.DOCK) return MeasureDomain.DOCK;
        return null;
    }

    private static Object proceedInDomain(
            io.github.libxposed.api.XposedInterface.BeforeHookCallback chain,
            MeasureDomain domain) throws Throwable {
        // This overload is intentionally unused; libxposed intercept callbacks are not BeforeHookCallback.
        return null;
    }

    private static Object proceedInDomain(
            io.github.libxposed.api.XposedInterface.Hooker.Chain chain,
            MeasureDomain domain) throws Throwable {
        MeasureDomain previous = ACTIVE_DOMAIN.get();
        if (domain != null) ACTIVE_DOMAIN.set(domain);
        try {
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        } finally {
            if (previous == null) ACTIVE_DOMAIN.remove();
            else ACTIVE_DOMAIN.set(previous);
        }
    }
}