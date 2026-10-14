package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
    private static final ThreadLocal<int[]> NATIVE_CONTENT_SPEC = new ThreadLocal<>();
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
            // Root cause: native LauncherFolder2x2IconContainer ignores the
            // dynamically allocated Workspace size and obtains another 2x2
            // size from DeviceConfig.getMiuiWidgetSizeSpec(). That value is
            // based on the vendor's fixed grid, so it keeps a small preview
            // centered within our correctly allocated 2x2 folder frame.
            // Redirect only the nested container's own measurement request.
            try {
            Class<?> nativeContainer = Class.forName(
                    "com.miui.home.launcher.folder.LauncherFolder2x2IconContainer",
                    false, loader);
            HookUtil.hook(deviceConfig.getDeclaredMethod("getMiuiWidgetSizeSpec",
                    int.class, int.class, boolean.class), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                int[] content = NATIVE_CONTENT_SPEC.get();
                if (content == null || ((Integer) chain.getArg(0)) != 2
                        || ((Integer) chain.getArg(1)) != 2) return result;
                return ((long) content[0] << 32) | (content[1] & 0xffffffffL);
            });
            HookUtil.hook(nativeContainer.getDeclaredMethod(
                    "onMeasure", int.class, int.class), chain -> {
                Object owner = chain.getThisObject();
                if (!(owner instanceof View)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View container = (View) owner;
                View folder = findFourFolderAncestor(container);
                if (folder == null || !isSelectedTwoByTwo(folder)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                int[] nativeContent = actualContainerSize(
                        folder, container, (Integer) chain.getArg(0),
                        (Integer) chain.getArg(1));
                int[] previous = NATIVE_CONTENT_SPEC.get();
                if (nativeContent != null) NATIVE_CONTENT_SPEC.set(nativeContent);
                try {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (nativeContent != null
                            && (container.getMeasuredWidth() < nativeContent[0] * 3 / 4
                            || container.getMeasuredHeight() < nativeContent[1] * 3 / 4)) {
                        Api101Bridge.log(TAG + " native preview undersized: allocated="
                                + nativeContent[0] + "x" + nativeContent[1]
                                + " actual=" + container.getMeasuredWidth()
                                + "x" + container.getMeasuredHeight());
                    }
                    return result;
                } finally {
                    if (previous == null) NATIVE_CONTENT_SPEC.remove();
                    else NATIVE_CONTENT_SPEC.set(previous);
                }
            });
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " native fixed widget-size transaction unavailable: " + error);
            }
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
            // MIUI caches FolderInfo.mBuddyIconView. After a native resize
            // the persisted model can say 2x2 while fromXml silently returns
            // its old 1x1 view, including the old background and drag image.
            // Invalidate that stale view only at the vendor rebind boundary.
            try {
                Class<?> folderIcon = Class.forName(
                        "com.miui.home.launcher.FolderIcon", false, loader);
                Class<?> launcher = Class.forName(
                        "com.miui.home.launcher.Launcher", false, loader);
                Class<?> folderInfo = Class.forName(
                        "com.miui.home.launcher.FolderInfo", false, loader);
                HookUtil.hook(folderIcon.getDeclaredMethod("fromXml",
                        int.class, launcher, ViewGroup.class, folderInfo), chain -> {
                    Object info = chain.getArg(3);
                    Object group = chain.getArg(2);
                    if (group instanceof ViewGroup && info != null
                            && LauncherLargeFolderPickerHook.isMarked(
                                    ((ViewGroup) group).getContext(), info)) {
                        int type = HookUtil.getIntField(info, "itemType");
                        int sx = HookUtil.getIntField(info, "spanX");
                        int sy = HookUtil.getIntField(info, "spanY");
                        if (type == 21 && sx == 2 && sy == 2) {
                            HookUtil.InvocationResult<Object> cached =
                                    HookUtil.tryInvoke(info, "getBuddyIconView");
                            if (cached.succeeded() && cached.value() instanceof View) {
                                View old = (View) cached.value();
                                if (!FOUR_CLASS.equals(old.getClass().getName())) {
                                    android.view.ViewParent parent = old.getParent();
                                    if (parent == null
                                            || parent == group && old.getTag() == info) {
                                        if (parent == group) {
                                            ((ViewGroup) parent).removeView(old);
                                        }
                                        HookUtil.tryInvoke(info, "setBuddyIconView", (Object) null);
                                        MainHook.log(TAG + " replaced stale small-folder binding "
                                                + old.getClass().getName());
                                    }
                                }
                            }
                        }
                    }
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                });
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " old buddy view compatibility: " + error);
            }
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
        // folder_icon_2x2_4.xml has a second WRAP_CONTENT FrameLayout
        // (icon_top_container) between icon_container and the background,
        // preview and cover. The old implementation resized only the outer
        // FrameLayout, leaving this measured inner host at stock dimensions.
        // Traverse the rendered XML instead of relying on a protected
        // getMPreviewContainer() accessor. This resolves the intermediate
        // WRAP_CONTENT host even when the vendor renamed the accessor.
        View nativeHost = findNativeContentHost(folder, 0);
        if (nativeHost != null) {
            sizeSquare(nativeHost, square);
        } else {
            Api101Bridge.log(TAG + " nested 2x2 content host unavailable "
                    + folder.getClass().getName());
        }
        sizeSquare(fieldOrNull(folder, "mIconImageView"), square);
        sizeSquare(fieldOrNull(folder, "mImageView"), square);
        sizeSquare(fieldOrNull(folder, "mFolderCover"), square);
        HookUtil.InvocationResult<Object> container =
                HookUtil.tryInvoke(folder, "getMPreviewContainer");
        if (container.succeeded()) sizeSquare(container.value(), square);
    }

    private static View findNativeContentHost(View root, int depth) {
        if (root == null || depth > 7) return null;
        if ("com.miui.home.launcher.folder.LauncherFolder2x2IconContainer"
                .equals(root.getClass().getName())) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View match = findNativeContentHost(group.getChildAt(i), depth + 1);
            if (match != null) return match;
        }
        return null;
    }

    private static View findFourFolderAncestor(View child) {
        ViewParent parent = child.getParent();
        for (int depth = 0; parent instanceof View && depth < 8; depth++) {
            View current = (View) parent;
            if (FOUR_CLASS.equals(current.getClass().getName())) return current;
            parent = current.getParent();
        }
        return null;
    }

    private static int[] actualContainerSize(
            View folder, View container, int widthSpec, int heightSpec) {
        int width = View.MeasureSpec.getSize(widthSpec);
        int height = View.MeasureSpec.getSize(heightSpec);
        // Explicit dimensions installed on the actual native
        // icon_top_container take precedence over its old vendor widget spec.
        ViewGroup.LayoutParams params = container.getLayoutParams();
        if (params != null && params.width > 0) width = params.width;
        if (params != null && params.height > 0) height = params.height;
        if (width <= 0 || height <= 0) {
            Object top = fieldOrNull(folder, "mIconContainer");
            if (top instanceof View) {
                ViewGroup.LayoutParams outer = ((View) top).getLayoutParams();
                if (outer != null) {
                    if (width <= 0) width = outer.width;
                    if (height <= 0) height = outer.height;
                }
            }
        }
        if (width <= 0 || height <= 0) return null;
        return new int[]{width, height};
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
