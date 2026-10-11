package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.CompoundButton;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * HyperOS Settings MIUIX switches only. The system still owns click/persistence.
 *
 * The OS3 Settings layouts use SlidingButton (checkbox rows), SlidingSwitch
 * (switch rows and SettingsMainSwitchBar), and SlidingSwitchCompat. Hook only
 * declared virtual overrides in these vendor classes, never framework methods.
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
    private static boolean installed;
    private static volatile boolean disabledByPresentationFailure;
    private static int candidateDraws;
    private static int attachedCount;

    private SystemSettingsPrismalSwitchHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) {
            Api101Bridge.log(TAG + " install skipped: Settings classLoader=null");
            return false;
        }
        int hooks = 0;
        for (String name : TARGETS) {
            try {
                Class<?> target = Class.forName(name, false, loader);
                if (!CompoundButton.class.isAssignableFrom(target)) {
                    Api101Bridge.log(TAG + " invalid type=" + name);
                    continue;
                }
                Method method = findSafeTarget(target);
                if (method == null) {
                    Api101Bridge.log(TAG + " no declared UI override class=" + name);
                    continue;
                }
                HookUtil.hook(method, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    // Do not swallow Settings native exceptions or mutate business behavior.
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    if (owner instanceof CompoundButton) {
                        try {
                            if (candidateDraws++ < 8) {
                                Api101Bridge.log(TAG + " observed class="
                                        + owner.getClass().getName()
                                        + " viewId=" + ((CompoundButton) owner).getId());
                            }
                            enqueue((CompoundButton) owner);
                        } catch (Throwable error) {
                            Api101Bridge.log(TAG + " candidate skipped; native drawing retained", error);
                        }
                    }
                    return result;
                });
                hooks++;
                Api101Bridge.log(TAG + " hooked " + name + "#"
                        + method.getName() + " (Settings loader)");
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " vendor hook unavailable class=" + name
                        + " (other switches continue)", error);
            }
        }
        installed = hooks > 0;
        Api101Bridge.log(TAG + " installation complete hooks=" + hooks);
        return installed;
    }

    private static Method findSafeTarget(Class<?> target) {
        // onDraw is confirmed in canonical SlidingButton.java. Other MIUIX
        // siblings may use different overrides; resolve rather than assume.
        Method candidate = declared(target, "onDraw", Canvas.class);
        if (candidate == null) {
            candidate = declared(target, "onMeasure", int.class, int.class);
        }
        if (candidate == null) {
            candidate = declared(target, "setChecked", boolean.class);
        }
        if (candidate == null) {
            candidate = declared(target, "onTouchEvent", MotionEvent.class);
        }
        return candidate;
    }

    private static Method declared(Class<?> type, String name, Class<?>... args) {
        try {
            Method method = type.getDeclaredMethod(name, args);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException missing) {
            return null;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " member inaccessible: " + type.getName()
                    + "#" + name, error);
            return null;
        }
    }

    private static void enqueue(CompoundButton switchView) {
        if (disabledByPresentationFailure || !switchView.isAttachedToWindow()) return;
        if (SystemSettingsPrismalSwitchOverlay.isAttached(switchView)) return;
        long now = SystemClock.uptimeMillis();
        synchronized (LAST_TRIED) {
            Long last = LAST_TRIED.get(switchView);
            // The first draw can occur before the Preference row is attached or
            // measured. Throttle follow-up attempts; don't permanently blacklist it.
            if (last != null && now - last < 600L) return;
            LAST_TRIED.put(switchView, now);
        }
        switchView.post(() -> {
            try {
                if (!switchView.isAttachedToWindow()) {
                    onDetached(switchView);
                    return;
                }
                if (!SystemSettingsPrismalSwitchOverlay.attach(switchView)) {
                    disabledByPresentationFailure = true;
                    Api101Bridge.log(TAG + " visual failed; disabling new overlays until restart");
                } else if (SystemSettingsPrismalSwitchOverlay.isAttached(switchView)) {
                    if (attachedCount++ < 8) {
                        Api101Bridge.log(TAG + " visual attached class="
                                + switchView.getClass().getName());
                    }
                }
            } catch (Throwable error) {
                disabledByPresentationFailure = true;
                Api101Bridge.log(TAG + " visual exception; native control retained", error);
            }
        });
    }

    static void onDetached(CompoundButton switchView) {
        synchronized (LAST_TRIED) { LAST_TRIED.remove(switchView); }
    }
}
