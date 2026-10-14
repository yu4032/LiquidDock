package com.hellovoid.liquiddock;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The fourth FolderSheet size option is a genuine native FolderIcon2x2_4
 * at a real 2x2 Workspace footprint. No second content layer is attached.
 *
 * In particular, the fourth quadrant is kept as the OEM 2x2 cluster of
 * SMALL preview icons. Native FolderPreviewIconView instances continue to
 * own launch, return-to-icon, drag, open and close animations.
 */
final class LauncherNativeFourFolderHook {
    private static final String TAG = "[DC][FolderFourNative]";
    private static final String NATIVE_FOUR =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final String CONTENT_HOST =
            "com.miui.home.launcher.folder.LauncherFolder2x2IconContainer";
    // A single source of dimensions: the CellLayout item frame (not the
    // already-constrained inner icon's incoming MeasureSpec).
    private static final ThreadLocal<int[]> ROOT_FRAME = new ThreadLocal<>();
    private static final ThreadLocal<int[]> NATIVE_SIZE = new ThreadLocal<>();
    private static final Set<View> WARNED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<View> CUSTOM_SIZED =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean installed;

    private LauncherNativeFourFolderHook() {}

    static boolean isFourGridInfo(Object info) {
        if (info == null) return false;
        try {
            return LargeFolderFootprintPolicy.hasRealTwoByTwoFootprint(
                    HookUtil.getIntField(info, "itemType"),
                    HookUtil.getIntField(info, "spanX"),
                    HookUtil.getIntField(info, "spanY"));
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean install(ClassLoader loader) {
        if (installed || loader == null) return installed;
        try {
            Class<?> cell = Class.forName(
                    "com.miui.home.launcher.CellLayout", false, loader);
            Class<?> content = Class.forName(CONTENT_HOST, false, loader);
            Class<?> config = Class.forName(
                    "com.miui.home.launcher.DeviceConfig", false, loader);
            Class<?> folderIcon = Class.forName(
                    "com.miui.home.launcher.FolderIcon", false, loader);
            Class<?> launcher = Class.forName(
                    "com.miui.home.launcher.Launcher", false, loader);
            Class<?> infoType = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, loader);
            Class<?> itemIcon = Class.forName(
                    "com.miui.home.launcher.ItemIcon", false, loader);

            // FolderIcon#getSpanX/Y already returns FolderInfo.spanX/Y.
            // The actual remaining clamp is ItemIcon#onMeasure, where the
            // DeviceConfig single-cell width can differ from the custom
            // workspace grid pitch. Scope the width override to the actual
            // native four-folder root measurement; never affect other icons.
            HookUtil.hook(config.getDeclaredMethod("getCellWidth"), chain -> {
                Object original = chain.proceed(chain.getArgs().toArray(new Object[0]));
                int[] frame = ROOT_FRAME.get();
                if (frame == null || frame[0] <= 0) return original;
                int minCellWidth = (frame[0] + 1) / 2;
                return Math.max(((Number) original).intValue(), minCellWidth);
            });
            HookUtil.hook(itemIcon.getDeclaredMethod(
                    "onMeasure", int.class, int.class), chain -> {
                Object owner = chain.getThisObject();
                if (!(owner instanceof View) || !eligible((View) owner)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View folder = (View) owner;
                int[] frame = desiredFrame(folder,
                        (Integer) chain.getArg(0), (Integer) chain.getArg(1));
                if (frame == null) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                int[] previous = ROOT_FRAME.get();
                ROOT_FRAME.set(frame);
                Object[] args = chain.getArgs().toArray(new Object[0]);
                args[0] = exact(frame[0]);
                args[1] = exact(frame[1]);
                try {
                    Object result = chain.proceed(args);
                    traceMeasure("root", folder, frame);
                    return result;
                } finally {
                    if (previous == null) ROOT_FRAME.remove();
                    else ROOT_FRAME.set(previous);
                }
            });

            // The built-in 2x2 content also calls DeviceConfig for widget
            // dimensions. Previously NATIVE_SIZE was taken from the inner
            // MeasureSpec, which was already 1x1 -- feeding that value back
            // reproduced the shrinking forever. Use the ROOT 2x2 allocation.

            HookUtil.hook(config.getDeclaredMethod("getMiuiWidgetSizeSpec",
                    int.class, int.class, boolean.class), chain -> {
                int[] size = NATIVE_SIZE.get();
                Object nativeValue = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (size == null
                        || ((Integer) chain.getArg(0)) != 2
                        || ((Integer) chain.getArg(1)) != 2
                        || !Boolean.TRUE.equals(chain.getArg(2))) return nativeValue;
                return ((long) size[0] << 32) | (size[1] & 0xffffffffL);
            });
            HookUtil.hook(content.getDeclaredMethod(
                    "onMeasure", int.class, int.class), chain -> {
                View child = (View) chain.getThisObject();
                View host = ancestorFolder(child);
                if (host == null || !eligible(host)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                // Do NOT feed the child's incoming MeasureSpec back into
                // DeviceConfig. It may be only one cell due to wrap_content.
                int[] frame = ROOT_FRAME.get();
                if (frame == null) {
                    frame = desiredFrame(host,
                            (Integer) chain.getArg(0),
                            (Integer) chain.getArg(1));
                }
                if (frame == null) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                int[] previous = NATIVE_SIZE.get();
                NATIVE_SIZE.set(frame);
                try {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    traceMeasure("inner", child, frame);
                    return result;
                } finally {
                    if (previous == null) NATIVE_SIZE.remove();
                    else NATIVE_SIZE.set(previous);
                }
            });

            // Native FolderIcon.fromXml() caches mBuddyIconView across resize.
            // If it still points to a 1x1 folder after conversion, force the
            // correct OEM 2x2_4 binding instead of layering another renderer.
            HookUtil.hook(folderIcon.getDeclaredMethod("fromXml",
                    int.class, launcher, ViewGroup.class, infoType), chain -> {
                Object info = chain.getArg(3);
                ViewGroup parent = (ViewGroup) chain.getArg(2);
                if (info != null) {
                    boolean marked = LauncherLargeFolderPickerHook.isMarked(
                            parent.getContext(), info);
                    HookUtil.InvocationResult<Object> cached =
                            HookUtil.tryInvoke(info, "getBuddyIconView");
                    if (cached.succeeded() && cached.value() instanceof View) {
                        View old = (View) cached.value();
                        boolean wrongClass = marked && isFourGridInfo(info)
                                && !NATIVE_FOUR.equals(old.getClass().getName());
                        boolean previouslyCustom = !marked && CUSTOM_SIZED.remove(old);
                        if (wrongClass || previouslyCustom) {
                            ViewParent p = old.getParent();
                            if (p == null || p == parent) {
                                if (p == parent) parent.removeView(old);
                                HookUtil.tryInvoke(info, "setBuddyIconView", (Object) null);
                                Api101Bridge.log(TAG + " invalidated cached native icon "
                                        + (previouslyCustom ? "after reverting size" : "after resize"));
                            }
                        }
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hook(cell.getDeclaredMethod("onLayout",
                    boolean.class, int.class, int.class, int.class, int.class), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof ViewGroup) {
                    ViewGroup workspace = (ViewGroup) chain.getThisObject();
                    for (int i = 0; i < workspace.getChildCount(); i++) {
                        View folder = workspace.getChildAt(i);
                        if (!eligible(folder)) continue;
                        try {
                            alignNativeFolderToWidget(workspace, folder);
                        } catch (Throwable error) {
                            Api101Bridge.log(TAG + " native 2x2 alignment failed: " + error);
                        }
                    }
                }
                return result;
            });
            installed = true;
            Api101Bridge.log(TAG + " native 2x2_4 preview and widget-frame hooks ready");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " installation unavailable: " + error);
            return false;
        }
    }

    private static final Set<View> MEASURE_LOGGED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static int[] desiredFrame(View folder, int incomingWidthSpec,
            int incomingHeightSpec) {
        ViewGroup.LayoutParams lp = folder.getLayoutParams();
        int requestedWidth = View.MeasureSpec.getSize(incomingWidthSpec);
        int requestedHeight = View.MeasureSpec.getSize(incomingHeightSpec);
        // ItemIcon's LayoutParams comes from CellLayout.setupLayoutParam;
        // it expresses the full multi-cell width, unlike the native
        // wrap_content inner child measured later.
        int width = lp != null && lp.width > 0 ? lp.width : requestedWidth;
        int height = lp != null && lp.height > 0 ? lp.height : requestedHeight;
        if (width <= 0 || height <= 0) return null;
        return new int[]{width, height};
    }

    private static void traceMeasure(String stage, View view, int[] expected) {
        if (!MEASURE_LOGGED.add(view)) return;
        Api101Bridge.log(TAG + " " + stage + " expected="
                + expected[0] + "x" + expected[1] + " measured="
                + view.getMeasuredWidth() + "x" + view.getMeasuredHeight());
    }

    private static boolean eligible(View folder) {
        if (folder == null || !NATIVE_FOUR.equals(folder.getClass().getName())
                || !isFourGridInfo(folder.getTag())) return false;
        return LauncherLargeFolderPickerHook.isMarked(
                folder.getContext(), folder.getTag());
    }

    private static View ancestorFolder(View child) {
        ViewParent parent = child.getParent();
        for (int n = 0; parent instanceof View && n < 8; n++) {
            View v = (View) parent;
            if (NATIVE_FOUR.equals(v.getClass().getName())) return v;
            parent = v.getParent();
        }
        return null;
    }

    private static View nativeContent(View node, int depth) {
        if (node == null || depth > 7) return null;
        if (CONTENT_HOST.equals(node.getClass().getName())) return node;
        if (!(node instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) node;
        for (int i = 0; i < group.getChildCount(); i++) {
            View result = nativeContent(group.getChildAt(i), depth + 1);
            if (result != null) return result;
        }
        return null;
    }

    private static void alignNativeFolderToWidget(ViewGroup workspace, View folder) {
        Object info = folder.getTag();
        ViewGroup.LayoutParams lp = folder.getLayoutParams();
        if (lp == null) return;
        try {
            if (HookUtil.getBooleanField(lp, "isDragging")) return;
        } catch (Throwable ignored) {}
        int[] xs = (int[]) HookUtil.getField(workspace, "mXs");
        int[] ys = (int[]) HookUtil.getField(workspace, "mYs");
        int cx = HookUtil.getIntField(info, "cellX");
        int cy = HookUtil.getIntField(info, "cellY");
        int cw = HookUtil.getIntField(workspace, "mCellWidth");
        int ch = HookUtil.getIntField(workspace, "mCellHeight");
        if (xs == null || ys == null || cx < 0 || cy < 0
                || cx + 1 >= xs.length || cy + 1 >= ys.length
                || cw <= 0 || ch <= 0) return;
        int[] allocation = {xs[cx], ys[cy],
                xs[cx + 1] + cw - xs[cx],
                ys[cy + 1] + ch - ys[cy]};
        int[] frame = allocation;
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) lp;
            frame = WidgetGridSizing.centeredFrame(
                    allocation, p.leftMargin, p.topMargin,
                    p.rightMargin, p.bottomMargin);
        }
        CUSTOM_SIZED.add(folder);
        int w = frame[2], h = frame[3];
        if (w <= 0 || h <= 0) return;
        lp.width = w;
        lp.height = h;
        try {
            HookUtil.setIntField(lp, "x", frame[0]);
            HookUtil.setIntField(lp, "y", frame[1]);
        } catch (Throwable ignored) {}
        if (folder.getMeasuredWidth() != w || folder.getMeasuredHeight() != h) {
            folder.measure(exact(w), exact(h));
        }
        if (folder.getLeft() != frame[0] || folder.getTop() != frame[1]
                || folder.getWidth() != w || folder.getHeight() != h) {
            folder.layout(frame[0], frame[1], frame[0] + w, frame[1] + h);
        }

        View inner = nativeContent(folder, 0);
        if (inner == null) {
            if (WARNED.add(folder)) {
                Api101Bridge.log(TAG + " native icon content missing after binding");
            }
            return;
        }
        ViewGroup.LayoutParams innerParams = inner.getLayoutParams();
        if (innerParams == null) return;
        // The native folder preview owns three normal large icons plus the
        // small 2x2 group in quadrant four. Let its original preSetup2x2 and
        // onMeasureChild2x2 determine the SAME icon sizes as OEM.
        // Only expand the parent content frame to the widget allocation.
        int side = Math.min(w, h);
        ViewParent wrapperParent = inner.getParent();
        if (wrapperParent instanceof View) {
            // icon_container is a separate wrap_content FrameLayout centered
            // by the native FolderIcon2x2 RelativeLayout. Expanding ONLY its
            // child causes the large preview to be offset from the grid.
            View wrapper = (View) wrapperParent;
            ViewGroup.LayoutParams wrapperParams = wrapper.getLayoutParams();
            if (wrapperParams != null
                    && (wrapperParams.width != side || wrapperParams.height != side
                    || wrapper.getWidth() != side || wrapper.getHeight() != side)) {
                wrapperParams.width = side;
                wrapperParams.height = side;
                wrapper.measure(exact(side), exact(side));
                int x = (w - side) / 2;
                wrapper.layout(x, 0, x + side, side);
            }
        }
        if (innerParams.width != side || innerParams.height != side
                || inner.getWidth() != side || inner.getHeight() != side) {
            innerParams.width = side;
            innerParams.height = side;
            inner.measure(exact(side), exact(side));
            inner.layout(0, 0, side, side);
        }
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
