package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.RemoteViews;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Forces provider night-qualified resources only for opted-in Workspace AppWidgets. */
final class LauncherRemoteViewsNightModeHook {
    private static final String TAG = "[DC][WidgetNight]";
    private static boolean installed;

    private LauncherRemoteViewsNightModeHook() {}

    static boolean install(ClassLoader classLoader) {
        if (installed) return true;
        try {
            Class<?> remoteViewsClass = Class.forName("android.widget.RemoteViews", false, classLoader);
            int applyHooks = 0;
            int resourceHooks = 0;
            for (Method method : remoteViewsClass.getDeclaredMethods()) {
                if (isApplyEntry(method)) {
                    HookUtil.hook(method, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length >= 2 && args[0] instanceof Context
                                && isWorkspaceWidgetTarget(args[1])
                                && GlassRuntimeState.isWidgetDarkContentEnabled()) {
                            args[0] = markNightRequest((Context) args[0]);
                        }
                        return chain.proceed(args);
                    });
                    applyHooks++;
                    continue;
                }
                if (isProviderResourceContextMethod(method)) {
                    HookUtil.hook(method, chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        // Android releases before the current RemoteViews implementation may drop
                        // the caller's override Configuration while creating the provider context.
                        // Preserve our per-widget request after that cross-package context boundary.
                        if (args.length == 1 && args[0] instanceof NightRequestContext
                                && result instanceof Context) {
                            return forceNightConfiguration((Context) result);
                        }
                        return result;
                    });
                    resourceHooks++;
                }
            }
            installed = applyHooks > 0;
            MainHook.log(TAG + " RemoteViews night-resource hooks installed apply="
                    + applyHooks + " resources=" + resourceHooks);
            return installed;
        } catch (Throwable error) {
            MainHook.log(TAG + " RemoteViews night-resource hooks unavailable: " + error);
            return false;
        }
    }

    /** Reinflate the last provider RemoteViews so a live toggle changes native night resources. */
    static void reapplyCurrent(View host) {
        if (!isLauncherAppWidgetHost(host) || !LauncherGlassHierarchy.isWorkspace(host)) return;
        try {
            Object value = HookUtil.getField(host, "mLastInflatedRemoteViews");
            if (!(value instanceof RemoteViews)) return;

            // Launcher 4.50 does this in LauncherAppWidgetHostView.reInflate() before
            // updateAppWidget(mRemoteViews). Invalidating the framework-owned layout-id tag forces
            // AppWidgetHostView to inflate the newly qualified layout instead of re-applying night
            // RemoteViews actions onto a stale day-mode View tree. If this boundary is unavailable,
            // keep the existing widget alive and let the text-only fallback handle dark content.
            if (!invalidateRemoteViewsLayoutId(host)) {
                MainHook.log(TAG + " provider reinflate skipped: widget_frame tag unavailable host="
                        + host.getClass().getSimpleName());
                return;
            }

            HookUtil.invoke(host, "updateAppWidget", value);
            MainHook.log(TAG + " requested provider reinflate host="
                    + host.getClass().getSimpleName()
                    + " night=" + GlassRuntimeState.isWidgetDarkContentEnabled());
        } catch (Throwable error) {
            MainHook.log(TAG + " provider reinflate unavailable: " + error);
        }
    }

    private static boolean invalidateRemoteViewsLayoutId(View host) {
        if (!(host instanceof ViewGroup)) return false;
        ViewGroup group = (ViewGroup) host;
        if (group.getChildCount() != 1) return false;
        View child = group.getChildAt(0);
        if (child == null) return false;
        try {
            Method setTagInternal = HookUtil.findMethodExact(View.class, "setTagInternal",
                    new Class<?>[]{int.class, Object.class});
            setTagInternal.invoke(child, android.R.id.widget_frame, Integer.valueOf(-1));
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " widget_frame tag invalidation unavailable: " + error);
            return false;
        }
    }

    private static boolean isApplyEntry(Method method) {
        if (method == null || Modifier.isStatic(method.getModifiers())) return false;
        String name = method.getName();
        if (!"apply".equals(name) && !"applyAsync".equals(name)
                && !"reapply".equals(name) && !"reapplyAsync".equals(name)) return false;
        Class<?>[] types = method.getParameterTypes();
        return types.length >= 2 && Context.class.isAssignableFrom(types[0])
                && View.class.isAssignableFrom(types[1]);
    }

    private static boolean isProviderResourceContextMethod(Method method) {
        if (method == null || Modifier.isStatic(method.getModifiers())) return false;
        String name = method.getName();
        if (!"getContextForResources".equals(name)
                && !"getContextForResourcesEnsuringCorrectCachedApkPaths".equals(name)) {
            return false;
        }
        Class<?>[] types = method.getParameterTypes();
        return types.length == 1 && Context.class.isAssignableFrom(types[0])
                && Context.class.isAssignableFrom(method.getReturnType());
    }

    private static Object markNightRequest(Context context) {
        if (context instanceof NightRequestContext) return context;
        return new NightRequestContext(forceNightConfiguration(context));
    }

    private static Context forceNightConfiguration(Context context) {
        if (context == null) return null;
        int currentUiMode = context.getResources().getConfiguration().uiMode;
        if ((currentUiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES) return context;
        Configuration override = new Configuration();
        override.uiMode = (currentUiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | Configuration.UI_MODE_NIGHT_YES;
        return context.createConfigurationContext(override);
    }

    private static boolean isWorkspaceWidgetTarget(Object target) {
        if (!(target instanceof View)) return false;
        View cursor = (View) target;
        while (cursor != null) {
            if (isLauncherAppWidgetHost(cursor)) {
                return LauncherGlassHierarchy.isWorkspace(cursor);
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static boolean isLauncherAppWidgetHost(View view) {
        return view != null && view.getClass().getName().endsWith(".LauncherAppWidgetHostView");
    }

    /** Marker survives RemoteViews async apply so provider resource context can keep night mode. */
    private static final class NightRequestContext extends ContextWrapper {
        NightRequestContext(Context base) {
            super(base);
        }
    }
}
