package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.content.Context;

/**
 * Follows native large-folder measurement for an explicitly selected 2x2
 * folder. The workspace allocation is authoritative. Unlike View.scaleX/Y,
 * this updates the actual measured image, cover and preview geometry, so the
 * native launcher also captures the correct drag content and hit regions.
 */
final class LauncherLargeFolderVisualHook {
    private static final String TAG = "[DC][FolderFourVisual]";
    private static final String FOUR_CLASS =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final ThreadLocal<Integer> MEASURING_CELL_WIDTH = new ThreadLocal<>();
    private static boolean installed;

    private LauncherLargeFolderVisualHook() {}

    static boolean install(ClassLoader loader) {
        if (loader == null) return false;
        if (installed) return true;
        try {
            Class<?> iconClass =
                    Class.forName("com.miui.home.launcher.ItemIcon", false, loader);
            Class<?> deviceConfig =
                    Class.forName("com.miui.home.launcher.DeviceConfig", false, loader);

            // ItemIcon limits measured width using DeviceConfig.getCellWidth()
            // even when CellLayout has already allocated a full custom grid
            // 2x2 frame. Override only during this exact icon's measure.
            HookUtil.hook(deviceConfig.getDeclaredMethod("getCellWidth"), chain -> {
                Object nativeSize = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Integer needed = MEASURING_CELL_WIDTH.get();
                return needed != null && nativeSize instanceof Integer
                        ? Math.max((Integer) nativeSize, needed) : nativeSize;
            });
            HookUtil.hook(iconClass.getDeclaredMethod(
                    "onMeasure", int.class, int.class), chain -> {
                Object owner = chain.getThisObject();
                if (!(owner instanceof View)
                        || !FOUR_CLASS.equals(owner.getClass().getName())
                        || !isSelectedTwoByTwo((View) owner)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                View icon = (View) owner;
                int widthSpec = (Integer) chain.getArg(0);
                int heightSpec = (Integer) chain.getArg(1);
                int width = View.MeasureSpec.getSize(widthSpec);
                int height = View.MeasureSpec.getSize(heightSpec);
                if (width <= 0 || height <= 0) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                // Use actual grid allocation, not a magnified 1x1 bitmap.
                // The existing native FolderIcon2x2_4 preview positioning
                // then recalculates four equal cells from the larger square.
                resizeNativeLargeFolderContents(icon, width, height);
                Integer prior = MEASURING_CELL_WIDTH.get();
                MEASURING_CELL_WIDTH.set(Math.max(1, (width + 1) / 2));
                try {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (icon.getMeasuredWidth() != width
                            || icon.getMeasuredHeight() != height) {
                        MainHook.log(TAG + " unexpected measured folder size: allocated="
                                + width + "x" + height + " actual="
                                + icon.getMeasuredWidth() + "x" + icon.getMeasuredHeight());
                    }
                    return result;
                } finally {
                    if (prior == null) MEASURING_CELL_WIDTH.remove();
                    else MEASURING_CELL_WIDTH.set(prior);
                }
            });
            installed = true;
            Api101Bridge.log(TAG + " native large-folder measurement installed");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native measurement unavailable: " + error);
            return false;
        }
    }

    static boolean isSelectedTwoByTwo(View folder) {
        if (folder == null) return false;
        Object info = folder.getTag();
        if (info == null
                || !LauncherLargeFolderPickerHook.isMarked(folder.getContext(), info)) {
            return false;
        }
        try {
            return HookUtil.getIntField(info, "itemType") == 21
                    && HookUtil.getIntField(info, "spanX") == 2
                    && HookUtil.getIntField(info, "spanY") == 2;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void resizeNativeLargeFolderContents(View folder, int width, int height) {
        Context context = folder.getContext();
        // Keep the native title's measured space. Relative constraints place
        // it below the icon container, not inside the expanded preview.
        int titleHeight = dp(context, 24);
        Object nativeTitle = fieldOrNull(folder, "mTitleContainer");
        if (nativeTitle instanceof View) {
            View title = (View) nativeTitle;
            if (title.getMeasuredHeight() > 0) {
                titleHeight = Math.max(titleHeight, title.getMeasuredHeight());
            }
        }
        int square = Math.min(width - folder.getPaddingLeft() - folder.getPaddingRight(),
                height - folder.getPaddingTop() - folder.getPaddingBottom() - titleHeight);
        if (square <= dp(context, 56)) return;

        // Stock large-folder XML has fixed icon-container resources tuned to
        // the OEM grid. Derive their bounds from the actual 2x2 CellLayout
        // allocation, preserving each node's native drawable and padding.
        sizeSquare(fieldOrNull(folder, "mIconContainer"), square);
        sizeSquare(fieldOrNull(folder, "mIconImageView"), square);
        sizeSquare(fieldOrNull(folder, "mImageView"), square);
        sizeSquare(fieldOrNull(folder, "mFolderCover"), square);
        HookUtil.InvocationResult<Object> container =
                HookUtil.tryInvoke(folder, "getMPreviewContainer");
        if (container.succeeded()) sizeSquare(container.value(), square);
    }

    private static Object fieldOrNull(Object target, String name) {
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }

    private static void sizeSquare(Object owner, int side) {
        if (!(owner instanceof View)) return;
        View child = (View) owner;
        ViewGroup.LayoutParams params = child.getLayoutParams();
        if (params == null) return;
        if (params.width == side && params.height == side) return;
        params.width = side;
        params.height = side;
        child.setLayoutParams(params);
    }

    private static int dp(Context context, int value) {
        return (int) (context.getResources().getDisplayMetrics().density * value + .5f);
    }
}
