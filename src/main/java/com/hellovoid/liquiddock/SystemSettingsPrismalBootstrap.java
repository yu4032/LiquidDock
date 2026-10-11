package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.AssetManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * PrismalAGSL's App Startup provider runs in LiquidDock, never in the host
 * Settings process. Bind its internal shader cache to the MODULE assets
 * before ANY ComposeView/setContent. No Settings assets and no fake provider.
 *
 * The narrow reflective bridge is guarded by the R8 keep rule in
 * proguard-rules.pro. If anything fails, retain the native widget.
 */
final class SystemSettingsPrismalBootstrap {
    private static boolean initialized;
    private static boolean failed;
    private static final String TAG = "[DC][SettingsSwitch]";
    private SystemSettingsPrismalBootstrap() {}

    static synchronized boolean ensure(Context settingsContext) {
        if (initialized) return true;
        if (failed) return false;
        try {
            Context moduleContext = settingsContext.createPackageContext(
                    "com.hellovoid.liquiddock", Context.CONTEXT_IGNORE_SECURITY);
            AssetManager assets = moduleContext.getAssets();
            // Ensure the actual module APK contains the assets, not merely the
            // Settings application's unrelated AssetManager.
            try (java.io.InputStream ignored =
                         assets.open("agsl/rect_refraction.agsl")) {
                // Opening the asset performs a cheap, exact preflight.
            }
            Class<?> holder = Class.forName(
                    "com.styropyr0.prismal.internal.PrismalAgslAssets",
                    true, SystemSettingsPrismalBootstrap.class.getClassLoader());
            Field instance = holder.getDeclaredField("INSTANCE");
            instance.setAccessible(true);
            Method bind = holder.getDeclaredMethod("bind", AssetManager.class);
            bind.setAccessible(true);
            bind.invoke(instance.get(null), assets);
            initialized = true;
            Api101Bridge.log(TAG + " PrismalAGSL assets bound from module APK");
            return true;
        } catch (Throwable error) {
            failed = true;
            Api101Bridge.log(TAG + " PrismalAGSL asset bootstrap unavailable; stock kept: "
                    + error.getClass().getName() + ": " + error.getMessage());
            return false;
        }
    }
}
