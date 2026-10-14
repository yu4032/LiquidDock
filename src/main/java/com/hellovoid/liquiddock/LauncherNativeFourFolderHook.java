package com.hellovoid.liquiddock;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** Scales native four- and nine-preview folders to a real 2x2 workspace footprint.
 * Preview contents, item interaction and animations remain Launcher-owned.
 */
final class LauncherNativeFourFolderHook {
    private static final String TAG = "[DC][FolderFourNative]";
    private static final String NATIVE_FOUR =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final String NATIVE_NINE =
            "com.miui.home.launcher.folder.FolderIcon2x2_9";
    private static final String PREVIEW_FOUR =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4";
    private static final String PREVIEW_NINE =
            "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_9";

    private static boolean nativeFolderClass(View root) {
        return root != null && (NATIVE_FOUR.equals(root.getClass().getName())
                || NATIVE_NINE.equals(root.getClass().getName()));
    }

    private static boolean nativePreviewClass(View preview) {
        return preview != null && (PREVIEW_FOUR.equals(preview.getClass().getName())
                || PREVIEW_NINE.equals(preview.getClass().getName()));
    }
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

    /** Called only while a native large folder is about to draw its title child. */
    static void reconcileTitleDrawingBeforeChild(View folder, View child) {
        if (!eligible(folder) || child == null) return;
        Object candidate = HookUtil.tryInvoke(folder, "getTitleContainer").value();
        if (candidate != child || trustedCellFrame(folder) == null) return;
        boolean modelHidden = Boolean.TRUE.equals(
                HookUtil.tryInvoke(folder.getTag(), "needHideTitleByFlags").value());
        boolean show = LargeFolderTitlePolicy.shouldShow(true,
                LauncherLargeFolderPickerHook.shouldShowTitle(folder.getContext(), folder.getTag()),
                modelHidden);
        if (show) restoreNativeTitleDrawing(folder, true);
    }

    static void refreshTitle(Object info) {
        if (info == null) return;
        Object view = HookUtil.tryInvoke(info, "getBuddyIconView").value();
        if (view instanceof View && eligible((View) view)) {
            queueTitleRestore((View) view);
        }
    }

    static boolean isCustomFolder(View folder) {
        return eligible(folder);
    }

    static boolean isCustomFolderInfo(View contextOwner, Object info) {
        return contextOwner != null && isFourGridInfo(info)
                && LauncherLargeFolderPickerHook.isMarked(contextOwner.getContext(), info);
    }

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
                    // Do not hook Android framework ImageView/ViewGroup
                    // onMeasure globally if OEM removed its override.
                    if (!measure.getDeclaringClass().getName().startsWith(
                            "com.miui.home.")) {
                        throw new NoSuchMethodException("vendor onMeasure absent: "
                                + measure.toGenericString());
                    }
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
                        int[] frame = visualFrame(root);
                        if (frame == null) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        NativeFourFolderSizing.Size size = nativeSizing(root, frame);
                        if (size == null) return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        // FolderAnimController interpolates mCustomWidth/Height on
                        // its own native copy. Preserve those per-frame values.
                        if (workspaceSource(root) != root
                                && (HookUtil.getIntField(surface, "mCustomWidth") > 0
                                || HookUtil.getIntField(surface, "mCustomHeight") > 0)) {
                            return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        }
                        frame = new int[]{size.backgroundWidth, size.backgroundHeight};
                        // Launcher 4.50 explicitly supports per-view custom dimensions.
                        // Let ImageView measure normally so its drawable matrix also updates.
                        int previousWidth = HookUtil.getIntField(surface, "mCustomWidth");
                        int previousHeight = HookUtil.getIntField(surface, "mCustomHeight");
                        HookUtil.setIntField(surface, "mCustomWidth", frame[0]);
                        HookUtil.setIntField(surface, "mCustomHeight", frame[1]);
                        try {
                            Object result = chain.proceed(new Object[]{exact(frame[0]), exact(frame[1])});
                            forceMeasuredFrame(surface, frame, "image");
                            return result;
                        } finally {
                            HookUtil.setIntField(surface, "mCustomWidth", previousWidth);
                            HookUtil.setIntField(surface, "mCustomHeight", previousHeight);
                        }
                    });
                    installedHooks++;
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native image onMeasure unavailable", error);
                }
                try {
                    HookUtil.hook(nativeImageClass.getDeclaredMethod("getOriginalSize"), chain -> {
                        View surface = (View) chain.getThisObject();
                        View root = ancestorFolder(surface);
                        if (root == null || !eligible(root)) return chain.proceed();
                        int[] frame = visualFrame(root);
                        NativeFourFolderSizing.Size size = frame == null ? null : nativeSizing(root, frame);
                        return size == null ? chain.proceed()
                                : new int[]{size.backgroundWidth, size.backgroundHeight};
                    });
                    installedHooks++;
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native original image size unavailable", error);
                }
            }
            // Preview geometry is vendor-owned; hook each concrete preview type.
            // Nine-preview uses a 3x3 grid and must not inherit four-preview's
            // three-large + four-small parameters.
            java.util.Set<java.lang.reflect.Method> hookedMeasures = new java.util.HashSet<>();
            java.util.Set<java.lang.reflect.Method> hookedPreMeasures = new java.util.HashSet<>();
            for (String previewName : new String[]{PREVIEW_FOUR, PREVIEW_NINE}) {
                Class<?> previewType = vendorClass(loader, previewName);
                if (previewType == null) continue;
                try {
                    java.lang.reflect.Method pre = HookUtil.findMethodExact(
                            previewType, "preMeasure2x2",
                            new Class<?>[]{int.class, int.class});
                    if (pre.getDeclaringClass().getName().startsWith("com.miui.home.")
                            && hookedPreMeasures.add(pre)) {
                        HookUtil.hook(pre, chain -> {
                            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                            View preview = (View) chain.getThisObject();
                            if (!nativePreviewClass(preview)) return result;
                            View root = ancestorFolder(preview);
                            if (root == null || !eligible(root)) return result;
                            int[] frame = visualFrame(root);
                            if (frame == null) return result;
                            NativeFourFolderSizing.Size size = nativeSizing(root, frame);
                            if (size == null) return result;
                            preview.setPadding(size.insetX, size.insetY, size.insetX, size.insetY);
                            if (PREVIEW_FOUR.equals(preview.getClass().getName())) {
                                applyFourPreviewGeometry(preview, size);
                            } else {
                                applyNinePreviewGeometry(preview, size);
                            }
                            return result;
                        });
                        installedHooks++;
                    }
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native preview geometry unavailable " + previewName, error);
                }
                try {
                    java.lang.reflect.Method measure = HookUtil.findMethodExact(
                            previewType, "onMeasure",
                            new Class<?>[]{int.class, int.class});
                    if (!measure.getDeclaringClass().getName().startsWith("com.miui.home.")) {
                        throw new NoSuchMethodException("vendor preview onMeasure absent");
                    }
                    if (hookedMeasures.add(measure)) {
                        HookUtil.hook(measure, chain -> {
                            Object instance = chain.getThisObject();
                            if (!(instance instanceof View)
                                    || !nativePreviewClass((View) instance)) {
                                return chain.proceed(chain.getArgs().toArray(new Object[0]));
                            }
                            View preview = (View) instance;
                            View root = ancestorFolder(preview);
                            if (root == null || !eligible(root)) {
                                return chain.proceed(chain.getArgs().toArray(new Object[0]));
                            }
                            int[] frame = visualFrame(root);
                            if (frame == null) return chain.proceed(chain.getArgs().toArray(new Object[0]));
                            Object result = chain.proceed(new Object[]{
                                    exact(frame[0]), exact(frame[1])});
                            forceMeasuredFrame(preview, frame, "preview");
                            return result;
                        });
                        installedHooks++;
                    }
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native preview onMeasure unavailable " + previewName, error);
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
                int[] frame = desiredFrame(folder);
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
                    return result;
                } finally {
                    if (previous == null) ROOT_FRAME.remove();
                    else ROOT_FRAME.set(previous);
                }
            });
                    installedHooks++;
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
                    frame = visualFrame(host);
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
                    Object result = chain.proceed(new Object[]{exact(frame[0]), exact(frame[1])});
                    // This parent onMeasure IS confirmed executing on the
                    // device. If a vendor subclass ignores the child hook
                    // (inlined/renamed in Launcher 4.50), reconcile measured
                    // dimensions here before FrameLayout performs layout.
                    reconcileNativeMeasuredChildren(child, frame);
                    return result;
                } finally {
                    if (previous == null) NATIVE_SIZE.remove();
                    else NATIVE_SIZE.set(previous);
                }
            });
                    installedHooks++;
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
                                && !expectedFolderClass(info).equals(old.getClass().getName());
                        boolean previouslyCustom = !marked && CUSTOM_SIZED.remove(old);
                        if (wrongClass || previouslyCustom) {
                            ViewParent p = old.getParent();
                            if (p == null || p == parent) {
                                if (p == parent) parent.removeView(old);
                                HookUtil.tryInvoke(info, "setBuddyIconView", (Object) null);
                            }
                        }
                    }
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
                    installedHooks++;
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " FolderIcon#fromXml unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " FolderIcon#fromXml class unavailable");
            }

            if (cell != null) {
                // OEM may leave the dragged FolderIcon root INVISIBLE after
                // reattaching it to the desktop. The label itself is VISIBLE,
                // so title-only invalidation cannot make it appear.
                try {
                    HookUtil.hook(cell.getDeclaredMethod("onDropCompleted"), chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object target = chain.getThisObject();
                        if (target instanceof ViewGroup) {
                            ViewGroup workspace = (ViewGroup) target;
                            for (int i = 0; i < workspace.getChildCount(); i++) {
                                View child = workspace.getChildAt(i);
                                if (!eligible(child)) continue;
                                if (child.getVisibility() == View.INVISIBLE) {
                                    // Restore only the settled marked folder(s)
                                    // hidden by this drop, not unrelated Views.
                                    child.postOnAnimation(() -> restoreDroppedFolder(child));
                                } else {
                                    queueTitleRestore(child);
                                }
                            }
                        }
                        return result;
                    });
                    installedHooks++;
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " CellLayout#onDropCompleted unavailable", error);
                }
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
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " CellLayout#onLayout unavailable on this launcher", error);
                }
            } else {
                Api101Bridge.log(TAG + " CellLayout#onLayout class unavailable");
            }
            installed = installedHooks > 0;
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

    private static void forceMeasuredFrame(View view, int[] expected, String layer) {
        if (view.getMeasuredWidth() == expected[0]
                && view.getMeasuredHeight() == expected[1]) return;
        HookUtil.InvocationResult<Object> adjusted = HookUtil.tryInvoke(
                view, "setMeasuredDimension", expected[0], expected[1]);
        if (!adjusted.succeeded() && WARNED.add(view)) {
            Api101Bridge.log(TAG + " failed to measure " + layer + ": " + adjusted.failure());
        }
    }

    /** Resolve only the OEM animation copy's explicit original FolderInfo link. */
    private static View workspaceSource(View folder) {
        if (folder == null) return null;
        ViewParent parent = folder.getParent();
        if (parent != null && "com.miui.home.launcher.CellLayout".equals(parent.getClass().getName())) {
            return folder;
        }
        try {
            Object originalInfo = HookUtil.getField(folder, "mCopiedInfo");
            Object buddy = originalInfo == null ? null
                    : HookUtil.tryInvoke(originalInfo, "getBuddyIconView").value();
            if (buddy instanceof View && buddy != folder && eligible((View) buddy)) {
                View source = (View) buddy;
                ViewParent sourceParent = source.getParent();
                if (sourceParent != null && "com.miui.home.launcher.CellLayout".equals(
                        sourceParent.getClass().getName())) return source;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static int[] visualFrame(View folder) {
        View source = workspaceSource(folder);
        return source == null ? null : desiredFrame(source);
    }

    /**
     * Weak workspace key -> primitive-only sizing snapshot. Never retain any
     * FolderIcon or CellLayout through a cache value.
     *
     * Cache invalidation follows GridConfig identity, pitch, icon-scale result,
     * orientation, layout direction, density and theme UI mode. Ordinary
     * onMeasure calls reuse the dimensions without scanning workspace items.
     */
    private static final WeakHashMap<ViewGroup, NativeFourFolderWorkspaceMetrics> ICON_METRICS =
            new WeakHashMap<>();

    private static final Set<ViewGroup> RETRIED_UNMEASURED =
            Collections.newSetFromMap(new WeakHashMap<>());

    /** A single post-layout retry handles initial binds before icons are measured. */
    private static void refreshUnmeasuredMetricsAfterLayout(ViewGroup workspace) {
        NativeFourFolderWorkspaceMetrics cached = ICON_METRICS.get(workspace);
        if (cached != null && !cached.sampledSibling
                && RETRIED_UNMEASURED.add(workspace)) {
            ICON_METRICS.remove(workspace);
        }
    }

    private static NativeFourFolderSizing.Size nativeSizing(View root, int[] frame) {
        root = workspaceSource(root);
        if (root == null || !(root.getParent() instanceof ViewGroup)) return null;
        try {
            ViewGroup workspace = (ViewGroup) root.getParent();
            Object grid = HookUtil.getField(workspace, "mGridConfig");
            if (grid == null) return null;
            int cellW = HookUtil.getIntField(workspace, "mCellWidth");
            int cellH = HookUtil.getIntField(workspace, "mCellHeight");
            if (cellW <= 0 || cellH <= 0) return null;
            int baseIcon = ((Number) HookUtil.requireInvoke(grid, "getIconSize")).intValue();
            int scaledIcon = Launcher450IconSizeHook.workspaceIconSize(baseIcon);
            android.content.res.Configuration config = root.getResources().getConfiguration();
            int orientation = config.orientation;
            int density = config.densityDpi;
            int uiMode = config.uiMode;
            int direction = root.getLayoutDirection();
            NativeFourFolderWorkspaceMetrics metrics = ICON_METRICS.get(workspace);
            if (metrics == null || !metrics.matches(grid, cellW, cellH, baseIcon,
                    scaledIcon, orientation, density, uiMode, direction)) {
                if (metrics != null) RETRIED_UNMEASURED.remove(workspace);
                int iconW = scaledIcon, iconH = scaledIcon;
                boolean sampledSibling = false;
                int iconId = root.getResources().getIdentifier(
                        "icon_icon", "id", "com.miui.home");
                // Scan once when relevant geometry changes. Never walk sibling
                // icons on every FolderIcon/ImageView/Preview measure pass.
                if (iconId != 0) {
                    for (int i = 0; i < workspace.getChildCount(); i++) {
                        View sibling = workspace.getChildAt(i);
                        if (sibling == root || sibling.getVisibility() != View.VISIBLE) continue;
                        String name = sibling.getClass().getName();
                        if (!name.endsWith(".ShortcutIcon")
                                && !name.endsWith(".ProgressShortcutIcon")) continue;
                        View image = sibling.findViewById(iconId);
                        if (image == null || image.getMeasuredWidth() <= 0
                                || image.getMeasuredHeight() <= 0) continue;
                        iconW = image.getMeasuredWidth();
                        iconH = image.getMeasuredHeight();
                        sampledSibling = true;
                        break;
                    }
                }
                if (iconW <= 0 || iconH <= 0) return null;
                metrics = new NativeFourFolderWorkspaceMetrics(grid, cellW, cellH, baseIcon,
                        scaledIcon, orientation, density, uiMode, direction, iconW, iconH,
                        sampledSibling);
                ICON_METRICS.put(workspace, metrics);
            }
            return NativeFourFolderSizing.calculate(frame[0], frame[1],
                    cellW, cellH, metrics.iconW, metrics.iconH);
        } catch (Throwable error) {
            if (WARNED.add(root)) Api101Bridge.log(TAG + " workspace icon bounds unavailable", error);
            return null;
        }
    }

    private static void applyFourPreviewGeometry(View preview, NativeFourFolderSizing.Size size) {
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeEdgeHor", size.edgeX);
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeEdgeVer", size.edgeY);
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeInnerHor", size.gapX);
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeInnerVer", size.gapY);
        HookUtil.setIntField(preview, "mLargeItemWith", size.largeWidth);
        HookUtil.setIntField(preview, "mLargeItemHeight", size.largeHeight);
        HookUtil.setIntField(preview, "mSmall2x2ItemMergeInner", size.smallGap);
        HookUtil.setIntField(preview, "mSmallItemWith", size.smallWidth);
        HookUtil.setIntField(preview, "mSmallItemHeight", size.smallHeight);
    }

    private static void applyNinePreviewGeometry(View preview, NativeFourFolderSizing.Size size) {
        NativeNineFolderSizing.Geometry nine = NativeNineFolderSizing.calculate(
                size.backgroundWidth, size.backgroundHeight);
        try {
        // These are the shared OEM geometry inputs. Let 2X2_9's own
        // preSetup2x2/onLayout2x2 select and lay out its nine native children.
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeEdgeHor", nine.edgeX);
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeEdgeVer", nine.edgeY);
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeInnerHor", nine.gapX);
        HookUtil.setIntField(preview, "mLarge2x2ItemMergeInnerVer", nine.gapY);
        HookUtil.setIntField(preview, "mLargeItemWith", nine.iconWidth);
        HookUtil.setIntField(preview, "mLargeItemHeight", nine.iconHeight);
        HookUtil.setIntField(preview, "mSmall2x2ItemMergeInner", nine.gapX);
        HookUtil.setIntField(preview, "mSmallItemWith", nine.iconWidth);
        HookUtil.setIntField(preview, "mSmallItemHeight", nine.iconHeight);
        } catch (Throwable error) {
            // The 2X2_9 implementation is vendor-specific. If this APK lacks
            // a shared geometry field, retain its original native layout.
            if (WARNED.add(preview)) {
                Api101Bridge.log(TAG + " nine-preview geometry unsupported", error);
            }
        }
    }

    private static void resizeNativePreviewChildren(View container, int width, int height) {
        if (!(container instanceof ViewGroup) || width <= 0 || height <= 0) return;
        NativeFourFolderSizing.Size size = nativeSizing(ancestorFolder(container), new int[]{width, height});
        if (size == null) return;
        ViewGroup group = (ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++) {
            View view = group.getChildAt(i);
            String name = view.getClass().getName();
            boolean iconSurface = name.endsWith(".LauncherFolder2x2IconImageView");
            boolean preview = PREVIEW_FOUR.equals(name) || PREVIEW_NINE.equals(name);
            if (!iconSurface && !preview) continue;
            ViewGroup.LayoutParams params = view.getLayoutParams();
            if (params == null) continue;
            if (preview) {
                params.width = width;
                params.height = height;
            } else {
                params.width = size.backgroundWidth;
                params.height = size.backgroundHeight;
                // Parent is already measuring or laying out these native
                // children. LayoutParams is a live object; do not call
                // setLayoutParams() here (which requests another layout and
                // can cause repeated measure passes during transitions).
            }
        }
    }

    private static void reconcileNativeMeasuredChildren(View container, int[] frame) {
        if (!(container instanceof ViewGroup) || frame[0] <= 0 || frame[1] <= 0) return;
        NativeFourFolderSizing.Size size = nativeSizing(ancestorFolder(container), frame);
        if (size == null) return;
        ViewGroup group = (ViewGroup) container;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            String name = child.getClass().getName();
            boolean preview = PREVIEW_FOUR.equals(name) || PREVIEW_NINE.equals(name);
            boolean image = name.endsWith(".LauncherFolder2x2IconImageView");
            if (!preview && !image) continue;
            View root = ancestorFolder(container);
            if (image && workspaceSource(root) != root
                    && (HookUtil.getIntField(child, "mCustomWidth") > 0
                    || HookUtil.getIntField(child, "mCustomHeight") > 0)) continue;
            int[] target = preview ? frame : new int[]{size.backgroundWidth, size.backgroundHeight};
            if (child.getMeasuredWidth() == target[0]
                    && child.getMeasuredHeight() == target[1]) continue;
            if (preview) {
                // Re-enter OEM preview onMeasure with the correct width
                // so its own preMeasure2x2/preSetup2x2 recompute icon
                // positions. Do not manually draw or scale any preview.
                child.measure(exact(frame[0]), exact(frame[1]));
            }
            forceMeasuredFrame(child, target,
                    preview ? "fallback-preview" : "fallback-image");
        }
    }



    /**
     * Accept geometry only from the REAL workspace CellLayout. A folder
     * opening/drag transition can temporarily assign full-screen
     * LayoutParams (3008x1880 in the 2026-10-14 trace), which must NEVER
     * be mistaken for the 2x2 widget allocation (718x721).
     */
    private static int[] trustedCellFrame(View folder) {
        if (folder == null) return null;
        ViewParent parent = folder.getParent();
        if (!(parent instanceof ViewGroup)
                || !"com.miui.home.launcher.CellLayout".equals(
                        parent.getClass().getName())) {
            return null;
        }
        ViewGroup cell = (ViewGroup) parent;
        Object info = folder.getTag();
        if (info == null) return null;
        ViewGroup.LayoutParams lp = folder.getLayoutParams();
        if (lp == null) return null;
        try {
            if (HookUtil.getBooleanField(lp, "isDragging")) return null;
        } catch (Throwable ignored) {
            // Some vendor LayoutParams variants do not expose isDragging.
        }
        try {
            int[] xs = (int[]) HookUtil.getField(cell, "mXs");
            int[] ys = (int[]) HookUtil.getField(cell, "mYs");
            int cx = HookUtil.getIntField(info, "cellX");
            int cy = HookUtil.getIntField(info, "cellY");
            int cw = HookUtil.getIntField(cell, "mCellWidth");
            int ch = HookUtil.getIntField(cell, "mCellHeight");
            if (xs == null || ys == null || cx < 0 || cy < 0
                    || cx + 1 >= xs.length || cy + 1 >= ys.length
                    || cw <= 0 || ch <= 0) return null;
            int[] allocation = {xs[cx], ys[cy],
                    xs[cx + 1] + cw - xs[cx],
                    ys[cy + 1] + ch - ys[cy]};
            int[] frame = allocation;
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins =
                        (ViewGroup.MarginLayoutParams) lp;
                frame = WidgetGridSizing.centeredFrame(allocation,
                        margins.leftMargin, margins.topMargin,
                        margins.rightMargin, margins.bottomMargin);
            }
            if (frame[2] <= 0 || frame[3] <= 0) return null;
            // The two-cell rectangle must fit the actual desktop grid.
            // A transient drag/fullscreen host is not a valid cell frame.
            if (frame[0] < 0 || frame[1] < 0
                    || frame[0] + frame[2] > cell.getWidth()
                    || frame[1] + frame[3] > cell.getHeight()) {
                // Parent may not have completed its first layout yet.
                // Defer correction to CellLayout.onLayout instead of
                // falling back to a screen-sized incoming MeasureSpec.
                return null;
            }
            return frame;
        } catch (Throwable error) {
            if (WARNED.add(folder)) {
                Api101Bridge.log(TAG + " trusted CellLayout frame unavailable: " + error);
            }
            return null;
        }
    }

    private static int[] desiredFrame(View folder) {
        int[] frame = trustedCellFrame(folder);
        return frame == null ? null : new int[]{frame[2], frame[3]};
    }

    private static boolean eligible(View folder) {
        if (!nativeFolderClass(folder) || !isFourGridInfo(folder.getTag())) return false;
        int type = HookUtil.getIntField(folder.getTag(), "itemType");
        if (type == LargeFolderFootprintPolicy.FOUR
                && !NATIVE_FOUR.equals(folder.getClass().getName())) return false;
        if (type == LargeFolderFootprintPolicy.NINE
                && !NATIVE_NINE.equals(folder.getClass().getName())) return false;
        return LauncherLargeFolderPickerHook.isMarked(
                folder.getContext(), folder.getTag());
    }

    private static String expectedFolderClass(Object info) {
        return HookUtil.getIntField(info, "itemType") == LargeFolderFootprintPolicy.NINE
                ? NATIVE_NINE : NATIVE_FOUR;
    }

    private static View ancestorFolder(View child) {
        ViewParent parent = child.getParent();
        for (int n = 0; parent instanceof View && n < 8; n++) {
            View v = (View) parent;
            if (nativeFolderClass(v)) return v;
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
        // Measurement, final layout and the drawable children MUST use
        // one identical CellLayout-derived rectangle. No LayoutParams
        // fallback: those can become fullscreen during folder animations.
        if (folder.getParent() != workspace) return;
        refreshUnmeasuredMetricsAfterLayout(workspace);
        int[] frame = trustedCellFrame(folder);
        if (frame == null) return;
        ViewGroup.LayoutParams lp = folder.getLayoutParams();
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
        layoutFolderTitle(folder, nativeSizing(folder, new int[]{w, h}), w, h);
        // Native drag/drop can change title visibility AFTER CellLayout.onLayout.
        // Reassert only after the folder has settled back into its real cell.
        queueTitleRestore(folder);
    }

    private static void restoreDroppedFolder(View folder) {
        if (!eligible(folder) || !folder.isAttachedToWindow()) return;
        int[] frame = trustedCellFrame(folder);
        if (frame == null) return; // Still dragging or transiently reparented.
        if (folder.getVisibility() != View.INVISIBLE) return;
        folder.setVisibility(View.VISIBLE);
        NativeFourFolderSizing.Size sizing =
                nativeSizing(folder, new int[]{frame[2], frame[3]});
        layoutFolderTitle(folder, sizing, frame[2], frame[3]);
        folder.postInvalidateOnAnimation();
    }

    private static final Set<View> TITLE_RESTORE_PENDING =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static void queueTitleRestore(View folder) {
        if (!TITLE_RESTORE_PENDING.add(folder)) return;
        folder.postOnAnimation(() -> restoreTitleAfterPlacement(folder, 0));
    }

    private static void restoreTitleAfterPlacement(View folder, int attempts) {
        if (!folder.isAttachedToWindow() || !eligible(folder)) {
            TITLE_RESTORE_PENDING.remove(folder);
            return;
        }
        int[] frame = trustedCellFrame(folder);
        if (frame == null) {
            if (attempts < 12) {
                folder.postOnAnimation(() -> restoreTitleAfterPlacement(folder, attempts + 1));
            } else {
                TITLE_RESTORE_PENDING.remove(folder);
            }
            return;
        }
        TITLE_RESTORE_PENDING.remove(folder);
        NativeFourFolderSizing.Size size =
                nativeSizing(folder, new int[]{frame[2], frame[3]});
        layoutFolderTitle(folder, size, frame[2], frame[3]);
        folder.postInvalidateOnAnimation();
    }

    private static void layoutFolderTitle(View folder, NativeFourFolderSizing.Size size, int width, int height) {
        if (size == null) return;
        Object value = HookUtil.tryInvoke(folder, "getTitleContainer").value();
        if (!(value instanceof View)) return;
        View title = (View) value;
        // GONE is also used temporarily by native drag and rebind logic.
        // It is not evidence of an explicit no-title preference. Honor the
        // model's persistent title-hide flag rather than a stale view state.
        boolean modelHidden = Boolean.TRUE.equals(HookUtil.tryInvoke(
                folder.getTag(), "needHideTitleByFlags").value());
        boolean show = LargeFolderTitlePolicy.shouldShow(true,
                LauncherLargeFolderPickerHook.shouldShowTitle(folder.getContext(), folder.getTag()),
                modelHidden);
        if (!show) {
            restoreNativeTitleDrawing(folder, false);
            if (title.getVisibility() != View.INVISIBLE) title.setVisibility(View.INVISIBLE);
            return;
        }
        int top = size.insetY + size.backgroundHeight;
        int available = height - top;
        if (available <= 0) return;
        // ItemIcon.drawChild() can skip the title entirely while its internal
        // mIsHideTitle/mDrawOutline flags remain set after a drag, regardless
        // of the title View's VISIBLE state. Clear those transient draw gates
        // only for an attached, settled, explicitly selected real folder.
        restoreNativeTitleDrawing(folder, true);
        if (title.getVisibility() != View.VISIBLE) title.setVisibility(View.VISIBLE);
        if (title.getMeasuredWidth() != size.backgroundWidth
                || title.getMeasuredHeight() > available
                || title.getMeasuredHeight() == 0) {
            title.measure(exact(size.backgroundWidth), View.MeasureSpec.makeMeasureSpec(available,
                    View.MeasureSpec.AT_MOST));
        }
        int left = (width - size.backgroundWidth) / 2;
        if (title.getLeft() != left || title.getTop() != top
                || title.getWidth() != size.backgroundWidth
                || title.getHeight() != title.getMeasuredHeight()) {
            title.layout(left, top, left + size.backgroundWidth, top + title.getMeasuredHeight());
        }
    }

    private static void restoreNativeTitleDrawing(View folder, boolean show) {
        boolean changed = false;
        HookUtil.InvocationResult<Object> hidden =
                HookUtil.tryInvoke(folder, "getIsHideTitle");
        if (hidden.succeeded() && hidden.value() instanceof Boolean
                && ((Boolean) hidden.value()) == show) {
            changed |= HookUtil.tryInvoke(folder, "setIsHideTitle", !show).succeeded();
        }
        if (show) {
            HookUtil.InvocationResult<Object> outlined =
                    HookUtil.tryInvoke(folder, "isDrawOutline");
            if (outlined.succeeded() && Boolean.TRUE.equals(outlined.value())) {
                changed |= HookUtil.tryInvoke(folder, "setDrawOutline", false).succeeded();
            }
        }
        // The vendor setters only change draw flags; they do not invalidate.
        // No repeated work is required once the drawing state is correct.
        if (show) {
            Object container = HookUtil.tryInvoke(folder, "getTitleContainer").value();
            if (container instanceof View) {
                View title = (View) container;
                if (title.getAlpha() == 0f) {
                    title.setAlpha(1f);
                    changed = true;
                }
            }
            // ItemIcon.setTextAlpha changes mTitleView, not mTitleContainer.
            // A dragged folder may therefore have a VISIBLE parent but a
            // fully transparent title even after the container is restored.
            Object text = HookUtil.tryInvoke(folder, "getTitleView").value();
            if (text instanceof View && ((View) text).getAlpha() == 0f) {
                changed |= HookUtil.tryInvoke(folder, "setTextAlpha", 1f).succeeded();
            }
        }
        if (changed) folder.invalidate();
    }

    private static int exact(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }
}
