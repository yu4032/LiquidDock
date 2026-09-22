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
    private static boolean installed;

    private LauncherUninstallDialogGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        ConfigReader preferences = ConfigReader.load();
        boolean enabled = preferences.b(
                ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name(),
                ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.runtimeFallback());
        if (!enabled) {
            MainHook.log(TAG + " disabled by setting");
            return false;
        }

        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        try {
            Class<?> base = Class.forName(BASE_UNINSTALL_DIALOG, false, classLoader);
            int hooked = 0;
            for (Constructor<?> constructor : base.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 0 || !Context.class.isAssignableFrom(parameters[0])) {
                    continue;
                }
                HookUtil.hook(constructor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    String type = owner != null ? owner.getClass().getName() : "<null>";
                    MainHook.log(TAG + " constructor hit type=" + type
                            + " args=" + args.length);
                    if (!(owner instanceof Dialog) || args.length == 0
                            || !(args[0] instanceof Activity) || !isSupportedDialog(type)) {
                        MainHook.log(TAG + " constructor ignored type=" + type
                                + " context=" + (args.length > 0 && args[0] != null
                                ? args[0].getClass().getName() : "<null>"));
                        return result;
                    }
                    LauncherDialogGlassCoordinator.watchUninstallDialog(
                            (Activity) args[0],
                            (Dialog) owner,
                            glassConfig,
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
            MainHook.log(TAG + " constructor hooks installed count=" + hooked);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
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
