package com.hellovoid.liquiddock;

import android.view.Display;
import android.view.View;
import java.lang.reflect.Method;

/**
 * Framework DisplayInfo rotation is the only physical orientation authority.
 * Xiaomi's installation quarter-turn is applied before deriving portrait /
 * landscape. This class does not read Configuration, grid dimensions or a
 * previously observed rotation. Missing normalization returns UNKNOWN.
 */
final class FrameworkDisplayRotation {
    static final int UNKNOWN = -1;
    private static volatile boolean installLookupDone;
    private static volatile Method installMethod;

    private FrameworkDisplayRotation() {}

    static int normalized(View view) {
        Display display = view == null ? null : view.getDisplay();
        return normalized(display);
    }

    static int normalized(Display display) {
        if (display == null) return UNKNOWN;
        Method method = getInstallMethod();
        if (method == null) return UNKNOWN;
        try {
            int raw = display.getRotation();
            Object value = method.invoke(display);
            if (!(value instanceof Number)) return UNKNOWN;
            int install = ((Number) value).intValue();
            if (raw < 0 || raw > 3 || install < 0 || install > 3) return UNKNOWN;
            return (raw + install) & 3;
        } catch (ReflectiveOperationException | SecurityException exception) {
            return UNKNOWN;
        }
    }

    static int direction(View view) {
        int rotation = normalized(view);
        return rotation < 0 ? UNKNOWN : (rotation & 1);
    }

    private static Method getInstallMethod() {
        if (installLookupDone) return installMethod;
        synchronized (FrameworkDisplayRotation.class) {
            if (!installLookupDone) {
                try {
                    installMethod = Display.class.getMethod("getInstallOrientation");
                } catch (ReflectiveOperationException | SecurityException exception) {
                    installMethod = null;
                }
                installLookupDone = true;
            }
        }
        return installMethod;
    }
}
