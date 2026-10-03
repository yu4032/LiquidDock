package com.hellovoid.liquiddock;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

/** Process-local bridge to libxposed API101. */
public final class Api101Bridge {
    private static volatile XposedModule module;
    private static volatile boolean debugLogging;

    private Api101Bridge() {}

    static void init(XposedModule value) {
        module = value;
    }

    static void setDebugLogging(boolean enabled) {
        debugLogging = enabled;
    }

    public static boolean isDebugLoggingEnabled() {
        return debugLogging;
    }

    public static XposedModule module() {
        XposedModule value = module;
        if (value == null) throw new IllegalStateException("API 101 module not initialized");
        return value;
    }

    public static SharedPreferences remotePreferences(String group) {
        return module().getRemotePreferences(group);
    }

    /** Debug/diagnostic INFO output. Completely silent unless the user enables debug logging. */
    public static void log(String message) {
        if (!debugLogging) return;
        writeInfo(message);
    }

    /** Debug/diagnostic ERROR output. Completely silent unless the user enables debug logging. */
    public static void log(String message, Throwable error) {
        if (!debugLogging) return;
        writeError(message, error);
    }

    /**
     * Unconditional fatal module error. Keep this narrow: routine feature failures and fallback
     * diagnostics must use {@link #log(String, Throwable)} and remain behind the debug switch.
     */
    public static void errorAlways(String message, Throwable error) {
        writeError(message, error);
    }

    private static void writeInfo(String message) {
        try {
            module().log(Log.INFO, "LiquidDock", message);
        } catch (Throwable ignored) {
            Log.i("LiquidDock", message);
        }
    }

    private static void writeError(String message, Throwable error) {
        try {
            module().log(Log.ERROR, "LiquidDock", message, error);
        } catch (Throwable ignored) {
            Log.e("LiquidDock", message, error);
        }
    }
}
