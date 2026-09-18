package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Locale;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reuses SystemUI's own advanced clock-material routing.
 *
 * <p>ClockEffectUtils already knows the exact native container/member Views for every clock style.
 * LiquidDock only remaps those existing Views onto the configured glass material while LOCKSCREEN
 * is active. No overlay, glyph bitmap, TextureView, Surface or private EGL thread is created.</p>
 */
final class LockScreenClockNativeMaterialHook {
    private static final String TAG = "[DC][LockScreenClockGlass]";
    private static final String EFFECT_UTILS = "com.miui.clock.utils.ClockEffectUtils";
    private static final String STYLE_INFO = "com.miui.clock.module.ClockStyleInfo";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, Boolean> CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> MEMBERS = new WeakHashMap<>();

    private LockScreenClockNativeMaterialHook() {}

    static boolean install(ClassLoader classLoader) {
        if (classLoader == null) return false;
        if (!INSTALLED.compareAndSet(false, true)) return true;
        try {
            Class<?> utils = Class.forName(EFFECT_UTILS, false, classLoader);
            Class<?> styleInfo = Class.forName(STYLE_INFO, false, classLoader);

            Method container = HookUtil.findMethodExact(
                    utils,
                    "setClockEffectsContainer",
                    new Class<?>[]{View.class, int.class, styleInfo, boolean.class});
            HookUtil.hook(container, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                try {
                    if (args.length >= 4 && args[0] instanceof View
                            && !Boolean.TRUE.equals(args[3])) {
                        applyContainer((View) args[0]);
                    }
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native container remap failed", error);
                }
                return result;
            });

            hookMemberOverload(utils, styleInfo, false);
            hookMemberOverload(utils, styleInfo, true);

            Api101Bridge.log(TAG + " installed native ClockEffectUtils material remap");
            return true;
        } catch (Throwable error) {
            INSTALLED.set(false);
            Api101Bridge.log(TAG + " native ClockEffectUtils hook unavailable", error);
            return false;
        }
    }

    private static void hookMemberOverload(
            Class<?> utils, Class<?> styleInfo, boolean hasTextDarkAlpha) throws Exception {
        Class<?>[] signature = hasTextDarkAlpha
                ? new Class<?>[]{
                        View.class, styleInfo, boolean.class,
                        int.class, int.class, int.class, boolean.class, int.class}
                : new Class<?>[]{
                        View.class, styleInfo, boolean.class,
                        int.class, int.class, int.class, boolean.class};
        Method method = HookUtil.findMethodExact(utils, "setClockEffectsView", signature);
        HookUtil.hook(method, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            Object result = chain.proceed(args);
            try {
                if (args.length >= 7 && args[0] instanceof View
                        && !Boolean.TRUE.equals(args[6])) {
                    applyMember((View) args[0]);
                }
            } catch (Throwable error) {
                Api101Bridge.log(TAG + " native member remap failed", error);
            }
            return result;
        });
    }

    static void onLockscreenSceneChanged(boolean lockscreen) {
        if (!lockscreen) return;
        ArrayList<View> containers;
        ArrayList<View> members;
        synchronized (LOCK) {
            containers = new ArrayList<>(CONTAINERS.keySet());
            members = new ArrayList<>(MEMBERS.keySet());
        }
        for (View view : containers) {
            if (view != null && view.isAttachedToWindow()) {
                try { view.post(() -> applyContainer(view)); } catch (Throwable ignored) {}
            }
        }
        for (View view : members) {
            if (view != null && view.isAttachedToWindow()) {
                try { view.post(() -> applyMember(view)); } catch (Throwable ignored) {}
            }
        }
    }

    private static void applyContainer(View view) {
        Runtime runtime = runtime();
        if (runtime == null || view == null || !isClockContainer(view)) return;
        int radius = Math.max(0, Math.min(400, Math.round(runtime.appearance.blur)));
        if (!MiBlurBridge.applyClockMaterialContainer(view, radius)) return;
        synchronized (LOCK) {
            CONTAINERS.put(view, Boolean.TRUE);
        }
        Api101Bridge.log(TAG + " native container material class="
                + view.getClass().getName() + " radius=" + radius);
    }

    private static void applyMember(View view) {
        Runtime runtime = runtime();
        if (runtime == null || view == null || !isTimeMember(view)) return;
        ThirdPartyGlassAppearance appearance = runtime.appearance;
        if (!MiBlurBridge.applyClockMaterialMember(
                view,
                appearance.tintR,
                appearance.tintG,
                appearance.tintB,
                appearance.tintAlpha)) return;
        synchronized (LOCK) {
            MEMBERS.put(view, Boolean.TRUE);
        }
        Api101Bridge.log(TAG + " native time member material class="
                + view.getClass().getName() + " id=" + resourceEntryName(view));
    }

    private static Runtime runtime() {
        if (!SystemUiKeyguardGoneSource.isLockscreenScene()) return null;
        ConfigReader reader = ConfigReader.load();
        LiquidDockConfig config = LiquidDockConfig.from(reader);
        if (!config.enabled || !config.glass.enabled) return null;
        ThirdPartyGlassAppearance appearance =
                LockScreenClockGlassPreferences.resolve(reader, config.glass);
        if (!appearance.enabled) return null;
        return new Runtime(appearance);
    }

    static boolean isTimeMember(View view) {
        if (view == null) return false;
        String own = normalizedResourceName(view);
        if (isExcluded(own)) return false;
        if (isTimeSemantic(own)) return true;

        View current = parentView(view);
        int depth = 0;
        while (current != null && depth++ < 4) {
            String name = normalizedResourceName(current);
            if (isExcluded(name)) return false;
            if (isTimeContainerSemantic(name)) return true;
            current = parentView(current);
        }
        return false;
    }

    static boolean isClockContainer(View view) {
        if (view == null) return false;
        String className = view.getClass().getName().toLowerCase(Locale.ROOT);
        String resource = normalizedResourceName(view);
        if (isExcluded(resource)
                || className.contains("datesignature")
                || className.contains("notification")
                || className.contains("weather")) return false;
        return className.contains(".clock.")
                || className.endsWith("clock")
                || className.contains("clockview")
                || isTimeContainerSemantic(resource);
    }

    private static View parentView(View view) {
        android.view.ViewParent parent = view.getParent();
        return parent instanceof View ? (View) parent : null;
    }

    private static String normalizedResourceName(View view) {
        String name = resourceEntryName(view);
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }

    private static String resourceEntryName(View view) {
        if (view == null || view.getId() == View.NO_ID) return null;
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isExcluded(String name) {
        return name != null && (name.contains("date")
                || name.contains("week")
                || name.contains("weather")
                || name.contains("signature")
                || name.contains("notification")
                || name.contains("lunar"));
    }

    private static boolean isTimeSemantic(String name) {
        if (name == null || name.isEmpty()) return false;
        return name.equals("time")
                || name.equals("time_view")
                || name.equals("time_view2")
                || name.equals("tv_time")
                || name.equals("tv_hour")
                || name.equals("tv_minute")
                || name.equals("colon")
                || name.equals("colon1")
                || name.equals("colon2")
                || name.equals("colon_view")
                || name.contains("time_hour")
                || name.contains("time_minute")
                || name.contains("hour_text")
                || name.contains("minute_text")
                || name.contains("time_colon")
                || name.contains("time_separator")
                || name.endsWith("_hour")
                || name.endsWith("_minute");
    }

    private static boolean isTimeContainerSemantic(String name) {
        if (name == null || name.isEmpty()) return false;
        return name.equals("time_container")
                || name.equals("hour_container")
                || name.equals("minute_container")
                || name.contains("clock_time")
                || name.contains("time_content")
                || name.contains("time_group")
                || name.equals("time_hour")
                || name.equals("time_minute");
    }

    private static final class Runtime {
        final ThirdPartyGlassAppearance appearance;

        Runtime(ThirdPartyGlassAppearance appearance) {
            this.appearance = appearance;
        }
    }
}
