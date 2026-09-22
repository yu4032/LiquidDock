package com.hellovoid.liquiddock;

import android.app.Activity;
import android.app.Dialog;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.List;

/**
 * HyperOS Launcher uninstall/remove confirmation dialog glass.
 *
 * <p>Hooks only source-verified UninstallController entry points. It deliberately does not hook
 * framework Dialog.show(), AlertDialog, or MiuiX globally.</p>
 */
final class LauncherUninstallDialogGlassHook {
    private static final String TAG = "[DC][LauncherUninstallDialogGlass]";
    private static final String UNINSTALL_CONTROLLER =
            "com.miui.home.launcher.uninstall.UninstallController";
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
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
            HookUtil.hookMethod(
                    classLoader,
                    UNINSTALL_CONTROLLER,
                    "showDialog",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object launcher = args.length > 0 ? args[0] : null;
                        bindDeleteDialog(launcher, glassConfig);
                        return result;
                    },
                    LAUNCHER,
                    List.class);

            HookUtil.hookMethod(
                    classLoader,
                    UNINSTALL_CONTROLLER,
                    "hideAppWidthDialog",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object controller = chain.getThisObject();
                        Object result = chain.proceed(args);
                        Object launcher = args.length > 1 ? args[1] : null;
                        bindDialog(controller, launcher, "mRemoveDialog",
                                "remove-dialog", glassConfig);
                        return result;
                    },
                    List.class,
                    LAUNCHER);

            installed = true;
            MainHook.log(TAG + " hooks installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void bindDeleteDialog(Object launcher, LiquidDockConfig.Glass glassConfig) {
        if (launcher == null) return;
        HookUtil.InvocationResult<Object> controller =
                HookUtil.tryInvoke(launcher, "getUninstallController");
        if (!controller.succeeded()) {
            MainHook.log(TAG + " getUninstallController unavailable: " + controller.failure());
            return;
        }
        bindDialog(controller.value(), launcher, "mDeleteDialog", "delete-dialog", glassConfig);
    }

    private static void bindDialog(
            Object controller,
            Object launcher,
            String fieldName,
            String source,
            LiquidDockConfig.Glass glassConfig) {
        if (!(launcher instanceof Activity) || controller == null) {
            MainHook.log(TAG + " owner unavailable source=" + source);
            return;
        }
        try {
            Object dialogObject = HookUtil.getField(controller, fieldName);
            if (!(dialogObject instanceof Dialog)) {
                MainHook.log(TAG + " field is not Dialog source=" + source
                        + " field=" + fieldName);
                return;
            }
            boolean bound = LauncherDialogGlassCoordinator.attachUninstallDialog(
                    (Activity) launcher, (Dialog) dialogObject, glassConfig, source);
            if (!bound) {
                MainHook.log(TAG + " glass not claimed; stock material retained source=" + source);
            }
        } catch (Throwable error) {
            MainHook.log(TAG + " bind failed; stock material retained source=" + source
                    + " error=" + error);
        }
    }
}
