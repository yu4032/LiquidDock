package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.view.View;
import android.widget.CompoundButton;

import java.lang.reflect.Method;
import java.util.WeakHashMap;

/**
 * Opt-in, Settings-only experiment: replace the presentation of MIUIX SlidingButton.
 * The original CompoundButton remains the sole state/click authority.
 *
 * We do not hook framework CompoundButton or Settings preference persistence.
 */
final class SystemSettingsPrismalSwitchHook {
    static final String TARGET_CLASS = "miuix.slidingwidget.widget.SlidingButton";
    private static final WeakHashMap<CompoundButton, Boolean> QUEUED = new WeakHashMap<>();
    private static boolean installed;

    private SystemSettingsPrismalSwitchHook() {}

    static synchronized boolean install(ClassLoader loader) {
        if (installed) return true;
        if (loader == null) return false;
        try {
            Class<?> target = Class.forName(TARGET_CLASS, false, loader);
            if (!CompoundButton.class.isAssignableFrom(target)) {
                Api101Bridge.log("[DC][SettingsSwitch] SlidingButton is not a CompoundButton; stock retained");
                return false;
            }

            Method ownerMethod;
            try {
                ownerMethod = target.getDeclaredMethod("onDraw", Canvas.class);
            } catch (NoSuchMethodException missingDraw) {
                // Some MIUIX builds draw from their onLayout override instead.
                ownerMethod = target.getDeclaredMethod("onLayout", boolean.class,
                        int.class, int.class, int.class, int.class);
            }
            if (ownerMethod.getDeclaringClass() != target) return false;
            ownerMethod.setAccessible(true);
            HookUtil.hook(ownerMethod, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof CompoundButton) {
                    enqueue((CompoundButton) owner);
                }
                return result;
            });
            installed = true;
            Api101Bridge.log("[DC][SettingsSwitch] MIUIX SlidingButton presenter hook installed");
            return true;
        } catch (Throwable error) {
            Api101Bridge.log("[DC][SettingsSwitch] unsupported vendor method; stock retained", error);
            return false;
        }
    }

    private static void enqueue(CompoundButton switchView) {
        if (!switchView.isAttachedToWindow()) return;
        synchronized (QUEUED) {
            if (QUEUED.containsKey(switchView)) return;
            QUEUED.put(switchView, Boolean.TRUE);
        }
        switchView.post(() -> {
            try {
                if (!switchView.isAttachedToWindow()
                        || !SystemSettingsPrismalSwitchOverlay.attach(switchView)) {
                    synchronized (QUEUED) { QUEUED.remove(switchView); }
                }
            } catch (Throwable error) {
                synchronized (QUEUED) { QUEUED.remove(switchView); }
                Api101Bridge.log("[DC][SettingsSwitch] presentation failed; stock retained", error);
            }
        });
    }

    static void onDetached(CompoundButton switchView) {
        synchronized (QUEUED) { QUEUED.remove(switchView); }
    }
}
