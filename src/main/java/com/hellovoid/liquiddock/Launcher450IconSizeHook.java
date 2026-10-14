package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.reflect.Method;

/**
 * HyperOS Launcher 4.50 icon-size hook.
 *
 * <p>Launcher 4.50 derives Workspace/Dock/open-Folder/workstation app-page ShortcutIcon and
 * FolderIcon1x1 geometry from GridConfig during onMeasure(). We scope the override to those
 * vendor measurement transactions with a ThreadLocal, so shared GridConfig instances are never
 * mutated and ordinary All Apps/search keep the vendor size.</p>
 */
final class Launcher450IconSizeHook {
    private static final String TAG = "[DC][IconSize450]";
    private static final String SHORTCUT_ICON = "com.miui.home.launcher.ShortcutIcon";
    private static final String SMALL_FOLDER = "com.miui.home.launcher.folder.FolderIcon1x1";
    private static final String FOLDER_PREVIEW_CONTAINER_1X1 =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer1X1";
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";
    private static final String BASE_PROGRESS_SHORTCUT_ICON =
            "com.miui.home.launcher.BaseProgressShortcutIcon";
    private static final String FOLDER_GRID_VIEW = "com.miui.home.launcher.FolderGridView";
    private static final String WORKSTATION_ALL_APPS_WORKSPACE =
            "com.miui.home.launcher.laptop.launchpad.AllAppsWorkspace";
    private static final String GRID_CONFIG = "com.miui.home.launcher.grid.GridConfig";

    private enum MeasureDomain {
        WORKSPACE, DOCK, FOLDER, FOLDER_CONTENT, WORKSTATION_APPS
    }

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
            Class<?> folderPreviewContainer = Class.forName(
                    FOLDER_PREVIEW_CONTAINER_1X1, false, classLoader);
            Class<?> baseProgressShortcutIcon = Class.forName(
                    BASE_PROGRESS_SHORTCUT_ICON, false, classLoader);
            Class<?> gridConfig = Class.forName(GRID_CONFIG, false, classLoader);

            Method shortcutMeasure = HookUtil.findMethodExact(
                    shortcutIcon, "onMeasure", new Class<?>[]{int.class, int.class});
            Method folderMeasure = HookUtil.findMethodExact(
                    smallFolder, "onMeasure", new Class<?>[]{int.class, int.class});
            Method getIconSize = HookUtil.findMethodExact(
                    gridConfig, "getIconSize", new Class<?>[0]);

            HookUtil.hook(shortcutMeasure, chain -> {
                Object owner = chain.getThisObject();
                MeasureDomain domain = owner instanceof View ? shortcutDomain((View) owner) : null;
                MeasureDomain previous = ACTIVE_DOMAIN.get();
                if (domain != null) ACTIVE_DOMAIN.set(domain);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    if (previous == null) ACTIVE_DOMAIN.remove();
                    else ACTIVE_DOMAIN.set(previous);
                }
            });
            HookUtil.hook(folderMeasure, chain -> {
                Object owner = chain.getThisObject();
                View folder = owner instanceof View ? (View) owner : null;
                MeasureDomain domain = smallFolderMeasureDomain(folder);
                if (domain != null && percent != Launcher450IconSizePolicy.DEFAULT_PERCENT) {
                    // The OEM layout's inner FrameLayout, native blur ImageView and
                    // cover are wrap_content. Scaling only ItemIcon.mIconContainer
                    // lets an async folder background rebind shrink its native blur
                    // stencil back to the drawable's unscaled intrinsic size.
                    bindSmallFolderMaterialToIconSize(folder);
                }
                return withMeasureDomain(domain,
                        () -> chain.proceed(chain.getArgs().toArray(new Object[0])));
            });
            installFolderScaleTransactions(shortcutIcon, baseProgressShortcutIcon);
            installFolderPreviewMeasureTransaction(folderPreviewContainer);

            HookUtil.hook(getIconSize, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                MeasureDomain domain = ACTIVE_DOMAIN.get();
                if (!enabled || domain == null || !(result instanceof Integer)) return result;

                // Scale only the icon body. Dock slot width remains vendor-owned so the
                // ShortcutIcon stays centered in the same Flexbox item.
                return Launcher450IconSizePolicy.scaledPx((Integer) result, true, percent);
            });

            installed = true;
            MainHook.log(TAG + " installed enabled=" + enabled + " percent=" + percent);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable on target Launcher: " + error);
            return false;
        }
    }

    private interface DomainAction { Object run() throws Throwable; }

    private static Object withMeasureDomain(MeasureDomain domain, DomainAction action) throws Throwable {
        MeasureDomain previous = ACTIVE_DOMAIN.get();
        // The temporary FolderCling animation icon must not inherit the real
        // Workspace icon's size context through nested vendor callbacks.
        if (domain == null) ACTIVE_DOMAIN.remove();
        else ACTIVE_DOMAIN.set(domain);
        try { return action.run(); }
        finally {
            if (previous == null) ACTIVE_DOMAIN.remove();
            else ACTIVE_DOMAIN.set(previous);
        }
    }

    /** Match Launcher's own FolderIcon1x1.onMeasure() ownership: direct CellLayout parent. */
    private static MeasureDomain smallFolderMeasureDomain(View view) {
        if (!enabled || view == null) return null;
        View cursor = view;
        while (cursor != null) {
            if (SMALL_FOLDER.equals(cursor.getClass().getName())) {
                ViewParent parent = cursor.getParent();
                return parent instanceof View && CELL_LAYOUT.equals(parent.getClass().getName())
                        ? MeasureDomain.FOLDER : null;
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    /**
     * OEM folder_icon_1x1.xml uses a fixed-size outer icon_container, but its inner
     * FrameLayout, icon_icon (native blur target) and cover are wrap_content.
     * On a new drawable load they therefore shrink to the stock intrinsic bounds,
     * regardless of the scaled parent. Convert these three dependent layout nodes
     * to MATCH_PARENT; ItemIcon remains the sole authority for parent icon size.
     *
     * Only genuine CellLayout-owned 1x1 folders are adjusted; FolderCling's cloned
     * animation icon (setUseCustomWidth=true) retains all its stock layout rules.
     */
    private static void bindSmallFolderMaterialToIconSize(View folder) {
        if (folder == null) return;
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(folder, "getIconContainer");
        if (!result.succeeded() || !(result.value() instanceof ViewGroup)) return;
        ViewGroup outer = (ViewGroup) result.value();
        if (outer.getChildCount() < 1 || !(outer.getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup inner = (ViewGroup) outer.getChildAt(0);
        if (inner.getChildCount() < 3 || !(inner.getChildAt(0) instanceof ImageView)
                || !(inner.getChildAt(2) instanceof ImageView)
                || !FOLDER_PREVIEW_CONTAINER_1X1.equals(inner.getChildAt(1).getClass().getName())) {
            return;
        }
        useParentIconSize(inner);
        useParentIconSize(inner.getChildAt(0));
        useParentIconSize(inner.getChildAt(2));
    }

    private static void useParentIconSize(View child) {
        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (params == null || (params.width == ViewGroup.LayoutParams.MATCH_PARENT
                && params.height == ViewGroup.LayoutParams.MATCH_PARENT)) return;
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        child.setLayoutParams(params);
    }

    private static void installFolderPreviewMeasureTransaction(
            Class<?> folderPreviewContainer) throws NoSuchMethodException {
        Method onMeasure = HookUtil.findMethodExact(
                folderPreviewContainer, "onMeasure", new Class<?>[]{int.class, int.class});
        HookUtil.hook(onMeasure, chain -> {
            Object owner = chain.getThisObject();
            MeasureDomain domain = owner instanceof View
                    ? smallFolderMeasureDomain((View) owner) : null;
            return withMeasureDomain(domain,
                    () -> chain.proceed(chain.getArgs().toArray(new Object[0])));
        });
    }

    private static void installFolderScaleTransactions(
            Class<?> shortcutIcon, Class<?> baseProgressShortcutIcon) throws NoSuchMethodException {
        installFolderScaleTransaction(shortcutIcon);
        installFolderScaleTransaction(baseProgressShortcutIcon);
    }

    private static void installFolderScaleTransaction(Class<?> ownerType) throws NoSuchMethodException {
        Method scaleDown = HookUtil.findMethodExact(
                ownerType, "scaleDownToFolder", new Class<?>[]{boolean.class});
        HookUtil.hook(scaleDown, chain -> {
            Object owner = chain.getThisObject();
            MeasureDomain domain = owner instanceof View ? shortcutDomain((View) owner) : null;
            if (domain == null) domain = MeasureDomain.WORKSPACE;
            final MeasureDomain active = domain;
            return withMeasureDomain(active,
                    () -> chain.proceed(chain.getArgs().toArray(new Object[0])));
        });
    }

    private static MeasureDomain shortcutDomain(View view) {
        if (!enabled || view == null) return null;
        if (hasAncestor(view, FOLDER_GRID_VIEW)) return MeasureDomain.FOLDER_CONTENT;
        if (hasAncestor(view, WORKSTATION_ALL_APPS_WORKSPACE)) {
            return MeasureDomain.WORKSTATION_APPS;
        }
        LauncherGlassHierarchy.Domain domain = LauncherGlassHierarchy.classify(view);
        if (domain == LauncherGlassHierarchy.Domain.WORKSPACE) return MeasureDomain.WORKSPACE;
        if (domain == LauncherGlassHierarchy.Domain.DOCK) return MeasureDomain.DOCK;
        return null;
    }

    private static boolean hasAncestor(View view, String className) {
        for (ViewParent parent = view.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof View && className.equals(parent.getClass().getName())) {
                return true;
            }
        }
        return false;
    }
}
