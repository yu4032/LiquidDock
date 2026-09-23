package com.hellovoid.liquiddock;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;

import java.lang.reflect.Constructor;

/**
 * Native MIUIX night-mode bridge scoped to BaseUninstallDialog construction.
 *
 * <p>No colors, drawables, buttons, icons, or MIUIX private fields are modified. While an exact
 * Launcher uninstall dialog is being constructed, MIUIX AlertDialog receives a themed Context
 * whose only configuration override is UI_MODE_NIGHT_YES. AlertDialog/AlertController then resolve
 * their own night resources, selectors, text appearances, and button styles normally.</p>
 */
final class LauncherDialogNativeNightBridge {
    private static final String TAG = "[DC][LauncherDialogNativeNight]";
    private static final String MIUIX_ALERT_DIALOG = "miuix.appcompat.app.AlertDialog";

    private static final ThreadLocal<Integer> FORCE_NIGHT_DEPTH = new ThreadLocal<>();
    private static boolean installed;

    private LauncherDialogNativeNightBridge() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> alertDialog = Class.forName(MIUIX_ALERT_DIALOG, false, classLoader);
            int hooked = 0;
            for (Constructor<?> constructor : alertDialog.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 0 || !Context.class.isAssignableFrom(parameters[0])) {
                    continue;
                }
                HookUtil.hook(constructor, chain -> {
                    if (!isForceNightActive()) {
                        return chain.proceed(chain.getArgs().toArray(new Object[0]));
                    }

                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (args.length == 0 || !(args[0] instanceof Context)) {
                        return chain.proceed(args);
                    }

                    Context original = (Context) args[0];
                    if (isNight(original) || original instanceof ForcedNightContext) {
                        return chain.proceed(args);
                    }

                    Context forced = createForcedNightContext(original);
                    if (forced == null || !parameters[0].isInstance(forced)) {
                        MainHook.log(TAG + " native night context unavailable"
                                + " parameter=" + parameters[0].getName()
                                + " original=" + original.getClass().getName());
                        return chain.proceed(args);
                    }

                    args[0] = forced;
                    MainHook.log(TAG + " injected native night context"
                            + " constructor=" + constructor.toGenericString()
                            + " original=" + original.getClass().getName()
                            + " theme=0x" + Integer.toHexString(
                                    ((ForcedNightContext) forced).themeResId));
                    return chain.proceed(args);
                });
                hooked++;
            }
            if (hooked == 0) {
                MainHook.log(TAG + " no Context-based MIUIX AlertDialog constructor");
                return false;
            }
            installed = true;
            MainHook.log(TAG + " native night constructor hooks installed count=" + hooked);
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " native night hook unavailable: " + error);
            return false;
        }
    }

    static Scope enter() {
        Integer depth = FORCE_NIGHT_DEPTH.get();
        FORCE_NIGHT_DEPTH.set(depth == null ? 1 : depth + 1);
        return new Scope();
    }

    private static boolean isForceNightActive() {
        Integer depth = FORCE_NIGHT_DEPTH.get();
        return depth != null && depth > 0;
    }

    private static void leave() {
        Integer depth = FORCE_NIGHT_DEPTH.get();
        if (depth == null || depth <= 1) {
            FORCE_NIGHT_DEPTH.remove();
        } else {
            FORCE_NIGHT_DEPTH.set(depth - 1);
        }
    }

    private static boolean isNight(Context context) {
        if (context == null) return false;
        int uiMode = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    private static Context createForcedNightContext(Context original) {
        int themeResId = resolveHostThemeResId(original);
        if (themeResId == 0) {
            MainHook.log(TAG + " host theme unavailable context="
                    + original.getClass().getName());
            return null;
        }
        try {
            return new ForcedNightContext(original, themeResId);
        } catch (Throwable error) {
            MainHook.log(TAG + " night ContextThemeWrapper failed context="
                    + original.getClass().getName() + " error=" + error);
            return null;
        }
    }

    private static int resolveHostThemeResId(Context context) {
        Activity activity = findActivity(context);
        if (activity != null) {
            try {
                ActivityInfo info = activity.getPackageManager().getActivityInfo(
                        activity.getComponentName(), 0);
                if (info != null && info.theme != 0) return info.theme;
            } catch (Throwable error) {
                MainHook.log(TAG + " ActivityInfo theme unavailable: " + error);
            }
        }
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return info != null ? info.theme : 0;
        } catch (Throwable error) {
            MainHook.log(TAG + " ApplicationInfo theme unavailable: " + error);
            return 0;
        }
    }

    private static Activity findActivity(Context context) {
        Context current = context;
        for (int i = 0; i < 8 && current != null; i++) {
            if (current instanceof Activity) return (Activity) current;
            if (!(current instanceof android.content.ContextWrapper)) return null;
            Context next = ((android.content.ContextWrapper) current).getBaseContext();
            if (next == current) return null;
            current = next;
        }
        return null;
    }

    static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {}

        @Override public void close() {
            if (closed) return;
            closed = true;
            leave();
        }
    }

    private static final class ForcedNightContext extends ContextThemeWrapper {
        final int themeResId;

        ForcedNightContext(Context base, int themeResId) {
            super(base, themeResId);
            this.themeResId = themeResId;

            Configuration override = new Configuration(base.getResources().getConfiguration());
            override.uiMode = (override.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;
            // Must happen before this wrapper's resources/theme are first requested.
            applyOverrideConfiguration(override);
        }
    }
}
