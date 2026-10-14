package com.hellovoid.liquiddock;

import android.content.Context;
import android.view.View;

/**
 * Narrows the 2x2 size mapping to the native FolderInfo conversion of an
 * explicitly selected custom folder. Original MIUI folder styles preserve
 * their own model spans and layout policies.
 */
final class LauncherLargeFolderNativeSpanHook {
    private static final String TAG = "[DC][FolderFourSpan]";
    private static final int FOUR_TYPE = 21;
    private static final ThreadLocal<Boolean> FORCE_LARGE = new ThreadLocal<>();

    private static boolean installed;
    private static boolean enabled;

    private LauncherLargeFolderNativeSpanHook() {}

    static boolean install(ClassLoader loader) {
        if (loader == null) return false;
        if (installed) return enabled;
        try {
            Class<?> controller = Class.forName(
                    "com.miui.home.launcher.convertsize.FolderIconConvertSizeController",
                    false, loader);
            Class<?> folderInfo = Class.forName(
                    "com.miui.home.launcher.FolderInfo", false, loader);

            // All required APIs are resolved before any interception is added.
            java.lang.reflect.Method spanX = HookUtil.findMethodExact(
                    controller, "getFolderSpanXFromType", new Class<?>[]{int.class});
            java.lang.reflect.Method spanY = HookUtil.findMethodExact(
                    controller, "getFolderSpanYFromType", new Class<?>[]{int.class});
            java.lang.reflect.Method convert = HookUtil.findMethodExact(
                    folderInfo, "convertIconSize", new Class<?>[]{int.class});

            HookUtil.hook(spanX, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (enabled && Boolean.TRUE.equals(FORCE_LARGE.get())
                        && ((Integer) chain.getArg(0)) == FOUR_TYPE) {
                    return 2;
                }
                return result;
            });
            HookUtil.hook(spanY, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (enabled && Boolean.TRUE.equals(FORCE_LARGE.get())
                        && ((Integer) chain.getArg(0)) == FOUR_TYPE) {
                    return 2;
                }
                return result;
            });
            HookUtil.hook(convert, chain -> {
                Object info = chain.getThisObject();
                boolean force = enabled
                        && ((Integer) chain.getArg(0)) == FOUR_TYPE
                        && isExplicitlySelected(info);
                Boolean previous = FORCE_LARGE.get();
                if (force) FORCE_LARGE.set(Boolean.TRUE);
                try {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                } finally {
                    if (previous == null) FORCE_LARGE.remove();
                    else FORCE_LARGE.set(previous);
                }
            });
            enabled = true;
            installed = true;
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " unavailable: " + error);
            return false;
        }
    }

    private static boolean isExplicitlySelected(Object info) {
        if (info == null) return false;
        try {
            HookUtil.InvocationResult<Object> view =
                    HookUtil.tryInvoke(info, "getBuddyIconView");
            if (view.succeeded() && view.value() instanceof View) {
                return LauncherLargeFolderPickerHook.isMarked(
                        ((View) view.value()).getContext(), info);
            }
            // The icon can be temporarily unbound during a screen move.
            ClassLoader loader = info.getClass().getClassLoader();
            Class<?> application = Class.forName(
                    "com.miui.home.launcher.Application", false, loader);
            HookUtil.InvocationResult<Object> launcher =
                    HookUtil.tryInvokeStatic(application, "getLauncher");
            return launcher.succeeded() && launcher.value() instanceof Context
                    && LauncherLargeFolderPickerHook.isMarked(
                            (Context) launcher.value(), info);
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " folder selection lookup failed: " + error);
            return false;
        }
    }
}
