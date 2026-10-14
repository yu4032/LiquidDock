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
            // Vendor 4.50 may rename or inline *one* method. Never let
            // that disable unrelated geometry, model and view binding hooks.
            Class<?> cell = vendorClass(loader, "com.miui.home.launcher.CellLayout");
            Class<?> content = vendorClass(loader, CONTENT_HOST);
            Class<?> config = vendorClass(loader, "com.miui.home.launcher.DeviceConfig");
            Class<?> folderIcon = vendorClass(loader, "com.miui.home.launcher.FolderIcon");
            Class<?> launcher = vendorClass(loader, "com.miui.home.launcher.Launcher");
            Class<?> infoType = vendorClass(loader, "com.miui.home.launcher.FolderInfo");
            Class<?> itemIcon = vendorClass(loader, "com.miui.home.launcher.ItemIcon");
            Class<?> nativeImageClass = vendorClass(loader,
                    "com.miui.home.launcher.folder.LauncherFolder2x2IconImageView");
            int installedHooks = 0;

            // Device log 13:55:05:
            // background/cover [measured=287, params=718] and
            // preview [measured=359, params=718]. The OEM onMeasure,
            // not LayoutParams, is the source of the remaining 1x1 result.
            // Launcher 4.50 has no hookable getWidth/HeightMeasureSpec
            // methods (the preceding approach installed 0/2 hooks).
            // Hook the actual onMeasure implementations instead.
            if (nativeImageClass != null) {
                try {
                    java.lang.reflect.Method measure = HookUtil.findMethodExact(
                            nativeImageClass, "onMeasure",
                            new Class<?>[]{int.class, int.class});
                    HookUtil.hook(measure, chain -> {
                        Object instance = chain.getThisObject();
                        if (!(instance instanceof View)
                                || !instance.getClass().getName().equals(
                                "com.miui.home.launcher.folder.LauncherFolder2x2IconImageView")) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        View surface = (View) instance;
                        View root = ancestorFolder(surface);
                        if (root == null || !eligible(root)) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        int[] frame = desiredFrame(root,
                                (Integer) chain.getArg(0),
                                (Integer) chain.getArg(1));
                        if (frame == null) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        Object result = chain.proceed(new Object[]{
                                exact(frame[0]), exact(frame[1])});
                        forceMeasuredFrame(surface, frame, "image");
                        return result;
                    });
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed native image onMeasure "
                            + measure.toGenericString());
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native image onMeasure unavailable", error);
                }
            }
            Class<?> nativePreviewClass = vendorClass(loader,
                    "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4");
            if (nativePreviewClass != null) {
                try {
                    java.lang.reflect.Method measure = HookUtil.findMethodExact(
                            nativePreviewClass, "onMeasure",
                            new Class<?>[]{int.class, int.class});
                    HookUtil.hook(measure, chain -> {
                        Object instance = chain.getThisObject();
                        if (!(instance instanceof View)
                                || !instance.getClass().getName().equals(
                                "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4")) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        View previewView = (View) instance;
                        View root = ancestorFolder(previewView);
                        if (root == null || !eligible(root)) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        int[] frame = desiredFrame(root,
                                (Integer) chain.getArg(0),
                                (Integer) chain.getArg(1));
                        if (frame == null) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        // OEM preMeasure2x2() runs inside this call. Feed it
                        // the full widget frame so large/small icon rectangles
                        // are recomputed before their native onLayout2x2().
                        Object result = chain.proceed(new Object[]{
                                exact(frame[0]), exact(frame[1])});
                        forceMeasuredFrame(previewView, frame, "preview");
                        return result;
                    });
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed native preview onMeasure "
                            + measure.toGenericString());
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native preview onMeasure unavailable", error);
                }
            }

            // FolderIcon#getSpanX/Y already returns FolderInfo.spanX/Y.
            // The actual remaining clamp is ItemIcon#onMeasure, where the
            // DeviceConfig single-cell width can differ from the custom
            // workspace grid pitch. Scope the width override to the actual
            // native four-folder root measurement; never affect other icons.
            if (config != null) {
                try {
            HookUtil.hook(config.getDeclaredMethod("getCellWidth"), chain -> {
                Object original = chain.proceed(chain.getArgs().toArray(new Object[0]));
                int[] frame = ROOT_FRAME.get();
                if (frame == null || frame[0] <= 0) return original;
                int minCellWidth = (frame[0] + 1) / 2;
                return Math.max(((Number) original).intValue(), minCellWidth);
            });
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed DeviceConfig#getCellWidth");
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " DeviceConfig#getCellWidth unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " DeviceConfig#getCellWidth class unavailable");
            }
            if (itemIcon != null) {
                try {
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
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed ItemIcon#onMeasure");
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " ItemIcon#onMeasure unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " ItemIcon#onMeasure class unavailable");
            }

            // The built-in 2x2 content also calls DeviceConfig for widget
            // dimensions. Previously NATIVE_SIZE was taken from the inner
            // MeasureSpec, which was already 1x1 -- feeding that value back
            // reproduced the shrinking forever. Use the ROOT 2x2 allocation.

            if (config != null) {
                try {
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
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed DeviceConfig#getMiuiWidgetSizeSpec");
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " DeviceConfig#getMiuiWidgetSizeSpec unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " DeviceConfig#getMiuiWidgetSizeSpec class unavailable");
            }
            if (content != null) {
                try {
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
                // Device-confirmed defect (2026-10-14):
                // native host=718x721, icon background=287x287,
                // preview=359x359. These are *child* layout parameters
                // inherited from the stock compact folder, not a span,
                // CellLayout or host measurement problem. Adjust the
                // actual native children BEFORE FrameLayout measures them.
                // The native FolderIconPreviewContainer2X2_4 still owns its
                // three-large + fourth-quadrant four-small icon algorithm.
                resizeNativePreviewChildren(child, frame[0], frame[1]);
                int[] previous = NATIVE_SIZE.get();
                NATIVE_SIZE.set(frame);
                try {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    traceMeasure("inner", child, frame);
                    tracePreviewChildren("after-inner-measure", child);
                    return result;
                } finally {
                    if (previous == null) NATIVE_SIZE.remove();
                    else NATIVE_SIZE.set(previous);
                }
            });
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed LauncherFolder2x2IconContainer#onMeasure");
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " LauncherFolder2x2IconContainer#onMeasure unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " LauncherFolder2x2IconContainer#onMeasure class unavailable");
            }

            // Native FolderIcon.fromXml() caches mBuddyIconView across resize.
            // If it still points to a 1x1 folder after conversion, force the
            // correct OEM 2x2_4 binding instead of layering another renderer.
            if (folderIcon != null && launcher != null && infoType != null) {
                try {
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
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed FolderIcon#fromXml");
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " FolderIcon#fromXml unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " FolderIcon#fromXml class unavailable");
            }

            if (cell != null) {
                try {
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
                    installedHooks++;
                    Api101Bridge.log(TAG + " installed CellLayout#onLayout");
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " CellLayout#onLayout unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " CellLayout#onLayout class unavailable");
            }
            installed = installedHooks > 0;
            Api101Bridge.log(TAG + " native folder hook coverage=" + installedHooks
                    + "/8; class-specific compatibility requires runtime trace");
            return installed;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " installation unavailable: " + error);
            return false;
        }
    }

    private static Class<?> vendorClass(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " vendor class missing: " + name + " " + error);
            return null;
        }
    }

    private static final Set<View> CORRECTION_LOGGED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static void forceMeasuredFrame(View child, int[] frame, String layer) {
        int beforeWidth = child.getMeasuredWidth();
        int beforeHeight = child.getMeasuredHeight();
        if (beforeWidth == frame[0] && beforeHeight == frame[1]) return;
        HookUtil.InvocationResult<Object> resized = HookUtil.tryInvoke(
                child, "setMeasuredDimension", frame[0], frame[1]);
        if (CORRECTION_LOGGED.add(child)) {
            Api101Bridge.log(TAG + " measured-frame " + layer
                    + " before=" + beforeWidth + "x" + beforeHeight
                    + " target=" + frame[0] + "x" + frame[1]
                    + " after=" + child.getMeasuredWidth()
                    + "x" + child.getMeasuredHeight()
                    + " success=" + resized.succeeded()
                    + (resized.succeeded() ? "" : " failure=" + resized.failure()));
        }
    }

    private static final Set<View> PREVIEW_LOGGED =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static void resizeNativePreviewChildren(View container, int width, int height) {
        if (!(container instanceof ViewGroup) || width <= 0 || height <= 0) return;
        ViewGroup group = (ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            String name = view.getClass().getName();
            boolean iconSurface = name.endsWith(".LauncherFolder2x2IconImageView");
            boolean preview = name.endsWith(".FolderIconPreviewContainer2X2_4");
            if (!iconSurface && !preview) continue;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null) continue;
            if (params.width != width || params.height != height) {
                params.width = width;
                params.height = height;
                view.setLayoutParams(params);
            }
        }
    }

    private static void tracePreviewChildren(String stage, View container) {
        if (!(container instanceof ViewGroup) || !PREVIEW_LOGGED.add(container)) return;
        ViewGroup group = (ViewGroup) container;
        StringBuilder log = new StringBuilder(TAG).append(" ").append(stage)
                .append(" host=").append(group.getMeasuredWidth())
                .append("x").append(group.getMeasuredHeight());
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            ViewGroup.LayoutParams params = child.getLayoutParams();
            log.append(" ").append(child.getClass().getSimpleName())
                    .append("[").append(child.getMeasuredWidth())
                    .append("x").append(child.getMeasuredHeight()).append("/")
                    .append(params == null ? "null"
                            : params.width + "x" + params.height)
                    .append("]");
        }
        Api101Bridge.log(log.toString());
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
        // Also fix pre-existing folder views created before the first
        // measured 2x2 binding; subsequent layouts then stay native.
        resizeNativePreviewChildren(inner, w, h);
        // The native folder preview owns three normal large icons plus the
        // small 2x2 group in quadrant four. Let its original preSetup2x2 and
        // onMeasureChild2x2 determine the SAME icon sizes as OEM.
        // Only expand the parent content frame to the widget allocation.
        // Do not square the widget frame! On tablets and custom HomeGrid
        // configurations, a 2x2 allocation can be wider than it is tall.
        // Using min(w, h) recreated the appearance of a small centered
        // folder despite the full 2x2 workspace reservation.
        ViewParent wrapperParent = inner.getParent();
        if (wrapperParent instanceof View) {
            View wrapper = (View) wrapperParent;
            ViewGroup.LayoutParams wrapperParams = wrapper.getLayoutParams();
            if (wrapperParams != null
                    && (wrapperParams.width != w || wrapperParams.height != h
                    || wrapper.getWidth() != w || wrapper.getHeight() != h)) {
                wrapperParams.width = w;
                wrapperParams.height = h;
                wrapper.measure(exact(w), exact(h));
                wrapper.layout(0, 0, w, h);
            }
        }
        if (innerParams.width != w || innerParams.height != h
                || inner.getWidth() != w || inner.getHeight() != h) {
            innerParams.width = w;
            innerParams.height = h;
            inner.measure(exact(w), exact(h));
            inner.layout(0, 0, w, h);
        }
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
