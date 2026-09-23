package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.view.ContextThemeWrapper;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/**
 * Forces MIUIX's own dark AlertDialog theme at the point where the native AlertDialog parent is
 * constructed for Launcher uninstall dialogs.
 *
 * <p>Changing Dialog.getContext().setTheme() after BaseUninstallDialog construction is too late:
 * MIUIX has already created its themed context / AlertController and resolved constructor-time
 * attributes. This bridge therefore intercepts only MIUIX AlertDialog constructors whose live
 * stack contains BaseUninstallDialog.<init>, replacing the parent constructor's Context and, when
 * present, explicit themeResId before MIUIX resolves any dialog resources.</p>
 *
 * <p>No view colors, tints, drawables, icons, button backgrounds, or Prismal parameters are
 * modified. The only appearance authority remains MIUIX's own AlertDialog.Theme.Dark.</p>
 */
final class LauncherDialogNativeThemeBridge {
    private static final String TAG = "[DC][LauncherDialogNativeTheme]";
    private static final String MIUIX_ALERT_DIALOG = "miuix.appcompat.app.AlertDialog";
    private static final String BASE_UNINSTALL_DIALOG =
            "com.miui.home.launcher.uninstall.BaseUninstallDialog";
    private static final String MIUIX_STYLE_CLASS = "miuix.appcompat.R$style";
    private static final String DARK_STYLE_FIELD = "AlertDialog_Theme_Dark";
    private static final String DARK_STYLE_NAME = "AlertDialog.Theme.Dark";
    private static boolean installed;

    private LauncherDialogNativeThemeBridge() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        try {
            Class<?> alertDialog = Class.forName(MIUIX_ALERT_DIALOG, false, classLoader);
            int hooked = 0;
            for (Constructor<?> constructor : alertDialog.getDeclaredConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 0
                        || !Context.class.isAssignableFrom(parameterTypes[0])) {
                    continue;
                }
                HookUtil.hook(constructor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!shouldForceDark() || !isUninstallParentConstruction()
                            || args.length == 0 || !(args[0] instanceof Context)) {
                        return chain.proceed(args);
                    }

                    Context original = (Context) args[0];
                    int styleId = resolveDarkStyle(original, classLoader);
                    if (styleId == 0) {
                        MainHook.log(TAG + " native dark style unavailable constructor="
                                + constructorSignature(constructor)
                                + " package=" + original.getPackageName());
                        return chain.proceed(args);
                    }

                    if (!(original instanceof NativeDarkContext)) {
                        args[0] = new NativeDarkContext(original, styleId);
                    }
                    boolean explicitThemeOverridden = false;
                    if (parameterTypes.length > 1 && parameterTypes[1] == int.class) {
                        args[1] = styleId;
                        explicitThemeOverridden = true;
                    }

                    MainHook.log(TAG + " forcing native dark before MIUIX construction"
                            + " constructor=" + constructorSignature(constructor)
                            + " style=0x" + Integer.toHexString(styleId)
                            + " explicitTheme=" + explicitThemeOverridden
                            + " context=" + original.getClass().getName());
                    return chain.proceed(args);
                });
                hooked++;
            }
            if (hooked == 0) {
                MainHook.log(TAG + " no compatible MIUIX AlertDialog constructor");
                return false;
            }
            installed = true;
            MainHook.log(TAG + " native constructor bridge installed count=" + hooked);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " native constructor bridge unavailable: " + error);
            return false;
        }
    }

    private static boolean shouldForceDark() {
        if (!GlassRuntimeState.isEnabled()) return false;
        ConfigReader reader = ConfigReader.load();
        return reader.b(
                        ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.name(),
                        ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS.runtimeFallback())
                && reader.b(
                        ConfigSchema.Glass.DIALOG_DARK_MODE.name(),
                        ConfigSchema.Glass.DIALOG_DARK_MODE.runtimeFallback());
    }

    private static boolean isUninstallParentConstruction() {
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (BASE_UNINSTALL_DIALOG.equals(frame.getClassName())
                    && "<init>".equals(frame.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static int resolveDarkStyle(Context context, ClassLoader classLoader) {
        try {
            Class<?> styleClass = Class.forName(MIUIX_STYLE_CLASS, false, classLoader);
            Field field = styleClass.getDeclaredField(DARK_STYLE_FIELD);
            field.setAccessible(true);
            int value = field.getInt(null);
            if (value != 0) return value;
        } catch (Throwable ignored) {
            // Fall through to resource-name lookup. Resource fields can differ between MIUIX builds.
        }

        Resources resources = context.getResources();
        if (resources == null) return 0;
        String packageName = context.getPackageName();
        int id = resources.getIdentifier(DARK_STYLE_FIELD, "style", packageName);
        if (id != 0) return id;
        return resources.getIdentifier(DARK_STYLE_NAME, "style", packageName);
    }

    private static String constructorSignature(Constructor<?> constructor) {
        StringBuilder out = new StringBuilder(constructor.getDeclaringClass().getSimpleName())
                .append('(');
        Class<?>[] types = constructor.getParameterTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) out.append(',');
            out.append(types[i].getSimpleName());
        }
        return out.append(')').toString();
    }

    /** Marker wrapper so nested MIUIX constructor delegation does not stack wrappers repeatedly. */
    private static final class NativeDarkContext extends ContextThemeWrapper {
        NativeDarkContext(Context base, int themeResId) {
            super(base, themeResId);
        }
    }
}
