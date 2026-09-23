package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.Window;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Native MIUIX night-resource bridge for Launcher uninstall dialogs.
 *
 * <p>The authoritative boundary is {@code AlertController(Context, AppCompatDialog, Window)}.
 * MIUIX stores this Context, resolves {@code alertDialogStyle} from it, inflates the entire dialog
 * hierarchy with it, and later uses the resulting text colors / night configuration to select the
 * day/night material token. Spoofing AlertDialog after construction is therefore too late.</p>
 *
 * <p>While an exact BaseUninstallDialog constructor is active, this bridge replaces only the
 * AlertController Context with the same MIUIX dialog theme reapplied on a night-configuration
 * Context. No text color, button tint, drawable, icon, selector, or Prismal color is modified by
 * LiquidDock.</p>
 */
final class LauncherDialogNativeNightBridge {
    private static final String TAG = "[DC][LauncherDialogNativeNight]";
    private static final String ALERT_CONTROLLER = "miuix.appcompat.app.AlertController";
    private static final String APP_COMPAT_DIALOG = "androidx.appcompat.app.AppCompatDialog";

    private static final ThreadLocal<Integer> FORCE_NIGHT_DEPTH = new ThreadLocal<>();
    private static boolean installed;

    private LauncherDialogNativeNightBridge() {}

    static synchronized boolean install(ClassLoader classLoader) {
        if (installed) return true;
        if (classLoader == null) return false;
        try {
            Class<?> controller = Class.forName(ALERT_CONTROLLER, false, classLoader);
            Class<?> appCompatDialog = Class.forName(APP_COMPAT_DIALOG, false, classLoader);
            Constructor<?> constructor = controller.getDeclaredConstructor(
                    Context.class, appCompatDialog, Window.class);
            constructor.setAccessible(true);

            HookUtil.hook(constructor, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!isForceNightActive() || args.length < 1 || !(args[0] instanceof Context)) {
                    return chain.proceed(args);
                }

                Context original = (Context) args[0];
                int originalNight = nightMask(original);
                int themeResId = resolveThemeResId(original);
                if (themeResId == 0) {
                    MainHook.log(TAG + " dialog theme id unavailable; original retained"
                            + " context=" + original.getClass().getName()
                            + " night=0x" + Integer.toHexString(originalNight));
                    return chain.proceed(args);
                }

                Context forced = createForcedNightContext(original, themeResId);
                if (forced == null) {
                    MainHook.log(TAG + " forced night context unavailable; original retained"
                            + " context=" + original.getClass().getName()
                            + " theme=0x" + Integer.toHexString(themeResId));
                    return chain.proceed(args);
                }

                args[0] = forced;
                MainHook.log(TAG + " injected AlertController context"
                        + " original=" + original.getClass().getName()
                        + " forced=" + forced.getClass().getName()
                        + " theme=0x" + Integer.toHexString(themeResId)
                        + " night=0x" + Integer.toHexString(originalNight)
                        + "->0x" + Integer.toHexString(nightMask(forced)));
                return chain.proceed(args);
            });

            installed = true;
            MainHook.log(TAG + " AlertController native-night bridge installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " AlertController bridge unavailable: " + error);
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

    private static Context createForcedNightContext(Context original, int themeResId) {
        try {
            Configuration override = new Configuration(
                    original.getResources().getConfiguration());
            override.uiMode = (override.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | Configuration.UI_MODE_NIGHT_YES;

            // createConfigurationContext() is deliberately called on MIUIX's actual themed
            // dialog context, not on the Launcher Activity/Application. The resulting Resources
            // therefore keep the same package/overlay asset authority while selecting -night.
            Context configured = original.createConfigurationContext(override);
            return new ForcedNightDialogContext(configured, themeResId);
        } catch (Throwable error) {
            MainHook.log(TAG + " createConfigurationContext failed: " + error);
            return null;
        }
    }

    /**
     * Recover the theme that MIUIX already selected for this AlertDialog context.
     *
     * <p>Do not substitute ActivityInfo/ApplicationInfo.theme here: that was the old failed
     * approach and drops MIUIX's dialog-specific theme layer.</p>
     */
    private static int resolveThemeResId(Context context) {
        Context current = context;
        for (int depth = 0; depth < 8 && current != null; depth++) {
            int resolved = invokeThemeResId(current);
            if (resolved != 0) return resolved;
            resolved = readThemeResourceField(current);
            if (resolved != 0) return resolved;

            if (!(current instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper) current).getBaseContext();
            if (next == current) break;
            current = next;
        }
        return 0;
    }

    private static int invokeThemeResId(Context context) {
        Class<?> type = context != null ? context.getClass() : null;
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("getThemeResId");
                method.setAccessible(true);
                Object value = method.invoke(context);
                return value instanceof Number ? ((Number) value).intValue() : 0;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static int readThemeResourceField(Context context) {
        Class<?> type = context != null ? context.getClass() : null;
        while (type != null) {
            try {
                Field field = type.getDeclaredField("mThemeResource");
                field.setAccessible(true);
                Object value = field.get(context);
                return value instanceof Number ? ((Number) value).intValue() : 0;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static int nightMask(Context context) {
        if (context == null || context.getResources() == null) return 0;
        return context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
    }

    static final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() {}

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            leave();
        }
    }

    /** Marker wrapper around the same MIUIX dialog theme, now resolved against -night resources. */
    private static final class ForcedNightDialogContext extends ContextThemeWrapper {
        ForcedNightDialogContext(Context configuredBase, int themeResId) {
            super(configuredBase, themeResId);
        }
    }
}
