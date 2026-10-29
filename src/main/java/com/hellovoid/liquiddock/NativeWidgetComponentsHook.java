package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.RemoteViews;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Widget component selection without claiming/suppressing the OEM advanced material.
 * Observe provider updates, not View.draw(): component hiding needs no compositor frame.
 */
final class NativeWidgetComponentsHook {
    private static final Map<View, View.OnAttachStateChangeListener> HOSTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean enabled;
    private static SharedPreferences prefs;
    private static SharedPreferences.OnSharedPreferenceChangeListener listener;

    private NativeWidgetComponentsHook() {}

    static boolean isEnabled() { return enabled; }

    static void install(ClassLoader loader, LiquidDockConfig config) {
        if (loader == null || config == null || !config.enabled
                || !config.glass.advancedMaterialEnabled) return;
        enabled = config.glass.widgetEnabled;
        prefs = Api101Bridge.remotePreferences(ConfigReader.REMOTE_GROUP);
        if (prefs != null) {
            listener = (changed, key) -> {
                if (key != null && !ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name().equals(key)
                        && !ConfigSchema.Glass.WIDGET_GLASS.name().equals(key)
                        && !ConfigSchema.Core.ENABLED.name().equals(key)
                        && !WidgetComponentStore.SELECTION_KEY.equals(key)) return;
                enabled = changed.getBoolean(ConfigSchema.Core.ENABLED.name(), true)
                        && changed.getBoolean(ConfigSchema.Glass.ADVANCED_MATERIAL_ENABLED.name(), false)
                        && changed.getBoolean(ConfigSchema.Glass.WIDGET_GLASS.name(), true);
                new Handler(Looper.getMainLooper()).post(() -> {
                    for (View host : new ArrayList<>(HOSTS.keySet())) {
                        if (host != null) {
                            if (enabled && host.isAttachedToWindow()) apply(host);
                            else LauncherWidgetComponentSelectionExecutor.release(host);
                        }
                    }
                });
            };
            prefs.registerOnSharedPreferenceChangeListener(listener);
        }
        installHost(loader, "com.miui.home.launcher.LauncherAppWidgetHostView");
        installHost(loader, "com.miui.home.launcher.maml.MaMlHostView");
        try {
            HookUtil.hookMethod(loader,
                    "com.miui.home.launcher.LauncherAppWidgetHostView", "updateAppWidget",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        if (chain.getThisObject() instanceof View)
                            apply((View) chain.getThisObject());
                        return result;
                    }, RemoteViews.class);
        } catch (Throwable error) {
            MainHook.log("[DC][NativeWidgetComponents] provider update unavailable: " + error);
        }
    }

    private static void installHost(ClassLoader loader, String name) {
        try {
            for (Constructor<?> constructor : Class.forName(name, false, loader)
                    .getDeclaredConstructors()) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (chain.getThisObject() instanceof View) observe((View) chain.getThisObject());
                    return result;
                });
            }
        } catch (Throwable error) {
            MainHook.log("[DC][NativeWidgetComponents] host unavailable " + name + ": " + error);
        }
    }

    private static void observe(View host) {
        if (HOSTS.containsKey(host)) return;
        View.OnAttachStateChangeListener observer = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { view.post(() -> apply(view)); }
            @Override public void onViewDetachedFromWindow(View view) {
                LauncherWidgetComponentSelectionExecutor.release(view);
            }
        };
        HOSTS.put(host, observer);
        host.addOnAttachStateChangeListener(observer);
        if (host.isAttachedToWindow()) host.post(() -> apply(host));
    }

    private static void apply(View host) {
        if (host == null || !enabled || !host.isAttachedToWindow()
                || !LauncherGlassHierarchy.isWorkspace(host)) return;
        LauncherWidgetComponentDiscovery.scan(host);
        LauncherWidgetComponentSelectionExecutor.claim(host);
    }
}
