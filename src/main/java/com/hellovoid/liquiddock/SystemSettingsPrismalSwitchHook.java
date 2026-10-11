package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.widget.CompoundButton;
import java.lang.reflect.Method;

/**
 * Restrict the experiment to verified MIUIX virtual drawing overrides.
 * No ComposeView, lifecycle injection, listener changes or preference hooks.
 * A failure to draw always proceeds to Settings' original onDraw.
 */
final class SystemSettingsPrismalSwitchHook {
    static final String TARGET_CLASS = "miuix.slidingwidget.widget.SlidingButton";
    static final String TARGET_SWITCH = "miuix.slidingwidget.widget.SlidingSwitch";
    static final String TARGET_SWITCH_COMPAT = "miuix.slidingwidget.widget.SlidingSwitchCompat";
    private static final String TAG = "[DC][SettingsSwitch]";
    private static final String[] TARGETS = {
            TARGET_CLASS, TARGET_SWITCH, TARGET_SWITCH_COMPAT
    };
    private static boolean installed;
    private static int candidateDraws;

    private SystemSettingsPrismalSwitchHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) {
            Api101Bridge.log(TAG + " install skipped: Settings classLoader=null");
            return false;
        }
        int count = 0;
        for (String name : TARGETS) {
            try {
                Class<?> target = Class.forName(name, false, loader);
                if (!CompoundButton.class.isAssignableFrom(target)) {
                    Api101Bridge.log(TAG + " invalid CompoundButton type " + name);
                    continue;
                }
                Method method = declared(target, "onDraw", Canvas.class);
                if (method == null) {
                    Api101Bridge.log(TAG + " no declared onDraw(Canvas) " + name);
                    continue;
                }
                HookUtil.hook(method, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object owner = chain.getThisObject();
                    if (owner instanceof CompoundButton && args.length == 1
                            && args[0] instanceof Canvas) {
                        try {
                            if (candidateDraws++ < 5) {
                                Api101Bridge.log(TAG + " native onDraw observed="
                                        + owner.getClass().getName()
                                        + " id=" + ((CompoundButton) owner).getId());
                            }
                            // Rendering only: native View's touch, accessibility,
                            // setChecked, haptics and persistence stay authoritative.
                            if (SystemSettingsNativeSwitchPainter.paint(
                                    (Canvas) args[0], (CompoundButton) owner)) {
                                return null;
                            }
                        } catch (Throwable error) {
                            Api101Bridge.log(TAG + " native renderer failed, "
                                    + "falling back to MIUIX: "
                                    + error.getClass().getName()
                                    + " " + error.getMessage());
                        }
                    }
                    return chain.proceed(args);
                });
                count++;
                Api101Bridge.log(TAG + " hooked native Canvas "
                        + name + "#onDraw");
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " vendor hook unavailable=" + name, error);
            }
        }
        installed = count > 0;
        Api101Bridge.log(TAG + " native Canvas installed hooks=" + count);
        return installed;
    }

    private static Method declared(Class<?> type, String name, Class<?>... params) {
        try {
            Method method = type.getDeclaredMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (Throwable unavailable) {
            Api101Bridge.log(TAG + " draw override unavailable "
                    + type.getName() + "#" + name + ": "
                    + unavailable.getClass().getSimpleName());
            return null;
        }
    }
}
