package com.hellovoid.liquiddock;

import android.util.Log;

/** Always-on diagnostics for the experimental Launcher 4.50 side-slide port. */
final class SideSlideHoldDiagnostics {
    static final String LOGCAT_TAG = "LiquidDockSideSlide";

    private SideSlideHoldDiagnostics() {}

    static void log(String message) {
        Log.i(LOGCAT_TAG, message);
        try { Api101Bridge.log("[DC][SideSlideHold450] " + message); }
        catch (Throwable ignored) {}
    }

    static void log(String message, Throwable error) {
        Log.e(LOGCAT_TAG, message, error);
        try { Api101Bridge.log("[DC][SideSlideHold450] " + message, error); }
        catch (Throwable ignored) {}
    }
}
