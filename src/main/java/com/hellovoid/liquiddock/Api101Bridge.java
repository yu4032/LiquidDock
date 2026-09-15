package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

/** Process-local bridge to libxposed API101. */
public final class Api101Bridge {
    private static volatile XposedModule module;

    private Api101Bridge() {}

    static void init(XposedModule value) {
        module = value;
    }

    public static XposedModule module() {
        XposedModule value = module;
        if (value == null) throw new IllegalStateException("API 101 module not initialized");
        return value;
    }

    public static SharedPreferences remotePreferences(String group) {
        return module().getRemotePreferences(group);
    }

    public static void log(String message) {
        // Keep a temporary direct Logcat mirror while the Gboard structural migration is being
        // validated on hardware. XposedModule.log() is a framework/module log channel and is not
        // guaranteed to appear in adb logcat, which made an injection failure indistinguishable
        // from a resolver miss during device diagnosis.
        Log.i("LiquidDock", message);
        try {
            module().log(Log.INFO, "LiquidDock", message);
        } catch (Throwable ignored) {}
    }

    public static void log(String message, Throwable error) {
        Log.e("LiquidDock", message, error);
        try {
            module().log(Log.ERROR, "LiquidDock", message, error);
        } catch (Throwable ignored) {}
    }
}
