package com.hellovoid.liquiddock;

import android.content.Context;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Hides only Xiaomi Launcher's Dock entry for phone mirroring. */
final class DockMirrorShortcutHook {
    private static final String TAG = "[DC][DockMirror]";
    private static final String SETTINGS_UTILS = "com.xiaomi.mirror.SystemSettingsUtils";
    private static final String HOTSEATS_LIST = "com.miui.home.launcher.hotseats.HotSeatsList";
    private static final String MIRROR_SWITCH = "pref_key_mirror_switch";

    private static final Map<Object, Boolean> HOTSEATS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private DockMirrorShortcutHook() {}

    static synchronized void install(ClassLoader classLoader) {
        if (installed || classLoader == null) return;
        installMirrorSwitchReadHook(classLoader);
        installHotSeatsTracking(classLoader);
        installed = true;
    }

    private static void installMirrorSwitchReadHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, SETTINGS_UTILS, "getInt", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (VisualRuntimeState.isMirrorShortcutHidden()
                        && args.length >= 2
                        && MIRROR_SWITCH.equals(args[1])) {
                    return 0;
                }
                return chain.proceed(args);
            }, Context.class, String.class, int.class);
            MainHook.log(TAG + " mirror switch read hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " mirror switch read hook unavailable: " + error);
        }
    }

    private static void installHotSeatsTracking(ClassLoader classLoader) {
        try {
            Class<?> type = Class.forName(HOTSEATS_LIST, false, classLoader);
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (owner != null) HOTSEATS.put(owner, Boolean.TRUE);
                    return result;
                });
            }
            MainHook.log(TAG + " HotSeatsList tracking installed constructors="
                    + constructors.length);
        } catch (Throwable error) {
            MainHook.log(TAG + " HotSeatsList tracking unavailable: " + error);
        }
    }

    static void onRuntimeVisibilityChanged() {
        ArrayList<Object> snapshot;
        synchronized (HOTSEATS) {
            snapshot = new ArrayList<>(HOTSEATS.keySet());
        }
        int refreshed = 0;
        for (Object hotSeats : snapshot) {
            if (hotSeats == null) continue;
            try {
                HookUtil.invoke(hotSeats, "onMirrorSeatUpdate");
                refreshed++;
            } catch (Throwable error) {
                MainHook.log(TAG + " HotSeatsList refresh failed: " + error);
            }
        }
        MainHook.log(TAG + " hide=" + VisualRuntimeState.isMirrorShortcutHidden()
                + " refreshed=" + refreshed);
    }
}
