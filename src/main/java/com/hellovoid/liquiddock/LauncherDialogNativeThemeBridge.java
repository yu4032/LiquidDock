package com.hellovoid.liquiddock;

import android.app.Dialog;
import android.content.Context;
import android.content.res.Resources;

/**
 * Applies MIUIX's own AlertDialog.Theme.Dark to one Launcher dialog.
 *
 * <p>No view colors, tints, drawables, icons, or button state lists are touched. The dark style is
 * resolved from the target process resources and applied through Context.setTheme() before MIUIX
 * inflates the dialog content.</p>
 */
final class LauncherDialogNativeThemeBridge {
    private static final String TAG = "[DC][LauncherDialogNativeTheme]";
    private static final String DARK_STYLE_NAME = "AlertDialog.Theme.Dark";

    private LauncherDialogNativeThemeBridge() {}

    static boolean applyDarkTheme(Dialog dialog) {
        if (dialog == null) return false;
        Context context = dialog.getContext();
        if (context == null) return false;

        int styleId = resolveDarkStyle(context);
        if (styleId == 0) {
            MainHook.log(TAG + " dark style unavailable"
                    + " package=" + context.getPackageName()
                    + " context=" + context.getClass().getName());
            return false;
        }

        try {
            context.setTheme(styleId);
            MainHook.log(TAG + " applied " + DARK_STYLE_NAME
                    + " id=0x" + Integer.toHexString(styleId)
                    + " package=" + context.getPackageName()
                    + " context=" + context.getClass().getName());
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " setTheme failed id=0x"
                    + Integer.toHexString(styleId) + " error=" + error);
            return false;
        }
    }

    private static int resolveDarkStyle(Context context) {
        Resources resources = context.getResources();
        if (resources == null) return 0;

        String packageName = context.getPackageName();
        int id = resources.getIdentifier(DARK_STYLE_NAME, "style", packageName);
        if (id != 0) return id;

        // aapt generates Java field names with underscores for dotted style names; some resource
        // table implementations also accept that spelling through getIdentifier().
        return resources.getIdentifier("AlertDialog_Theme_Dark", "style", packageName);
    }
}
