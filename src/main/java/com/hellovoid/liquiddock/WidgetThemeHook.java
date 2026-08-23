package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewParent;
import android.widget.RemoteViews;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Applies a local light/dark Configuration only while RemoteViews are inflated inside MIUI's
 * LauncherAppWidgetHostView. The system uiMode and every non-widget RemoteViews call stay intact.
 */
public final class WidgetThemeHook {
    private static final String HOST_CLASS =
            "com.miui.home.launcher.LauncherAppWidgetHostView";

    private WidgetThemeHook() {}

    public static void install(ClassLoader classLoader, String mode) {
        if (!"light".equals(mode) && !"dark".equals(mode)) {
            Api101Bridge.log("[DC] Widget theme follows system");
            return;
        }

        try {
            Class<?> hostClass = Class.forName(HOST_CLASS, false, classLoader);
            int installed = 0;
            for (Method method : RemoteViews.class.getDeclaredMethods()) {
                if (!isInflationMethod(method)) continue;
                try {
                    HookUtil.hook(method, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length >= 2
                                && args[0] instanceof Context
                                && args[1] instanceof View
                                && isInsideLauncherWidgetHost((View) args[1], hostClass)) {
                            args[0] = createThemedContext((Context) args[0], mode);
                        }
                        return chain.proceed(args);
                    });
                    installed++;
                } catch (Throwable methodError) {
                    Api101Bridge.log("[DC] Widget theme method hook skipped: " + method,
                            methodError);
                }
            }
            Api101Bridge.log("[DC] Widget theme=" + mode + " RemoteViews hooks=" + installed);
        } catch (Throwable error) {
            Api101Bridge.log("[DC] Widget theme hook unavailable", error);
        }
    }

    static Context createThemedContext(Context base, String mode) {
        Configuration current = base.getResources().getConfiguration();
        int themedUiMode = WidgetThemePolicy.applyToUiMode(current.uiMode, mode);
        if (themedUiMode == current.uiMode) return base;

        Configuration override = new Configuration(current);
        override.uiMode = themedUiMode;
        return base.createConfigurationContext(override);
    }

    private static boolean isInflationMethod(Method method) {
        String name = method.getName();
        if (!"apply".equals(name)
                && !"reapply".equals(name)
                && !"applyAsync".equals(name)
                && !"reapplyAsync".equals(name)) {
            return false;
        }
        if (Modifier.isStatic(method.getModifiers())) return false;
        Class<?>[] params = method.getParameterTypes();
        return params.length >= 2
                && params[0] == Context.class
                && View.class.isAssignableFrom(params[1]);
    }

    private static boolean isInsideLauncherWidgetHost(View view, Class<?> hostClass) {
        View current = view;
        for (int depth = 0; current != null && depth < 16; depth++) {
            if (hostClass.isInstance(current)) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }
}
