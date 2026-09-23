package com.hellovoid.liquiddock;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.reflect.Constructor;

/**
 * HyperOS Launcher uninstall/remove confirmation dialog glass.
 *
 * <p>The stable lifecycle anchor is BaseUninstallDialog construction. DeleteDialog, RemoveDialog
 * and SecondConfirmDialog all extend that semantic vendor class. This avoids relying on one caller
 * such as UninstallController.showDialog(), which may be inlined or bypassed by runtime variants,
 * while still avoiding any global Dialog/AlertDialog hook.</p>
 */
final class LauncherUninstallDialogGlassHook {
    private static final String TAG = "[DC][LauncherUninstallDialogGlass]";
    private static final String BASE_UNINSTALL_DIALOG =
            "com.miui.home.launcher.uninstall.BaseUninstallDialog";
    private static final String DELETE_DIALOG =
            "com.miui.home.launcher.uninstall.DeleteDialog";
    private static final String REMOVE_DIALOG =
            "com.miui.home.launcher.uninstall.RemoveDialog";
    private static final String SECOND_CONFIRM_DIALOG =
            "com.miui.home.launcher.uninstall.SecondConfirmDialog";
    private static final String UNINSTALL_CONTROLLER =
            "com.miui.home.launcher.uninstall.UninstallController";
    private static final String LAUNCHER =
            "com.miui.home.launcher.Launcher";
    private static Boolean cachedDeleteDialogNativeNight;
    private static boolean installed;

    private LauncherUninstallDialogGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        try {
            // Install once at process startup. It remains inert unless the exact uninstall-dialog
            // constructor scope below explicitly requests native night resources.
            boolean nativeNightBridge = LauncherDialogNativeNightBridge.install(classLoader);
            boolean cacheInvalidationHook = installDeleteDialogCacheInvalidationHook(classLoader);
            Class<?> base = Class.forName(BASE_UNINSTALL_DIALOG, false, classLoader);
            int hooked = 0;
            for (Constructor<?> constructor : base.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 0 || !Context.class.isAssignableFrom(parameters[0])) {
                    continue;
                }
                HookUtil.hook(constructor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);

                    // Read live settings before chain.proceed(): the MIUIX AlertController is
                    // constructed from the superclass chain inside this call.
                    ConfigReader liveReader = ConfigReader.load();
                    LiquidDockConfig liveConfig = LiquidDockConfig.from(liveReader);
                    boolean enabled = liveReader.b(
                            ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name(),
                            ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.runtimeFallback());
                    boolean darkMode = liveReader.b(
                            ConfigSchema.Glass.DIALOG_DARK_MODE.name(),
                            ConfigSchema.Glass.DIALOG_DARK_MODE.runtimeFallback());
                    boolean requestNativeNight = nativeNightBridge
                            && liveConfig.enabled
                            && liveConfig.glass.enabled
                            && enabled
                            && darkMode;

                    LauncherDialogNativeNightBridge.Scope nightScope =
                            requestNativeNight ? LauncherDialogNativeNightBridge.enter() : null;
                    Object result;
                    try {
                        result = chain.proceed(args);
                    } finally {
                        if (nightScope != null) nightScope.close();
                    }

                    Object owner = chain.getThisObject();
                    String type = owner != null ? owner.getClass().getName() : "<null>";
                    MainHook.log(TAG + " constructor hit type=" + type
                            + " args=" + args.length
                            + " nativeNightBridge=" + nativeNightBridge
                            + " nativeNightRequested=" + requestNativeNight);
                    if (DELETE_DIALOG.equals(type)) {
                        cachedDeleteDialogNativeNight = Boolean.valueOf(requestNativeNight);
                    }
                    if (!(owner instanceof Dialog) || args.length == 0
                            || !(args[0] instanceof Activity) || !isSupportedDialog(type)) {
                        MainHook.log(TAG + " constructor ignored type=" + type
                                + " context=" + (args.length > 0 && args[0] != null
                                ? args[0].getClass().getName() : "<null>"));
                        return result;
                    }
                    if (!liveConfig.enabled || !liveConfig.glass.enabled || !enabled) {
                        MainHook.log(TAG + " dialog skipped by live setting type=" + type);
                        return result;
                    }
                    LauncherDialogGlassCoordinator.watchUninstallDialog(
                            (Activity) args[0],
                            (Dialog) owner,
                            liveConfig.glass,
                            sourceFor(type));
                    return result;
                });
                hooked++;
            }
            if (hooked == 0) {
                MainHook.log(TAG + " no compatible BaseUninstallDialog constructor");
                return false;
            }
            installed = true;
            MainHook.log(TAG + " constructor hooks installed count=" + hooked
                    + " nativeNightBridge=" + nativeNightBridge
                    + " cacheInvalidationHook=" + cacheInvalidationHook);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    /**
     * Launcher preloads and caches DeleteDialog. Keep that optimization, but invalidate the cache
     * exactly once when the requested native-night state changes so construction-time resources
     * can be resolved again through the normal getOrCreateDeleteDialog() path.
     */
    private static boolean installDeleteDialogCacheInvalidationHook(ClassLoader classLoader) {
        try {
            Class<?> controller = Class.forName(UNINSTALL_CONTROLLER, false, classLoader);
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            HookUtil.hookMethod(
                    controller,
                    "showDialog",
                    new Class<?>[]{launcher, java.util.List.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        maybeInvalidateCachedDeleteDialog(args);
                        return chain.proceed(args);
                    });
            MainHook.log(TAG + " DeleteDialog cache invalidation hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " DeleteDialog cache invalidation hook unavailable: " + error);
            return false;
        }
    }

    private static void maybeInvalidateCachedDeleteDialog(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return;

        ConfigReader reader = ConfigReader.load();
        LiquidDockConfig config = LiquidDockConfig.from(reader);
        boolean enabled = reader.b(
                ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name(),
                ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.runtimeFallback());
        boolean darkMode = reader.b(
                ConfigSchema.Glass.DIALOG_DARK_MODE.name(),
                ConfigSchema.Glass.DIALOG_DARK_MODE.runtimeFallback());
        boolean desiredNight = config.enabled && config.glass.enabled && enabled && darkMode;

        Boolean constructedNight = cachedDeleteDialogNativeNight;
        if (constructedNight == null || constructedNight.booleanValue() == desiredNight) return;

        HookUtil.InvocationResult<Object> controller =
                HookUtil.tryInvoke(args[0], "getUninstallController");
        if (!controller.succeeded() || controller.value() == null) {
            MainHook.log(TAG + " cached DeleteDialog theme changed but controller unavailable");
            return;
        }
        HookUtil.InvocationResult<Object> released =
                HookUtil.tryInvoke(controller.value(), "releasePreloadedDialog");
        if (!released.succeeded()) {
            MainHook.log(TAG + " cached DeleteDialog release failed: " + released.failure());
            return;
        }
        cachedDeleteDialogNativeNight = null;
        MainHook.log(TAG + " released preloaded DeleteDialog for native-night change "
                + constructedNight + "->" + desiredNight);
    }

    private static boolean isSupportedDialog(String type) {
        return DELETE_DIALOG.equals(type)
                || REMOVE_DIALOG.equals(type)
                || SECOND_CONFIRM_DIALOG.equals(type);
    }

    private static String sourceFor(String type) {
        if (DELETE_DIALOG.equals(type)) return "delete-dialog";
        if (REMOVE_DIALOG.equals(type)) return "remove-dialog";
        if (SECOND_CONFIRM_DIALOG.equals(type)) return "second-confirm-dialog";
        return "unknown";
    }
}
