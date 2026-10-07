package com.hellovoid.liquiddock;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.view.Display;

/** Resolves and clamps glass render FPS against the active display's real capabilities. */
public final class DisplayRefreshRatePolicy {
    private DisplayRefreshRatePolicy() {}

    public static int maxSupportedRefreshRateHz(Context context) {
        if (context == null) return 1;
        Display display = null;
        try { display = context.getDisplay(); } catch (Throwable ignored) {}
        if (display == null) {
            DisplayManager manager =
                    (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            display = manager != null ? manager.getDisplay(Display.DEFAULT_DISPLAY) : null;
        }
        int resolved = maxSupportedRefreshRateHz(display);
        return resolved > 0 ? resolved : 1;
    }

    static int maxSupportedRefreshRateHz(Display display) {
        if (display == null) return 0;
        float highest = 0f;
        try {
            Display.Mode current = display.getMode();
            if (current != null) highest = current.getRefreshRate();
        } catch (Throwable ignored) {}

        try {
            Display.Mode[] modes = display.getSupportedModes();
            if (modes != null) {
                for (Display.Mode mode : modes) {
                    if (mode == null) continue;
                    float rate = mode.getRefreshRate();
                    if (Float.isFinite(rate) && rate > highest) highest = rate;
                }
            }
        } catch (Throwable ignored) {}

        if (highest <= 0f) {
            try { highest = display.getRefreshRate(); } catch (Throwable ignored) {}
        }
        return roundRefreshRate(highest);
    }

    static int clampRequestedFps(Context context, int requestedFps) {
        if (requestedFps <= 0) return PassBlurQualityPolicy.DEFAULT_RENDER_FPS;
        return Math.min(requestedFps, maxSupportedRefreshRateHz(context));
    }

    static int roundRefreshRate(float refreshRate) {
        if (!Float.isFinite(refreshRate) || refreshRate <= 0f) return 0;
        return Math.max(1, Math.round(refreshRate));
    }
}
