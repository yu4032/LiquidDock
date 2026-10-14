package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Binds the fourth folder style to a NEW layout tree, not to MIUI's
 * FolderIcon2x2_4 preview layout and not to an upscaled FolderIcon1x1.
 *
 * Its original FolderIcon stays as a native workspace drag/folder-open host.
 * CellLayout is the authority for its real 2x2 geometry and drag footprint.
 */
final class LauncherFourGridLayoutHook {
    private static final String TAG = "LiquidDockFolder4";
    private static final int FOUR_TYPE = 21;
    private static final Map<ViewGroup, WeakReference<LauncherFourGridContentView>> CONTENTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, Integer> COVERED_VISIBILITY =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private LauncherFourGridLayoutHook() {}

    static boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        try {
            Class<?> cellLayout = Class.forName(
                    "com.miui.home.launcher.CellLayout", false, loader);
            HookUtil.hook(cellLayout.getDeclaredMethod("onLayout",
                    boolean.class, int.class, int.class, int.class, int.class), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup) {
                    try {
                        inspect((ViewGroup) owner);
                    } catch (Throwable error) {
                        Api101Bridge.log(TAG + "four-grid-attach-failed", error);
                    }
                }
                return result;
            });
            try {
                Class<?> info = Class.forName(
                        "com.miui.home.launcher.FolderInfo", false, loader);
                HookUtil.hook(info.getDeclaredMethod("notifyDataSetChanged"), chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object changed = chain.getThisObject();
                    HookUtil.InvocationResult<Object> bound =
                            HookUtil.tryInvoke(changed, "getBuddyIconView");
                    if (bound.succeeded() && bound.value() instanceof ViewGroup) {
                        ViewGroup root = (ViewGroup) bound.value();
                        LauncherFourGridContentView content = lookup(root);
                        if (content != null) {
                            root.post(() -> content.bind(root, changed, true));
                        }
                    }
                    return result;
                });
            } catch (Throwable error) {
                Api101Bridge.log(TAG + "four-grid-data-refresh-unavailable " + error.getClass().getSimpleName());
            }
            installed = true;
            Api101Bridge.log(TAG + "four-grid-independent-layout-installed");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + "four-grid-layout-install-failed", error);
            return false;
        }
    }

    private static LauncherFourGridContentView lookup(ViewGroup folder) {
        WeakReference<LauncherFourGridContentView> ref = CONTENTS.get(folder);
        return ref == null ? null : ref.get();
    }

    private static void inspect(ViewGroup cell) {
        int childCount = cell.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View root = cell.getChildAt(i);
            if (!(root instanceof ViewGroup)) continue;
            Object info = root.getTag();
            ViewGroup nativeFolder = (ViewGroup) root;
            LauncherFourGridContentView previous = lookup(nativeFolder);
            if (eligible(root, info)) {
                attach(nativeFolder, info);
            } else if (previous != null) {
                CONTENTS.remove(nativeFolder);
                nativeFolder.removeView(previous);
                restoreOriginal(nativeFolder);
                Api101Bridge.log(TAG + "four-grid-restored-native-layout");
            }
        }
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

    private static boolean eligible(View root, Object info) {
        if (info == null) return false;
        String name = root.getClass().getName();
        if (!name.contains("FolderIcon")) return false;
        try {
            return isFourGridInfo(info)
                    && LauncherLargeFolderPickerHook.isMarked(root.getContext(), info);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void attach(ViewGroup folder, Object info) {
        LauncherFourGridContentView content = lookup(folder);
        if (content == null) {
            content = new LauncherFourGridContentView(folder.getContext());
            ViewGroup.LayoutParams lp = createParams(folder);
            // Native title remains on the existing FolderIcon. The custom
            // content fills the same full-size FolderIcon host but reserves
            // the native title band in its own 2x2 measurement.
            folder.addView(content, lp);
            CONTENTS.put(folder, new WeakReference<>(content));
            hideOriginal(folder);
            Api101Bridge.log(TAG + "four-grid-attached root=" + folder.getClass().getName()
                    + " rootSize=" + folder.getWidth() + "x" + folder.getHeight()
                    + " content=" + content.getClass().getSimpleName());
        }
        content.bind(folder, info, false);
        // CellLayout has just laid out the native drag host. Independently
        // measure the new content to the host's ACTUAL frame now; a small
        // wrap_content native icon subtree must not constrain our 2x2 view.
        // This also works when the optional HomeGrid feature is disabled.
        int width = folder.getWidth(), height = folder.getHeight();
        if (width > 0 && height > 0 && (content.getWidth() != width
                || content.getHeight() != height
                || content.getMeasuredWidth() != width
                || content.getMeasuredHeight() != height)) {
            content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            content.layout(0, 0, width, height);
            Api101Bridge.log(TAG + " four-grid-content-frame=" + width + "x" + height);
        }
    }

    private static ViewGroup.LayoutParams createParams(ViewGroup folder) {
        if (folder instanceof RelativeLayout) {
            RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            p.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            p.addRule(RelativeLayout.CENTER_HORIZONTAL);
            return p;
        }
        if (folder instanceof FrameLayout) {
            return new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        return new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static void hideOriginal(ViewGroup folder) {
        Object target = field(folder, "mIconContainer");
        if (target instanceof View) {
            View iconContainer = (View) target;
            COVERED_VISIBILITY.put(iconContainer, iconContainer.getVisibility());
            iconContainer.setVisibility(View.INVISIBLE);
        } else {
            // The native field may be renamed: still show the independent
            // background and icons, never mutate an unknown view hierarchy.
            Api101Bridge.log(TAG + "four-grid-native-icon-container-not-found");
        }
    }

    private static void restoreOriginal(ViewGroup folder) {
        Object target = field(folder, "mIconContainer");
        if (!(target instanceof View)) return;
        View iconContainer = (View) target;
        Integer previous = COVERED_VISIBILITY.remove(iconContainer);
        if (previous != null) iconContainer.setVisibility(previous);
    }

    private static Object field(Object target, String name) {
        try { return HookUtil.getField(target, name); }
        catch (Throwable ignored) { return null; }
    }
}
