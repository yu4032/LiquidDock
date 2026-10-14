package com.hellovoid.liquiddock;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Read-only, debug-gated lifecycle trace for the 4.50 small-folder scaling regression.
 * Logs geometry only on changes, and the semantic open/close/drawable boundaries.
 * Never alters native blur state, clip flags, View sizes or animation timing.
 */
final class Launcher450SmallFolderTrace {
    private static final String TAG = "[DC][FolderSizeTrace]";
    private static final String SMALL_FOLDER = "com.miui.home.launcher.folder.FolderIcon1x1";
    private static final Map<View, Map<String, String>> LAST =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private Launcher450SmallFolderTrace() {}

    static synchronized void install(ClassLoader loader) {
        if (installed || loader == null) return;
        installed = true;
        hookLifecycle(loader);
        hookBackgroundBinds(loader);
        hookNativeClip(loader);
    }

    static void measureStart(View folder, boolean enabled, int percent, boolean workspace) {
        if (!MainHook.debugLogging) return;
        sample("measure-before sizeOn=" + enabled + " pct=" + percent
                + " workspace=" + workspace, folder, "measure-before", false);
    }

    static void measureEnd(View folder, boolean enabled, int percent, boolean workspace) {
        if (!MainHook.debugLogging) return;
        sample("measure-after sizeOn=" + enabled + " pct=" + percent
                + " workspace=" + workspace, folder, "measure-after", false);
    }

    private static void hookLifecycle(ClassLoader loader) {
        try {
            Class<?> folderIcon = Class.forName("com.miui.home.launcher.FolderIcon", false, loader);
            Method layout = HookUtil.findMethodExact(folderIcon, "onLayout",
                    new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class});
            HookUtil.hook(layout, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.debugLogging && chain.getThisObject() instanceof View) {
                    View icon = (View) chain.getThisObject();
                    if (isSmallFolder(icon)) sample("layout", icon, "layout", false);
                }
                return result;
            });
            for (String methodName : new String[]{"onOpen", "onClose"}) {
                Method method = HookUtil.findMethodExact(folderIcon, methodName, new Class<?>[0]);
                HookUtil.hook(method, chain -> {
                    Object object = chain.getThisObject();
                    if (MainHook.debugLogging && object instanceof View && isSmallFolder((View) object)) {
                        sample(methodName + "-before", (View) object, methodName, true);
                    }
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (MainHook.debugLogging && object instanceof View && isSmallFolder((View) object)) {
                        sample(methodName + "-after", (View) object, methodName, true);
                    }
                    return result;
                });
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " folder lifecycle hooks unavailable: " + error);
        }
        try {
            Class<?> folder = Class.forName("com.miui.home.launcher.Folder", false, loader);
            Method finish = HookUtil.findMethodExact(folder, "onCloseAnimationFinished",
                    new Class<?>[0]);
            HookUtil.hook(finish, chain -> {
                View icon = resolveBuddyIcon(chain.getThisObject());
                if (MainHook.debugLogging && icon != null) {
                    sample("close-animation-finish-before", icon, "close", true);
                }
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (MainHook.debugLogging && icon != null) {
                    sample("close-animation-finish-after", icon, "close", true);
                    // One observation after the vendor's layout is committed; not a repair.
                    icon.postOnAnimation(() -> {
                        if (MainHook.debugLogging && icon.isAttachedToWindow()) {
                            sample("close-next-frame", icon, "close-next-frame", true);
                        }
                    });
                }
                return result;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " folder close completion hook unavailable: " + error);
        }
    }

    private static void hookBackgroundBinds(ClassLoader loader) {
        try {
            Class<?> itemIcon = Class.forName("com.miui.home.launcher.ItemIcon", false, loader);
            Method imageBind = HookUtil.findMethodExact(itemIcon, "setIconImageView",
                    new Class<?>[]{Drawable.class, Bitmap.class});
            HookUtil.hook(imageBind, chain -> {
                View icon = chain.getThisObject() instanceof View
                        ? (View) chain.getThisObject() : null;
                boolean trace = MainHook.debugLogging && isSmallFolder(icon);
                if (trace) sample("drawable-bind-before", icon, "bind", true);
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (trace) sample("drawable-bind-after", icon, "bind", true);
                return result;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " drawable rebind hook unavailable: " + error);
        }
        try {
            Class<?> small = Class.forName(SMALL_FOLDER, false, loader);
            Method refresh = HookUtil.findMethodExact(small, "refreshFolderBackground",
                    new Class<?>[0]);
            HookUtil.hook(refresh, chain -> {
                View icon = chain.getThisObject() instanceof View
                        ? (View) chain.getThisObject() : null;
                if (MainHook.debugLogging && icon != null) {
                    sample("refresh-background-request", icon, "refresh", true);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " background refresh hook unavailable: " + error);
        }
    }

    private static void hookNativeClip(ClassLoader loader) {
        try {
            Class<?> blur = Class.forName(
                    "com.miui.home.launcher.common.BlurUtilities", false, loader);
            Class<?>[] types = new Class<?>[11];
            types[0] = View.class;
            for (int i = 1; i < types.length; i++) types[i] = int.class;
            Method setter = HookUtil.findMethodExact(blur, "setFolderIconBlur", types);
            HookUtil.hook(setter, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                View material = args.length == 11 && args[0] instanceof View
                        ? (View) args[0] : null;
                View icon = enclosingSmallFolder(material);
                boolean trace = MainHook.debugLogging && icon != null;
                Object result = chain.proceed(args);
                if (trace) {
                    String clip = "radius=" + args[1] + " mode=" + args[10]
                            + " nativeTarget=" + label(material)
                            + " measured=" + metric(material)
                            + " pad=" + padding(material)
                            + " expectedClip=["
                            + material.getPaddingLeft() + "," + material.getPaddingTop()
                            + "," + (material.getMeasuredWidth() - material.getPaddingRight())
                            + "," + (material.getMeasuredHeight() - material.getPaddingBottom()) + "]";
                    logChanged("vendor-blur", icon, clip, "vendor-setFolderIconBlur " + clip);
                }
                return result;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " native folder clip hook unavailable: " + error);
        }
    }

    private static View resolveBuddyIcon(Object folder) {
        if (folder == null) return null;
        try {
            HookUtil.InvocationResult<Object> info = HookUtil.tryInvoke(folder, "getInfo");
            Object icon = info.succeeded() && info.value() != null
                    ? HookUtil.getField(info.value(), "icon") : null;
            return icon instanceof View && isSmallFolder((View) icon) ? (View) icon : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isSmallFolder(View view) {
        return view != null && SMALL_FOLDER.equals(view.getClass().getName());
    }

    private static View enclosingSmallFolder(View view) {
        for (View cursor = view; cursor != null;) {
            if (isSmallFolder(cursor)) return cursor;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static void sample(String event, View folder, String key, boolean force) {
        if (folder == null || !MainHook.debugLogging) return;
        String metrics = "icon=" + label(folder)
                + " parent=" + (folder.getParent() instanceof View
                    ? label((View) folder.getParent()) : String.valueOf(folder.getParent()))
                + " attached=" + folder.isAttachedToWindow()
                + " glassSmall=" + GlassRuntimeState.isSmallFolderEnabled()
                + " host=" + metric(folder);
        Object root = HookUtil.tryInvoke(folder, "getIconContainer").value();
        if (root instanceof View) {
            View outer = (View) root;
            metrics += " outer=" + metric(outer) + " clip=" + clipping(outer);
            if (outer instanceof ViewGroup && ((ViewGroup) outer).getChildCount() > 0) {
                View inner = ((ViewGroup) outer).getChildAt(0);
                metrics += " inner=" + metric(inner) + " clip=" + clipping(inner);
            }
        }
        Object bg = HookUtil.tryInvoke(folder, "getIconImageView").value();
        Object cover = HookUtil.tryInvoke(folder, "getCover").value();
        Object preview = HookUtil.tryInvoke(folder, "getPreviewContainer").value();
        if (bg instanceof View) metrics += " bg=" + metric((View) bg) + drawable((View) bg);
        if (cover instanceof View) metrics += " cover=" + metric((View) cover) + drawable((View) cover);
        if (preview instanceof View) metrics += " preview=" + metric((View) preview);
        if (force) {
            MainHook.log(TAG + " " + event + " " + metrics);
        } else {
            logChanged(key, folder, metrics, event + " " + metrics);
        }
    }

    private static void logChanged(String key, View icon, String state, String message) {
        if (!MainHook.debugLogging || icon == null) return;
        synchronized (LAST) {
            Map<String, String> last = LAST.get(icon);
            if (last == null) {
                last = new java.util.HashMap<>();
                LAST.put(icon, last);
            }
            if (state.equals(last.put(key, state))) return;
        }
        MainHook.log(TAG + " " + message);
    }

    private static String label(View view) {
        return view.getClass().getSimpleName() + "@"
                + Integer.toHexString(System.identityHashCode(view));
    }

    private static String metric(View v) {
        if (v == null) return "-";
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        return label(v) + "{size=" + v.getWidth() + "x" + v.getHeight()
                + " measured=" + v.getMeasuredWidth() + "x" + v.getMeasuredHeight()
                + " lp=" + (lp != null ? lp.width + "x" + lp.height : "none")
                + " position=" + v.getLeft() + "," + v.getTop()
                + " scale=" + v.getScaleX() + "," + v.getScaleY()
                + " visible=" + v.getVisibility() + "}";
    }

    private static String padding(View v) {
        return v.getPaddingLeft() + "," + v.getPaddingTop() + ","
                + v.getPaddingRight() + "," + v.getPaddingBottom();
    }

    private static String clipping(View v) {
        return v instanceof ViewGroup
                ? ((ViewGroup) v).getClipChildren() + "/"
                    + ((ViewGroup) v).getClipToPadding() : "n/a";
    }

    private static String drawable(View v) {
        if (!(v instanceof ImageView)) return "";
        ImageView image = (ImageView) v;
        Drawable d = image.getDrawable();
        return d == null ? " drawable=null"
                : " drawable=" + d.getClass().getSimpleName() + "@"
                    + Integer.toHexString(System.identityHashCode(d))
                    + " intrinsic=" + d.getIntrinsicWidth() + "x" + d.getIntrinsicHeight()
                    + " bounds=" + d.getBounds().toShortString()
                    + " scaleType=" + image.getScaleType();
    }
}
