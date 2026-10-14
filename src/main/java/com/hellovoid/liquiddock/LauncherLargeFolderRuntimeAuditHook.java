package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/**
 * Temporary scoped runtime evidence for the four-large-icon folder feature.
 *
 * The CellLayout owns the real 2x2 reservation; unlike preview hooks, this
 * observer runs even when vendor FolderIcon methods have changed. It is
 * strictly read-only and samples only native four-preview itemType=21.
 * Never logs app names, titles, icon pixels, or other personal content.
 */
final class LauncherLargeFolderRuntimeAuditHook {
    private static final String TAG = "LiquidDockFolder4";
    private static final String FOUR_ICON_CLASS =
            "com.miui.home.launcher.folder.FolderIcon2x2_4";
    private static final String NATIVE_CONTENT_CLASS =
            "com.miui.home.launcher.folder.LauncherFolder2x2IconContainer";
    private static final Map<Long, String> LAST_SAMPLE = new HashMap<>();
    private static int emitted;
    private static boolean installed;

    private LauncherLargeFolderRuntimeAuditHook() {}

    static boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        try {
            logVendorContract(loader, "com.miui.home.launcher.FolderIcon",
                    "fromXml", "createItemIcon", "getSpanX", "getSpanY",
                    "onMeasure", "setIconImageView");
            logVendorContract(loader, "com.miui.home.launcher.ItemIcon",
                    "onMeasure", "getSpanX");
            logVendorContract(loader, "com.miui.home.launcher.folder.FolderIcon2x2",
                    "onMeasure", "onFinishInflate", "getPreviewPosition",
                    "getPreviewItems", "getMPreviewContainer");
            logVendorContract(loader, "com.miui.home.launcher.folder.FolderIcon2x2_4",
                    "onMeasure", "onFinishInflate", "getPreviewPosition");
            logVendorContract(loader, "com.miui.home.launcher.folder.BaseFolderIconPreviewContainer2X2",
                    "onMeasure", "onMeasureChildCustom", "preSetup2x2");
            logVendorContract(loader, "com.miui.home.launcher.folder.FolderIconPreviewContainer2X2_4",
                    "onMeasure", "preSetup2x2", "onMeasureChild2x2");
            logVendorContract(loader, NATIVE_CONTENT_CLASS, "onMeasure");
            logVendorContract(loader, "com.miui.home.launcher.DeviceConfig",
                    "getMiuiWidgetSizeSpec", "getCellWidth");
            logVendorContract(loader, "com.miui.home.launcher.CellLayout",
                    "onLayout", "measureChild", "setupLayoutParam");
            Class<?> cell = Class.forName(
                    "com.miui.home.launcher.CellLayout", false, loader);
            HookUtil.hook(cell.getDeclaredMethod("onLayout",
                    boolean.class, int.class, int.class, int.class, int.class), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup) inspect((ViewGroup) owner);
                return result;
            });
            // Inspect every native binding overload in the ACTUAL device
            // class, rather than assuming the older public decompile's
            // exact 4-argument FolderIcon.fromXml signature.
            try {
                Class<?> folderIcon = Class.forName(
                        "com.miui.home.launcher.FolderIcon", false, loader);
                Class<?> folderInfo = Class.forName(
                        "com.miui.home.launcher.FolderInfo", false, loader);
                int variants = 0;
                for (java.lang.reflect.Method method : folderIcon.getDeclaredMethods()) {
                    if (!"fromXml".equals(method.getName())
                            || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                        continue;
                    }
                    int infoIndex = -1;
                    Class<?>[] sig = method.getParameterTypes();
                    for (int n = 0; n < sig.length; n++) {
                        if (folderInfo.isAssignableFrom(sig[n])) {
                            infoIndex = n;
                            break;
                        }
                    }
                    if (infoIndex < 0) continue;
                    final int index = infoIndex;
                    HookUtil.hook(method, chain -> {
                        Object info = chain.getArg(index);
                        boolean marked = isMarkedInfo(info, loader);
                        Object before = marked
                                ? HookUtil.tryInvoke(info, "getBuddyIconView").value() : null;
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (marked && emitted < 80) {
                            Api101Bridge.log(TAG + " bind signature=" + method.toGenericString()
                                    + " marked=" + marked
                                    + " type=" + getInt(info, "itemType")
                                    + " span=" + getInt(info, "spanX")
                                    + "x" + getInt(info, "spanY")
                                    + " cached=" + shortClass(before)
                                    + " result=" + shortClass(result)
                                    + " root=" + dims(result));
                            emitted++;
                        }
                        return result;
                    });
                    variants++;
                }
                Api101Bridge.log(TAG + " native fromXml audit variants=" + variants);
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " fromXml-audit-unavailable " + error);
            }
            // The actual Android measure pass of the vendor's inner host,
            // instead of the visual hook's speculation about its presence.
            try {
                Class<?> contentHost = Class.forName(
                        "com.miui.home.launcher.folder.LauncherFolder2x2IconContainer",
                        false, loader);
                HookUtil.hook(contentHost.getDeclaredMethod(
                        "onMeasure", int.class, int.class), chain -> {
                    Object view = chain.getThisObject();
                    View folder = view instanceof View
                            ? ancestorFolder((View) view) : null;
                    int width = View.MeasureSpec.getSize((Integer) chain.getArg(0));
                    int height = View.MeasureSpec.getSize((Integer) chain.getArg(1));
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (folder != null && getInt(folder.getTag(), "itemType") == 21
                            && emitted < 80) {
                        Api101Bridge.log(TAG + "content-measure span="
                                + getInt(folder.getTag(), "spanX") + "x"
                                + getInt(folder.getTag(), "spanY")
                                + " root=" + dims(folder)
                                + " incoming=" + width + "x" + height
                                + " nativeContent=" + dims(view));
                        emitted++;
                    }
                    return result;
                });
            } catch (Throwable error) {
                Api101Bridge.log(TAG + "content-audit-unavailable " + error.getClass().getSimpleName());
            }
            installed = true;
            Api101Bridge.log(TAG + "audit-installed CellLayout#onLayout; build=folder4");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + "audit-install-failed " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            return false;
        }
    }

    private static void logVendorContract(ClassLoader loader, String cls, String... names) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        try {
            Class<?> type = Class.forName(cls, false, loader);
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                for (String wanted : names) {
                    if (wanted.equals(method.getName())) {
                        Api101Bridge.log(TAG + " contract "
                                + method.toGenericString());
                        break;
                    }
                }
            }
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " class-contract unavailable " + cls + " " + error);
        }
    }

    private static boolean isMarkedInfo(Object info, ClassLoader loader) {
        if (info == null) return false;
        try {
            Class<?> application = Class.forName(
                    "com.miui.home.launcher.Application", false, loader);
            HookUtil.InvocationResult<Object> launcher =
                    HookUtil.tryInvokeStatic(application, "getLauncher");
            if (launcher.succeeded() && launcher.value() instanceof android.content.Context) {
                return LauncherLargeFolderPickerHook.isMarked(
                        (android.content.Context) launcher.value(), info);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static View ancestorFolder(View child) {
        android.view.ViewParent parent = child.getParent();
        for (int i = 0; i < 8 && parent instanceof View; i++) {
            View view = (View) parent;
            if (view.getClass().getName().contains("FolderIcon2x2")) return view;
            parent = view.getParent();
        }
        return null;
    }

    private static String shortClass(Object item) {
        return item == null ? "null" : item.getClass().getSimpleName();
    }

    private static int getInt(Object item, String name) {
        try { return HookUtil.getIntField(item, name); }
        catch (Throwable ignored) { return -1; }
    }

    private static void inspect(ViewGroup layout) {
        if (emitted >= 80) return;
        for (int i = 0; i < layout.getChildCount() && emitted < 24; i++) {
            View child = layout.getChildAt(i);
            if (child == null) continue;
            Object info = child.getTag();
            if (info == null) continue;
            try {
                int type = HookUtil.getIntField(info, "itemType");
                int spanX = HookUtil.getIntField(info, "spanX");
                int spanY = HookUtil.getIntField(info, "spanY");
                boolean marked = LauncherLargeFolderPickerHook.isMarked(
                        child.getContext(), info);
                if (!marked && !(type == 21 && spanX == 2 && spanY == 2)) continue;
                long id = HookUtil.getLongField(info, "id");
                View nativeHost = findDescendant(child, NATIVE_CONTENT_CLASS, 0);
                ViewGroup parent = child.getParent() instanceof ViewGroup
                        ? (ViewGroup) child.getParent() : null;
                Object icon = fieldOrNull(child, "mIconContainer");
                Object bg = fieldOrNull(child, "mIconImageView");
                Object preview = fieldOrNull(child, "mPreviewContainer");
                String sample = "folder=" + id
                        + " marked=" + marked
                        + " span=" + spanX + "x" + spanY
                        + " class=" + child.getClass().getSimpleName()
                        + " classMatch=" + FOUR_ICON_CLASS.equals(child.getClass().getName())
                        + " root=" + dims(child)
                        + " alloc=" + layoutParams(child)
                        + " cellParent=" + (parent == null ? "none" : parent.getClass().getSimpleName())
                        + " iconHost=" + dims(icon)
                        + " background=" + dims(bg)
                        + " nativeHost=" + dims(nativeHost)
                        + " preview=" + dims(preview);
                String prior;
                synchronized (LAST_SAMPLE) {
                    prior = LAST_SAMPLE.put(id, sample);
                    if (sample.equals(prior)) continue;
                    if (LAST_SAMPLE.size() > 32) LAST_SAMPLE.clear();
                    emitted++;
                }
                Api101Bridge.log(TAG + sample);
                if (marked) Api101Bridge.log(TAG + " tree=" + viewTree(child, 0));
                if (child.isAttachedToWindow() && (marked || spanX == 2 && spanY == 2)) {
                    child.post(() -> inspectAfterFrame(child, id));
                }
            } catch (Throwable ignored) {
                // Vendor layout pass must never fail due to diagnostic code.
            }
        }
    }

    private static void inspectAfterFrame(View folder, long id) {
        if (emitted >= 80 || folder == null || !folder.isAttachedToWindow()) return;
        Object host = findDescendant(folder, NATIVE_CONTENT_CLASS, 0);
        Object background = fieldOrNull(folder, "mIconImageView");
        Object preview = fieldOrNull(folder, "mPreviewContainer");
        Api101Bridge.log(TAG + "after-layout folder=" + id + " root=" + dims(folder)
                + " background=" + dims(background)
                + " nativeHost=" + dims(host)
                + " preview=" + dims(preview));
        emitted++;
    }

    private static String viewTree(View node, int depth) {
        if (node == null) return "null";
        StringBuilder result = new StringBuilder();
        result.append(node.getClass().getSimpleName()).append("(")
                .append(dims(node)).append(",xy=")
                .append(node.getLeft()).append(",").append(node.getTop())
                .append(",v=").append(node.getVisibility())
                .append(")");
        if (depth >= 5 || !(node instanceof ViewGroup)) return result.toString();
        ViewGroup children = (ViewGroup) node;
        int count = Math.min(12, children.getChildCount());
        for (int i = 0; i < count; i++) {
            result.append(">").append(viewTree(children.getChildAt(i), depth + 1));
        }
        return result.toString();
    }

    private static View findDescendant(View root, String className, int depth) {
        if (root == null || depth > 8) return null;
        if (className.equals(root.getClass().getName())) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findDescendant(group.getChildAt(i), className, depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static Object fieldOrNull(Object root, String name) {
        try { return HookUtil.getField(root, name); }
        catch (Throwable ignored) { return null; }
    }

    private static String dims(Object o) {
        if (!(o instanceof View)) return "missing";
        View view = (View) o;
        return view.getMeasuredWidth() + "x" + view.getMeasuredHeight()
                + "/" + view.getWidth() + "x" + view.getHeight();
    }

    private static String layoutParams(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        return lp == null ? "missing" : lp.width + "x" + lp.height;
    }
}
