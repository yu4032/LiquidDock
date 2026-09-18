package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;

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
    private static final String MIUI_BLUR_UTILS = "com.miui.clock.utils.MiuiBlurUtils";
    private static final String TEXT_GLASS_VIEW = "com.miui.clock.MiuiTextGlassView";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, Boolean> CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> MEMBERS = new WeakHashMap<>();
    private static final WeakHashMap<View, View> MEMBER_CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<View, View> ROOT_CONTAINERS = new WeakHashMap<>();

    private LockScreenClockNativeMaterialHook() {}

    static boolean install(ClassLoader classLoader) {
        if (classLoader == null) return false;
        if (!INSTALLED.compareAndSet(false, true)) return true;
        try {
            Class<?> utils = Class.forName(EFFECT_UTILS, false, classLoader);
            Class<?> styleInfo = Class.forName(STYLE_INFO, false, classLoader);
            Class<?> blurUtils = Class.forName(MIUI_BLUR_UTILS, false, classLoader);

            int hooks = 0;
            hooks += hookContainerIfPresent(utils, styleInfo,
                    new Class<?>[]{View.class, int.class, styleInfo, boolean.class}, 3);
            hooks += hookContainerIfPresent(utils, styleInfo,
                    new Class<?>[]{View.class, int.class, styleInfo, boolean.class, boolean.class}, 3);

            hooks += hookMemberIfPresent(utils,
                    new Class<?>[]{View.class, styleInfo, boolean.class,
                            int.class, int.class, int.class, boolean.class}, 6);
            hooks += hookMemberIfPresent(utils,
                    new Class<?>[]{View.class, styleInfo, boolean.class,
                            int.class, int.class, int.class, boolean.class, int.class}, 6);

            hooks += hookMemberIfPresent(utils,
                    new Class<?>[]{View.class, styleInfo, boolean.class,
                            int.class, int.class, boolean.class, boolean.class}, 5);
            hooks += hookMemberIfPresent(utils,
                    new Class<?>[]{View.class, styleInfo, boolean.class,
                            int.class, int.class, int.class, boolean.class, boolean.class}, 6);
            hooks += hookMemberIfPresent(utils,
                    new Class<?>[]{View.class, styleInfo, boolean.class,
                            int.class, int.class, int.class, boolean.class, boolean.class, int.class}, 6);

            Method choose = HookUtil.findMethodExact(
                    blurUtils,
                    "chooseBackgroundBlurContainer",
                    new Class<?>[]{View.class, View.class});
            HookUtil.hook(choose, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                try {
                    if (args.length >= 2 && args[0] instanceof View && args[1] instanceof View) {
                        View member = (View) args[0];
                        View container = (View) args[1];
                        if (isTimeMember(member)) {
                            synchronized (LOCK) {
                                MEMBER_CONTAINERS.put(member, container);
                                CONTAINERS.put(container, Boolean.TRUE);
                                View root = container.getRootView();
                                if (root != null) ROOT_CONTAINERS.put(root, container);
                            }
                            if (runtime() != null) {
                                applyContainer(container);
                                applyMember(member);
                            }
                            Api101Bridge.log(TAG + " native member/container route member="
                                    + resourceEntryName(member)
                                    + " container=" + container.getClass().getName());
                        }
                    }
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native member/container route failed", error);
                }
                return result;
            });
            hooks++;

            // ClassicMax and several legacy clock styles use MiuiTextGlassView, whose stock
            // onDraw still delegates to TextView/drawText. HyperOS' actual Glass glyph renderer
            // (AllInOne.TimeView) converts text to a Path and draws it with Paint#setGlassEffect.
            // Replace only the draw call while the real lockscreen scene is authoritative.
            try {
                Class<?> textGlassView = Class.forName(TEXT_GLASS_VIEW, false, classLoader);
                Method onDraw = HookUtil.findMethodExact(
                        textGlassView, "onDraw", new Class<?>[]{Canvas.class});
                HookUtil.hook(onDraw, chain -> {
                    Object self = chain.getThisObject();
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    if (!(self instanceof TextView)
                            || args.length == 0
                            || !(args[0] instanceof Canvas)
                            || !isTimeMember((View) self)
                            || runtime() == null) {
                        return chain.proceed(args);
                    }
                    View member = (View) self;
                    if (!applyMember(member)) {
                        return chain.proceed(args);
                    }
                    if (MiBlurBridge.drawClockGlassText(
                            (TextView) self, (Canvas) args[0])) {
                        return null;
                    }
                    return chain.proceed(args);
                });
                hooks++;
            } catch (NoSuchMethodException | ClassNotFoundException ignored) {
                // Some styles/builds do not ship MiuiTextGlassView; their native path remains.
            }

            if (hooks == 0) throw new IllegalStateException("no clock material overloads hooked");
            Api101Bridge.log(TAG + " installed native ClockEffectUtils material remap hooks=" + hooks);
            return true;
        } catch (Throwable error) {
            INSTALLED.set(false);
            Api101Bridge.log(TAG + " native ClockEffectUtils hook unavailable", error);
            return false;
        }
    }

    private static int hookContainerIfPresent(
            Class<?> utils, Class<?> styleInfo, Class<?>[] signature, int aodIndex) {
        try {
            Method method = HookUtil.findMethodExact(
                    utils, "setClockEffectsContainer", signature);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                try {
                    if (args.length > aodIndex && args[0] instanceof View
                            && !Boolean.TRUE.equals(args[aodIndex])) {
                        applyContainer((View) args[0]);
                    }
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native container remap failed", error);
                }
                return result;
            });
            return 1;
        } catch (NoSuchMethodException ignored) {
            return 0;
        }
    }

    private static int hookMemberIfPresent(
            Class<?> utils, Class<?>[] signature, int aodIndex) {
        try {
            Method method = HookUtil.findMethodExact(utils, "setClockEffectsView", signature);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                try {
                    if (args.length > aodIndex && args[0] instanceof View
                            && !Boolean.TRUE.equals(args[aodIndex])) {
                        applyMember((View) args[0]);
                    }
                } catch (Throwable error) {
                    Api101Bridge.log(TAG + " native member remap failed", error);
                }
                return result;
            });
            return 1;
        } catch (NoSuchMethodException ignored) {
            return 0;
        }
    }

    static void onLockscreenSceneChanged(boolean lockscreen) {
        ArrayList<View> containers;
        ArrayList<View> members;
        synchronized (LOCK) {
            containers = new ArrayList<>(CONTAINERS.keySet());
            members = new ArrayList<>(MEMBERS.keySet());
        }

        if (!lockscreen) {
            // These clock Views can survive a KEYGUARD -> AOD/other-scene transition. Remove only
            // the LiquidDock Glass state so no forced material leaks outside the real lockscreen.
            for (View view : members) {
                if (view != null) {
                    try { view.post(() -> MiBlurBridge.clearClockGlassMember(view)); }
                    catch (Throwable ignored) {}
                }
            }
            for (View view : containers) {
                if (view != null) {
                    try { view.post(() -> MiBlurBridge.clearClockGlassContainer(view)); }
                    catch (Throwable ignored) {}
                }
            }
            return;
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

    private static boolean applyContainer(View view) {
        Runtime runtime = runtime();
        if (runtime == null || view == null) return false;
        int radius = Math.max(0, Math.min(400, Math.round(runtime.appearance.blur)));
        if (!MiBlurBridge.applyClockMaterialContainer(view, radius)) return false;
        synchronized (LOCK) {
            CONTAINERS.put(view, Boolean.TRUE);
            View root = view.getRootView();
            if (root != null) ROOT_CONTAINERS.put(root, view);
        }
        Api101Bridge.log(TAG + " native container material class="
                + view.getClass().getName()
                + " id=" + resourceEntryName(view)
                + " radius=" + radius);
        return true;
    }

    private static boolean applyMember(View view) {
        Runtime runtime = runtime();
        if (runtime == null || view == null || !isTimeMember(view)) return false;
        ThirdPartyGlassAppearance appearance = runtime.appearance;
        View container;
        synchronized (LOCK) {
            container = MEMBER_CONTAINERS.get(view);
            if (container == null) {
                View root = view.getRootView();
                if (root != null) container = ROOT_CONTAINERS.get(root);
            }
        }
        if (container != null) {
            if (!applyContainer(container)) return false;
            if (!MiBlurBridge.chooseClockBackgroundBlurContainer(view, container)) {
                Api101Bridge.log(TAG + " native time member has no backdrop route id="
                        + resourceEntryName(view));
                return false;
            }
        } else {
            Api101Bridge.log(TAG + " native time member waiting for backdrop route id="
                    + resourceEntryName(view));
            return false;
        }
        if (!MiBlurBridge.applyClockMaterialMember(
                view,
                appearance.tintR,
                appearance.tintG,
                appearance.tintB,
                appearance.tintAlpha)) return false;
        synchronized (LOCK) {
            MEMBERS.put(view, Boolean.TRUE);
        }
        Api101Bridge.log(TAG + " native time member material class="
                + view.getClass().getName()
                + " id=" + resourceEntryName(view)
                + " container=" + container.getClass().getName());
        return true;
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
