package com.hellovoid.liquiddock;

import android.util.Log;

/** Focused diagnostics for the experimental Launcher 4.50 side-slide Sidebar extension. */
final class SideSlideHoldDiagnostics {
    static final String LOGCAT_TAG = "LiquidDockSideSlide";

    private SideSlideHoldDiagnostics() {}

    static void log(String message) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        Log.i(LOGCAT_TAG, message);
        try {
            Api101Bridge.log("[DC][SideSlideHold450] " + message);
        } catch (Throwable ignored) {
        }
    }

    static void log(String message, Throwable error) {
        if (!Api101Bridge.isDebugLoggingEnabled()) return;
        Log.e(LOGCAT_TAG, message, error);
        try {
            Api101Bridge.log("[DC][SideSlideHold450] " + message, error);
        } catch (Throwable ignored) {
        }
    }
}
