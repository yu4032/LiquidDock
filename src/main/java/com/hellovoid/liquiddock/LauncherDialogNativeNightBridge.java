package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.Window;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Native MIUIX night-resource bridge for Launcher uninstall dialogs.
 *
 * <p>While an exact BaseUninstallDialog constructor is active, the MIUIX AlertController and
 * Launcher uninstall content are created from the same dialog theme on a night configuration.
 * Native resources remain the sole authority for text, buttons, icons and selectors.</p>
 */
final class LauncherDialogNativeNightBridge {
    private static final String TAG = "[DC][LauncherDialogNativeNight]";
    private static final String ALERT_CONTROLLER = "miuix.appcompat.app.AlertController";
    private static final String APP_COMPAT_DIALOG = "androidx.appcompat.app.AppCompatDialog";
    private static final String UNINSTALL_LAYOUT = "shortcut_uninstall_dialog";

    private static final ThreadLocal<Integer> FORCE_NIGHT_DEPTH = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> NIGHT_INFLATE_REENTRY = new ThreadLocal<>();
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
                int themeResId = resolveThemeResId(original);
                if (themeResId == 0) {
                    MainHook.log(TAG + " dialog theme id unavailable; original context retained");
                    return chain.proceed(args);
                }

                Context forced = createForcedNightContext(original, themeResId);
                if (forced == null) {
                    MainHook.log(TAG + " forced night context unavailable; original context retained");
                    return chain.proceed(args);
                }

                args[0] = forced;
                return chain.proceed(args);
            });

            installUninstallLayoutHooks();
            installed = true;
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " AlertController bridge unavailable: " + error);
            return false;
        }
    }

    /**
     * The Launcher owns a custom content layout in addition to MIUIX's parent panel. Inflate only
     * this exact semantic layout from the same night-qualified context while BaseUninstallDialog
     * is under construction; every other Launcher inflate remains untouched.
     */
    private static void installUninstallLayoutHooks() {
        try {
            HookUtil.hookMethod(
                    LayoutInflater.class,
                    "inflate",
                    new Class<?>[]{int.class, ViewGroup.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object target = chain.getThisObject();
                        if (!shouldInterceptUninstallInflate(target, args)) {
                            return chain.proceed(args);
                        }
                        Object replacement = inflateUninstallInNight(
                                (LayoutInflater) target, args, false);
                        return replacement != null ? replacement : chain.proceed(args);
                    });
        } catch (Throwable error) {
            MainHook.log(TAG + " two-arg uninstall inflate hook unavailable: " + error);
        }

        try {
            HookUtil.hookMethod(
                    LayoutInflater.class,
                    "inflate",
                    new Class<?>[]{int.class, ViewGroup.class, boolean.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object target = chain.getThisObject();
                        if (!shouldInterceptUninstallInflate(target, args)) {
                            return chain.proceed(args);
                        }
                        Object replacement = inflateUninstallInNight(
                                (LayoutInflater) target, args, true);
                        return replacement != null ? replacement : chain.proceed(args);
                    });
        } catch (Throwable error) {
            MainHook.log(TAG + " three-arg uninstall inflate hook unavailable: " + error);
        }
    }

    private static boolean shouldInterceptUninstallInflate(Object target, Object[] args) {
        if (!isForceNightActive()
                || Boolean.TRUE.equals(NIGHT_INFLATE_REENTRY.get())
                || !(target instanceof LayoutInflater)
                || args == null || args.length < 2
                || !(args[0] instanceof Integer)) {
            return false;
        }
        LayoutInflater inflater = (LayoutInflater) target;
        int resourceId = (Integer) args[0];
        if (resourceId == 0) return false;
        try {
            Context context = inflater.getContext();
            return context != null
                    && "layout".equals(context.getResources().getResourceTypeName(resourceId))
                    && UNINSTALL_LAYOUT.equals(
                            context.getResources().getResourceEntryName(resourceId));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object inflateUninstallInNight(
            LayoutInflater inflater, Object[] args, boolean threeArgs) {
        Context original = inflater != null ? inflater.getContext() : null;
        if (original == null) return null;
        int themeResId = resolveThemeResId(original);
        if (themeResId == 0) {
            MainHook.log(TAG + " uninstall layout theme id unavailable; original inflater retained");
            return null;
        }
        Context forced = createForcedNightContext(original, themeResId);
        if (forced == null) return null;

        NIGHT_INFLATE_REENTRY.set(Boolean.TRUE);
        try {
            LayoutInflater nightInflater = inflater.cloneInContext(forced);
            int resourceId = (Integer) args[0];
            ViewGroup root = args[1] instanceof ViewGroup ? (ViewGroup) args[1] : null;
            Object result = threeArgs
                    ? nightInflater.inflate(
                            resourceId,
                            root,
                            args.length > 2 && args[2] instanceof Boolean && (Boolean) args[2])
                    : nightInflater.inflate(resourceId, root);
            return result;
        } catch (Throwable error) {
            MainHook.log(TAG + " native night uninstall inflate failed: " + error);
            return null;
        } finally {
            NIGHT_INFLATE_REENTRY.remove();
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

            // Keep MIUIX's actual dialog resource/overlay authority while selecting -night.
            Context configured = original.createConfigurationContext(override);
            return new ForcedNightDialogContext(configured, themeResId);
        } catch (Throwable error) {
            MainHook.log(TAG + " createConfigurationContext failed: " + error);
            return null;
        }
    }

    /** Recover the theme MIUIX selected for this dialog context. */
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
