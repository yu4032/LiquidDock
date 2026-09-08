package com.hellovoid.liquiddock;

/** Owns only the Widget allocation and final-frame hooks for the custom Home grid. */
final class HomeGridWidgetAdaptationHook {
    private static final String TAG = "[DC][WidgetGrid]";
    private final boolean adaptationEnabled;

    HomeGridWidgetAdaptationHook(boolean adaptationEnabled) {
        this.adaptationEnabled = adaptationEnabled;
    }

    void install(ClassLoader classLoader) {
        Class<?> cellLayout;
        try {
            cellLayout = Class.forName(
                    "com.miui.home.launcher.CellLayout", false, classLoader);
        } catch (Throwable error) {
            MainHook.log(TAG + " CellLayout unavailable: " + error);
            return;
        }

        try {
            Class<?> itemInfo = Class.forName(
                    "com.miui.home.launcher.ItemInfo", false, classLoader);
            Class<?> cellLayoutParams = Class.forName(
                    "com.miui.home.launcher.CellLayout$LayoutParams", false, classLoader);
            HookUtil.hookMethod(cellLayout, "setupLayoutParam",
                    new Class[]{int.class, int.class, itemInfo, cellLayoutParams},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        applyWidgetGridSize(chain.getThisObject(),
                                (Integer) args[0], (Integer) args[1], args[2], args[3]);
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log(TAG + " span sizing hook unavailable: " + error);
        }

        try {
            HookUtil.hookMethod(cellLayout, "onLayout",
                    new Class[]{boolean.class, int.class, int.class, int.class, int.class},
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof android.view.ViewGroup) {
                            enforceWidgetGridFrames((android.view.ViewGroup) owner);
                        }
                        return result;
                    });
        } catch (Throwable error) {
            MainHook.log(TAG + " final frame hook unavailable: " + error);
        }
    }

    boolean shouldAdapt(Object itemInfo, int spanX, int spanY) {
        return adaptationEnabled
                && WidgetClassifier.isWidget(itemInfo)
                && WidgetSpecRegistry.DEFAULT.supports(spanX, spanY);
    }

    private void applyWidgetGridSize(Object cellLayout, int cellX, int cellY,
                                     Object info, Object layoutParams) {
        try {
            if (info == null || layoutParams == null) return;
            int spanX = HookUtil.getIntField(info, "spanX");
            int spanY = HookUtil.getIntField(info, "spanY");
            if (!shouldAdapt(info, spanX, spanY)) return;
            if (!(layoutParams instanceof android.view.ViewGroup.MarginLayoutParams)) return;

            int cellWidth = HookUtil.getIntField(cellLayout, "mCellWidth");
            int cellHeight = HookUtil.getIntField(cellLayout, "mCellHeight");
            int widthGap = Math.max(0, HookUtil.getIntField(cellLayout, "mWidthGap"));
            int heightGap = Math.max(0, HookUtil.getIntField(cellLayout, "mHeightGap"));
            int[] xs = (int[]) HookUtil.getField(cellLayout, "mXs");
            int[] ys = (int[]) HookUtil.getField(cellLayout, "mYs");
            if (cellWidth <= 0 || cellHeight <= 0 || xs == null || ys == null) return;

            int[] rect = WidgetGridSizing.gridRect(
                    true, cellX, cellY, spanX, spanY,
                    xs, ys, cellWidth, cellHeight, widthGap, heightGap);
            if (rect[2] <= 0 || rect[3] <= 0) return;

            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) layoutParams;
            lp.width = rect[2];
            lp.height = rect[3];
            HookUtil.setIntField(layoutParams, "x", rect[0]);
            HookUtil.setIntField(layoutParams, "y", rect[1]);
        } catch (Throwable error) {
            MainHook.log(TAG + " widget grid bounds failed: " + error);
        }
    }

    /** Reassert the same custom-grid allocation after MIUI's span-dependent onLayout work. */
    private void enforceWidgetGridFrames(android.view.ViewGroup cellLayout) {
        if (!adaptationEnabled || cellLayout == null) return;
        try {
            int cellWidth = HookUtil.getIntField(cellLayout, "mCellWidth");
            int cellHeight = HookUtil.getIntField(cellLayout, "mCellHeight");
            int widthGap = Math.max(0, HookUtil.getIntField(cellLayout, "mWidthGap"));
            int heightGap = Math.max(0, HookUtil.getIntField(cellLayout, "mHeightGap"));
            int[] xs = (int[]) HookUtil.getField(cellLayout, "mXs");
            int[] ys = (int[]) HookUtil.getField(cellLayout, "mYs");
            if (cellWidth <= 0 || cellHeight <= 0 || xs == null || ys == null) return;

            for (int i = 0; i < cellLayout.getChildCount(); i++) {
                android.view.View child = cellLayout.getChildAt(i);
                if (child == null || child.getVisibility() == android.view.View.GONE) continue;
                Object info = child.getTag();
                if (info == null) continue;

                Object lpObject = child.getLayoutParams();
                if (lpObject == null) continue;
                try {
                    if (HookUtil.getBooleanField(lpObject, "isDragging")) continue;
                } catch (Throwable ignored) {}

                int spanX;
                int spanY;
                int cellX;
                int cellY;
                try {
                    spanX = HookUtil.getIntField(info, "spanX");
                    spanY = HookUtil.getIntField(info, "spanY");
                    cellX = HookUtil.getIntField(info, "cellX");
                    cellY = HookUtil.getIntField(info, "cellY");
                } catch (Throwable ignored) {
                    continue;
                }
                if (!shouldAdapt(info, spanX, spanY)) continue;

                int[] rect = WidgetGridSizing.gridRect(
                        true, cellX, cellY, spanX, spanY,
                        xs, ys, cellWidth, cellHeight, widthGap, heightGap);
                int targetWidth = rect[2];
                int targetHeight = rect[3];
                if (targetWidth <= 0 || targetHeight <= 0) continue;

                if (lpObject instanceof android.view.ViewGroup.MarginLayoutParams) {
                    android.view.ViewGroup.MarginLayoutParams lp =
                            (android.view.ViewGroup.MarginLayoutParams) lpObject;
                    lp.width = targetWidth;
                    lp.height = targetHeight;
                }
                try {
                    HookUtil.setIntField(lpObject, "x", rect[0]);
                    HookUtil.setIntField(lpObject, "y", rect[1]);
                } catch (Throwable ignored) {}

                if (child.getMeasuredWidth() != targetWidth
                        || child.getMeasuredHeight() != targetHeight) {
                    child.measure(
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                    targetWidth, android.view.View.MeasureSpec.EXACTLY),
                            android.view.View.MeasureSpec.makeMeasureSpec(
                                    targetHeight, android.view.View.MeasureSpec.EXACTLY));
                }
                int right = rect[0] + targetWidth;
                int bottom = rect[1] + targetHeight;
                if (child.getLeft() != rect[0] || child.getTop() != rect[1]
                        || child.getRight() != right || child.getBottom() != bottom) {
                    child.layout(rect[0], rect[1], right, bottom);
                }
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " final widget frame enforcement failed: " + error);
        }
    }
}
