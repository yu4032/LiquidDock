package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.widget.CompoundButton;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Discover native MIUIX controls and attach a genuine PrismalAGSL component.
 * The vendor CompoundButton is retained for Preference state/persistence and
 * accessibility, but its actual visual/touch surface is replaced with Prismal.
 */
final class SystemSettingsPrismalSwitchHook {
    static final String TARGET_CLASS = "miuix.slidingwidget.widget.SlidingButton";
    static final String TARGET_SWITCH = "miuix.slidingwidget.widget.SlidingSwitch";
    static final String TARGET_SWITCH_COMPAT = "miuix.slidingwidget.widget.SlidingSwitchCompat";
    private static final String TAG = "[DC][SettingsSwitch]";
    private static final String[] TARGETS = {
            TARGET_CLASS, TARGET_SWITCH, TARGET_SWITCH_COMPAT
    };
    private static final WeakHashMap<CompoundButton, Long> LAST_TRIED = new WeakHashMap<>();
    private static int observed;
    private static boolean installed;

    private SystemSettingsPrismalSwitchHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        int count = 0;
        for (String name : TARGETS) {
            try {
                Class<?> cls = Class.forName(name, false, loader);
                if (!CompoundButton.class.isAssignableFrom(cls)) continue;
                Method draw = declared(cls, "onDraw", Canvas.class);
                if (draw == null) continue;
                HookUtil.hook(draw, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object target = chain.getThisObject();
                    if (target instanceof CompoundButton) {
                        try {
                            CompoundButton stock = (CompoundButton) target;
                            if (observed++ < 6) {
                                Api101Bridge.log(TAG + " native switch discovered="
                                        + stock.getClass().getName() + " id=" + stock.getId());
                            }
                            enqueue(stock);
                        } catch (Throwable error) {
                            Api101Bridge.log(TAG + " Prismal attachment not enqueued: "
                                    + error.getClass().getName() + " " + error.getMessage());
                        }
                    }
                    return result;
                });
                count++;
                Api101Bridge.log(TAG + " installed native discovery " + name + "#onDraw");

                Method checked = declared(cls, "setChecked", boolean.class);
                if (checked != null) {
                    try {
                        HookUtil.hook(checked, chain -> {
                            Object[] params = chain.getArgs().toArray(new Object[0]);
                            Object result = chain.proceed(params);
                            if (chain.getThisObject() instanceof CompoundButton) {
                                try {
                                    SystemSettingsPrismalSwitchHost.onCheckedChanged(
                                            (CompoundButton) chain.getThisObject());
                                } catch (Throwable error) {
                                    Api101Bridge.log(TAG + " Prismal state sync unavailable "
                                            + error.getClass().getSimpleName());
                                }
                            }
                            return result;
                        });
                    } catch (Throwable error) {
                        Api101Bridge.log(TAG + " checked observer skipped "
                                + error.getClass().getSimpleName());
                    }
                }
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " vendor discovery unavailable " + name
                        + " " + error.getClass().getName());
            }
        }
        installed = count > 0;
        Api101Bridge.log(TAG + " Prismal component discovery hooks=" + count);
        return installed;
    }

    private static void enqueue(CompoundButton view) {
        if (!view.isAttachedToWindow()
                || SystemSettingsPrismalSwitchHost.isAttached(view)) return;
        long now = SystemClock.uptimeMillis();
        synchronized (LAST_TRIED) {
            Long last = LAST_TRIED.get(view);
            if (last != null && now - last < 1500L) return;
            LAST_TRIED.put(view, now);
        }
        view.post(() -> {
            try {
                if (view.isAttachedToWindow())
                    SystemSettingsPrismalSwitchHost.attach(view);
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " Prismal component install fell back "
                        + error.getClass().getName() + ": " + error.getMessage());
            }
        });
    }

    private static Method declared(Class<?> cls, String name, Class<?>... parameters) {
        try {
            Method m = cls.getDeclaredMethod(name, parameters);
            m.setAccessible(true);
            return m;
        } catch (Throwable notPresent) {
            return null;
        }
    }
}
