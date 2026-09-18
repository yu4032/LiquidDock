package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Ports the HyperOS 4 2x1 folder shape onto Launcher 4.50's semantic folder-size path.
 *
 * <p>The vendor item type remains 21 so creation, persistence, drag/drop and folder opening stay
 * native. A 2x1 folder is distinguished by its persisted span (2,1); existing type-21 folders
 * whose database span is still 2x2 are intentionally left untouched.</p>
 */
final class Folder2x1Hook {
    private static final String TAG = "[DC][Folder2x1]";
    private static final String CONVERT_SIZE =
            "com.miui.home.launcher.convertsize.FolderIconConvertSizeController";
    private static final String BASE_PREVIEW =
            "com.miui.home.launcher.folder.BaseFolderIconPreviewContainer2X2";
    private static final String FOLDER_ICON_2X2_4 =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final String FOLDER_INFO = "com.miui.home.launcher.FolderInfo";
    private static final String GRID_UTILS = "com.miui.home.launcher.grid.GridUtils";
    private static final int VENDOR_TYPE_2X2_4 = 21;

    private static final Map<View, Folder2x1LayoutPolicy.Layout> LAYOUTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private static volatile boolean enabled;
    private static volatile boolean iconSizeEnabled;
    private static volatile int iconSizePercent = Launcher450IconSizePolicy.DEFAULT_PERCENT;
    private static volatile Class<?> gridUtilsClass;
    private static volatile Method setMeasuredDimension;
    private static boolean installed;

    private Folder2x1Hook() {}

    static boolean install(ClassLoader classLoader, boolean featureEnabled,
                           boolean scaleWorkspaceIcons, int workspaceIconPercent) {
        enabled = featureEnabled;
        iconSizeEnabled = scaleWorkspaceIcons;
        iconSizePercent = workspaceIconPercent;
        if (installed) return true;
        if (classLoader == null) return false;

        try {
            Class<?> convert = Class.forName(CONVERT_SIZE, false, classLoader);
            Class<?> preview = Class.forName(BASE_PREVIEW, false, classLoader);
            Class<?> folderInfo = Class.forName(FOLDER_INFO, false, classLoader);
            gridUtilsClass = Class.forName(GRID_UTILS, false, classLoader);
            setMeasuredDimension = View.class.getDeclaredMethod(
                    "setMeasuredDimension", int.class, int.class);
            setMeasuredDimension.setAccessible(true);

            Method spanX = HookUtil.findMethodExact(
                    convert, "getFolderSpanXFromType", new Class<?>[]{int.class});
            Method spanY = HookUtil.findMethodExact(
                    convert, "getFolderSpanYFromType", new Class<?>[]{int.class});
            Method previewMeasure = HookUtil.findMethodExact(
                    preview, "onMeasure", new Class<?>[]{int.class, int.class});
            Method previewLayout = HookUtil.findMethodExact(
                    preview, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            Method gridSize = HookUtil.findMethodExact(
                    folderInfo, "getFolderGridSize", new Class<?>[0]);

            HookUtil.hook(spanX, chain -> {
                Object type = chain.getArgs().get(0);
                if (enabled && type instanceof Integer
                        && ((Integer) type) == VENDOR_TYPE_2X2_4) {
                    return 2;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(spanY, chain -> {
                Object type = chain.getArgs().get(0);
                if (enabled && type instanceof Integer
                        && ((Integer) type) == VENDOR_TYPE_2X2_4) {
                    return 1;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(gridSize, chain -> {
                Object info = chain.getThisObject();
                if (enabled && isTargetFolderInfo(info)) return "2*1";
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hook(previewMeasure, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (enabled && owner instanceof ViewGroup) {
                    applyPreviewMeasure((ViewGroup) owner);
                }
                return result;
            });
            HookUtil.hook(previewLayout, chain -> {
                Object owner = chain.getThisObject();
                if (!(owner instanceof ViewGroup) || !enabled) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                ViewGroup group = (ViewGroup) owner;
                Folder2x1LayoutPolicy.Layout layout = LAYOUTS.get(group);
                if (layout == null || !isTargetPreview(group)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                layoutChildren(group, layout);
                return null;
            });

            installed = true;
            MainHook.log(TAG + " installed enabled=" + enabled);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " unavailable on target Launcher: " + error);
            return false;
        }
    }

    private static void applyPreviewMeasure(ViewGroup group) {
        if (!isTargetPreview(group)) {
            LAYOUTS.remove(group);
            return;
        }
        int iconSize = resolveWorkspaceIconSize(group);
        if (iconSize <= 0) {
            LAYOUTS.remove(group);
            return;
        }
        int contentCount = resolveContentCount(group);
        Folder2x1LayoutPolicy.Layout layout = Folder2x1LayoutPolicy.resolve(
                iconSize, contentCount, group.getChildCount());
        try {
            group.setPadding(0, 0, 0, 0);
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                Folder2x1LayoutPolicy.Slot slot =
                        i < layout.slots.length ? layout.slots[i] : null;
                if (slot == null) {
                    child.setVisibility(View.INVISIBLE);
                    continue;
                }
                child.setVisibility(View.VISIBLE);
                child.measure(
                        View.MeasureSpec.makeMeasureSpec(slot.width(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(slot.height(), View.MeasureSpec.EXACTLY));
            }
            setMeasuredDimension.invoke(group, layout.width, layout.height);
            LAYOUTS.put(group, layout);
        } catch (Throwable error) {
            LAYOUTS.remove(group);
            MainHook.log(TAG + " preview measure fallback: " + error);
        }
    }

    private static void layoutChildren(
            ViewGroup group, Folder2x1LayoutPolicy.Layout layout) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Folder2x1LayoutPolicy.Slot slot =
                    i < layout.slots.length ? layout.slots[i] : null;
            if (slot == null) {
                child.layout(0, 0, 0, 0);
                continue;
            }
            child.layout(slot.left, slot.top, slot.right, slot.bottom);
        }
    }

    private static boolean isTargetPreview(View preview) {
        Object info = findFolderInfo(preview);
        return isTargetFolderInfo(info);
    }

    private static boolean isTargetFolderInfo(Object info) {
        if (info == null) return false;
        try {
            return HookUtil.getIntField(info, "itemType") == VENDOR_TYPE_2X2_4
                    && HookUtil.getIntField(info, "spanX") == 2
                    && HookUtil.getIntField(info, "spanY") == 1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object findFolderInfo(View preview) {
        for (ViewParent parent = preview.getParent();
             parent != null; parent = parent.getParent()) {
            if (parent instanceof View
                    && FOLDER_ICON_2X2_4.equals(parent.getClass().getName())) {
                return ((View) parent).getTag();
            }
        }
        return null;
    }

    private static int resolveContentCount(View preview) {
        Object info = findFolderInfo(preview);
        if (info == null) return 0;
        HookUtil.InvocationResult<Object> count = HookUtil.tryInvoke(info, "count");
        if (count.succeeded() && count.value() instanceof Integer) {
            return Math.max(0, (Integer) count.value());
        }
        return preview instanceof ViewGroup ? ((ViewGroup) preview).getChildCount() : 0;
    }

    private static int resolveWorkspaceIconSize(View preview) {
        Class<?> gridUtils = gridUtilsClass;
        if (gridUtils == null) return 0;
        HookUtil.InvocationResult<Object> grid =
                HookUtil.tryInvokeStatic(gridUtils, "findParentUsedGridConfig", preview);
        if (!grid.succeeded() || grid.value() == null) return 0;
        HookUtil.InvocationResult<Object> iconSize =
                HookUtil.tryInvoke(grid.value(), "getIconSize");
        if (!iconSize.succeeded() || !(iconSize.value() instanceof Integer)) return 0;
        return Launcher450IconSizePolicy.scaledPx(
                (Integer) iconSize.value(), iconSizeEnabled, iconSizePercent);
    }
}
