package com.hellovoid.liquiddock;

import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.reflect.Method;

/**
 * Temporary, debug-only geometry observability for the OS 3 Launcher 4.50
 * small-folder scaling regression. No size, View, Drawable or blur state writes.
 */
final class Launcher450FolderSizeDiagnostic {
    private static final String TAG = "[DC][FolderSizeTrace]";
    private static final String SMALL_FOLDER =
            "com.miui.home.launcher.folder.FolderIcon1x1";
    private static boolean installed;

    private Launcher450FolderSizeDiagnostic() {}

    static void install(ClassLoader loader) {
        if (installed || loader == null) return;
        try {
            Class<?> small = Class.forName(SMALL_FOLDER, false, loader);
            Class<?> folderIcon = Class.forName(
                    "com.miui.home.launcher.FolderIcon", false, loader);
            Class<?> itemIcon = Class.forName(
                    "com.miui.home.launcher.ItemIcon", false, loader);
            Class<?> folder = Class.forName(
                    "com.miui.home.launcher.Folder", false, loader);
            Class<?> blurUtilities = Class.forName(
                    "com.miui.home.launcher.common.BlurUtilities", false, loader);

            Method measure = HookUtil.findMethodExact(
                    small, "onMeasure", new Class<?>[]{int.class, int.class});
            HookUtil.hook(measure, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View) {
                    snapshot("measured", (View) chain.getThisObject());
                }
                return result;
            });

            Method layout = HookUtil.findMethodExact(folderIcon, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            HookUtil.hook(layout, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View
                        && SMALL_FOLDER.equals(chain.getThisObject().getClass().getName())) {
                    snapshot("layout-blur", (View) chain.getThisObject());
                }
                return result;
            });

            Method iconRebind = HookUtil.findMethodExact(
                    itemIcon, "setIconImageView",
                    new Class<?>[]{Drawable.class, android.graphics.Bitmap.class});
            HookUtil.hook(iconRebind, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getThisObject() instanceof View
                        && SMALL_FOLDER.equals(chain.getThisObject().getClass().getName())) {
                    snapshot("drawable-rebind", (View) chain.getThisObject());
                }
                return result;
            });

            Class<?>[] vendorArgs = new Class<?>[]{
                    View.class, int.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, int.class, int.class, int.class};
            Method vendorBlur = HookUtil.findMethodExact(
                    blurUtilities, "setFolderIconBlur", vendorArgs);
            HookUtil.hook(vendorBlur, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (chain.getArgs().size() > 0 && chain.getArgs().get(0) instanceof View) {
                    View nativeView = (View) chain.getArgs().get(0);
                    ViewParent p = nativeView.getParent();
                    while (p instanceof View) {
                        if (SMALL_FOLDER.equals(p.getClass().getName())) {
                            snapshot("vendor-blur-write", (View) p);
                            break;
                        }
                        p = p.getParent();
                    }
                }
                return result;
            });

            Method close = HookUtil.findMethodExact(
                    folder, "onCloseAnimationFinished", new Class<?>[0]);
            HookUtil.hook(close, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (Api101Bridge.isDebugLoggingEnabled()) {
                    HookUtil.InvocationResult<Object> info =
                            HookUtil.tryInvoke(chain.getThisObject(), "getInfo");
                    if (info.succeeded() && info.value() != null) {
                        HookUtil.InvocationResult<Object> buddy =
                                HookUtil.tryInvoke(info.value(), "getBuddyIconView");
                        if (buddy.succeeded() && buddy.value() instanceof View) {
                            snapshot("close-finished", (View) buddy.value());
                        }
                    }
                }
                return result;
            });
            installed = true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook installation unavailable: " + error);
        }
    }

    private static void snapshot(String phase, View folder) {
        if (!Api101Bridge.isDebugLoggingEnabled()
                || !SMALL_FOLDER.equals(folder.getClass().getName())) return;
        try {
            View image = viewResult(folder, "getIconImageView");
            View container = viewResult(folder, "getIconContainer");
            View cover = viewResult(folder, "getCover");
            View preview = viewResult(folder, "getPreviewContainer");
            View inner = container instanceof ViewGroup
                    && ((ViewGroup) container).getChildCount() > 0
                    ? ((ViewGroup) container).getChildAt(0) : null;
            String parent = folder.getParent() != null
                    ? folder.getParent().getClass().getSimpleName() : "null";
            StringBuilder result = new StringBuilder(TAG)
                    .append(" phase=").append(phase)
                    .append(" owner=").append(Integer.toHexString(System.identityHashCode(folder)))
                    .append(" parent=").append(parent)
                    .append(" workspace=").append(LauncherGlassHierarchy.isWorkspace(folder))
                    .append(" attached=").append(folder.isAttachedToWindow())
                    .append(" root=").append(viewInfo(folder))
                    .append(" outer=").append(viewInfo(container))
                    .append(" inner=").append(viewInfo(inner))
                    .append(" blurTarget=").append(viewInfo(image))
                    .append(" preview=").append(viewInfo(preview))
                    .append(" cover=").append(viewInfo(cover));
            if (image != null) {
                Outline outline = new Outline();
                image.getOutline(outline);
                Rect rect = new Rect();
                result.append(" outlineRect=")
                        .append(outline.getRect(rect) ? rect.toShortString() : "nonRect")
                        .append(" outlineRadius=").append(outline.getRadius())
                        .append(" clipToOutline=").append(image.getClipToOutline());
                if (image instanceof ImageView) {
                    Drawable drawable = ((ImageView) image).getDrawable();
                    if (drawable != null) {
                        result.append(" drawable=")
                                .append(drawable.getClass().getSimpleName())
                                .append(" intrinsic=").append(drawable.getIntrinsicWidth())
                                .append("x").append(drawable.getIntrinsicHeight())
                                .append(" bounds=").append(drawable.getBounds().toShortString())
                                .append(" alpha=").append(drawable.getAlpha());
                    }
                }
            }
            MainHook.log(result.toString());
        } catch (Throwable error) {
            MainHook.log(TAG + " phase=" + phase + " read failed: " + error);
        }
    }

    private static View viewResult(Object owner, String method) {
        HookUtil.InvocationResult<Object> result = HookUtil.tryInvoke(owner, method);
        return result.succeeded() && result.value() instanceof View ? (View) result.value() : null;
    }

    private static String viewInfo(View v) {
        if (v == null) return "null";
        ViewGroup.LayoutParams p = v.getLayoutParams();
        return v.getClass().getSimpleName() + "(" + v.getMeasuredWidth() + "x"
                + v.getMeasuredHeight() + "/" + v.getWidth() + "x" + v.getHeight()
                + " lp=" + (p != null ? p.width + "x" + p.height : "null")
                + " scale=" + v.getScaleX() + "," + v.getScaleY()
                + " visibility=" + v.getVisibility() + ")";
    }
}
