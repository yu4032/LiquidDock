package com.hellovoid.liquiddock;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Reuses SystemUI's own advanced clock-material routing.
 *
 * <p>LiquidDock observes ClockEffectUtils' native container/member routing where available and
 * falls back to the owning MiuiBaseClock2 root for styles that do not publish a backdrop route.
 * Only the existing clock Views are remapped while LOCKSCREEN is active; no overlay, bitmap,
 * TextureView, Surface or private EGL thread is created.</p>
 */
final class LockScreenClockNativeMaterialHook {
    private static final String TAG = "[DC][LockScreenClockGlass]";
    private static final String EFFECT_UTILS = "com.miui.clock.utils.ClockEffectUtils";
    private static final String STYLE_INFO = "com.miui.clock.module.ClockStyleInfo";
    private static final String MIUI_BLUR_UTILS = "com.miui.clock.utils.MiuiBlurUtils";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, Boolean> CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> MEMBERS = new WeakHashMap<>();
    private static final WeakHashMap<View, View> MEMBER_CONTAINERS = new WeakHashMap<>();
    private static final WeakHashMap<View, View> ROOT_CONTAINERS = new WeakHashMap<>();
    private static final Set<Method> DRAW_HOOKS = new HashSet<>();

    // Exact public wrapper APIs recovered from HyperOS' MiuiBlurUtils implementation.
    private static Method SET_GLASS_BLUR_CONTAINER;
    private static Method SET_GLASS_EFFECT_METHOD;
    private static Method SET_PAINT_GLASS_EFFECT;
    private static Method SET_MI_GLASS_CLIP;
    private static Method CLEAR_GLASS_BLUR_CONTAINER;
    private static Method CLEAR_GLASS_EFFECT_METHOD;
    private static Method CHOOSE_BACKGROUND_BLUR_CONTAINER;

    private LockScreenClockNativeMaterialHook() {}

    static boolean install(ClassLoader classLoader) {
        if (classLoader == null) return false;
        if (!INSTALLED.compareAndSet(false, true)) return true;
        try {
            Class<?> utils = Class.forName(EFFECT_UTILS, false, classLoader);
            Class<?> styleInfo = Class.forName(STYLE_INFO, false, classLoader);
            Class<?> blurUtils = Class.forName(MIUI_BLUR_UTILS, false, classLoader);
            SET_GLASS_BLUR_CONTAINER = HookUtil.findMethodExact(
                    blurUtils, "setGlassBlurContainer",
                    new Class<?>[]{View.class, int.class, boolean.class});
            SET_GLASS_EFFECT_METHOD = HookUtil.findMethodExact(
                    blurUtils, "setGlassEffectMethod",
                    new Class<?>[]{View.class, float[].class});
            SET_PAINT_GLASS_EFFECT = HookUtil.findMethodExact(
                    blurUtils, "setPaintGlassEffect",
                    new Class<?>[]{Paint.class, boolean.class});
            SET_MI_GLASS_CLIP = HookUtil.findMethodExact(
                    blurUtils, "setMiGlassClip",
                    new Class<?>[]{View.class, float.class, float.class, float.class, float.class});
            CLEAR_GLASS_BLUR_CONTAINER = HookUtil.findMethodExact(
                    blurUtils, "clearGlassBlurContainer",
                    new Class<?>[]{View.class});
            CLEAR_GLASS_EFFECT_METHOD = HookUtil.findMethodExact(
                    blurUtils, "clearGlassEffectMethod",
                    new Class<?>[]{View.class});

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
            CHOOSE_BACKGROUND_BLUR_CONTAINER = choose;
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
                    try { view.post(() -> clearNativeGlassMember(view)); }
                    catch (Throwable ignored) {}
                }
            }
            for (View view : containers) {
                if (view != null) {
                    try { view.post(() -> clearNativeGlassContainer(view)); }
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
        if (!setNativeGlassContainer(view, radius)) return false;
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

    private static boolean ensureMemberMaterial(View view) {
        if (view == null) return false;
        ensureDrawHook(view.getClass());
        synchronized (LOCK) {
            if (Boolean.TRUE.equals(MEMBERS.get(view))
                    && MEMBER_CONTAINERS.get(view) != null) {
                return true;
            }
        }
        return applyMember(view);
    }

    private static boolean applyMember(View view) {
        Runtime runtime = runtime();
        if (runtime == null || view == null || !isTimeMember(view)) return false;
        ensureDrawHook(view.getClass());
        ThirdPartyGlassAppearance appearance = runtime.appearance;
        View container;
        synchronized (LOCK) {
            container = MEMBER_CONTAINERS.get(view);
            if (container == null) {
                View root = view.getRootView();
                if (root != null) container = ROOT_CONTAINERS.get(root);
            }
        }
        if (container == null) {
            container = resolveClockRootContainer(view);
            if (container != null) {
                synchronized (LOCK) {
                    MEMBER_CONTAINERS.put(view, container);
                    CONTAINERS.put(container, Boolean.TRUE);
                    View root = container.getRootView();
                    if (root != null) ROOT_CONTAINERS.put(root, container);
                }
            }
        }
        if (container != null) {
            if (!applyContainer(container)) return false;
            if (!chooseNativeBackgroundBlurContainer(view, container)) {
                Api101Bridge.log(TAG + " native time member has no backdrop route id="
                        + resourceEntryName(view));
                return false;
            }
        } else {
            Api101Bridge.log(TAG + " native time member waiting for backdrop route id="
                    + resourceEntryName(view));
            return false;
        }
        if (!setNativeGlassMember(
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


    private static void ensureDrawHook(Class<?> viewClass) {
        if (viewClass == null) return;
        Method draw;
        try {
            draw = HookUtil.findMethodExact(
                    viewClass, "onDraw", new Class<?>[]{Canvas.class});
        } catch (NoSuchMethodException ignored) {
            return;
        }
        synchronized (LOCK) {
            if (DRAW_HOOKS.contains(draw)) return;
            DRAW_HOOKS.add(draw);
        }
        try {
            HookUtil.hook(draw, chain -> {
                Object self = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (!(self instanceof TextView)
                        || args.length == 0
                        || !(args[0] instanceof Canvas)
                        || !isClockHierarchyView((View) self)
                        || !isTimeMember((View) self)
                        || runtime() == null) {
                    return chain.proceed(args);
                }
                View member = (View) self;
                if (!ensureMemberMaterial(member)) {
                    return chain.proceed(args);
                }
                Runtime drawRuntime = runtime();
                if (drawRuntime == null
                        || !setNativeGlassMember(
                                member,
                                drawRuntime.appearance.tintR,
                                drawRuntime.appearance.tintG,
                                drawRuntime.appearance.tintB,
                                drawRuntime.appearance.tintAlpha)) {
                    return chain.proceed(args);
                }
                if (drawNativeGlassText((TextView) self, (Canvas) args[0])) {
                    return null;
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            synchronized (LOCK) {
                DRAW_HOOKS.remove(draw);
            }
            Api101Bridge.log(TAG + " native time draw hook unavailable method=" + draw, error);
        }
    }

    private static boolean setNativeGlassContainer(View view, int radius) {
        Method method = SET_GLASS_BLUR_CONTAINER;
        if (method == null || view == null) return false;
        try {
            Object result = method.invoke(null, view, radius, false);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native glass container wrapper failed", error);
            return false;
        }
    }

    private static boolean setNativeGlassMember(
            View view, int tintR, int tintG, int tintB, int tintAlpha) {
        Method method = SET_GLASS_EFFECT_METHOD;
        if (method == null || view == null) return false;
        try {
            method.invoke(null, view,
                    nativeClockGlassData(tintR, tintG, tintB, tintAlpha));
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native glass member wrapper failed", error);
            return false;
        }
    }

    private static boolean chooseNativeBackgroundBlurContainer(View member, View container) {
        Method choose = CHOOSE_BACKGROUND_BLUR_CONTAINER;
        if (choose == null || member == null || container == null) return false;
        try {
            choose.invoke(null, member, container);
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native backdrop wrapper failed", error);
            return false;
        }
    }

    private static void clearNativeGlassContainer(View view) {
        Method method = CLEAR_GLASS_BLUR_CONTAINER;
        if (method == null || view == null) return;
        try { method.invoke(null, view); } catch (Throwable ignored) {}
    }

    private static void clearNativeGlassMember(View view) {
        Method method = CLEAR_GLASS_EFFECT_METHOD;
        if (method != null && view != null) {
            try { method.invoke(null, view); } catch (Throwable ignored) {}
        }
        if (view instanceof TextView && SET_PAINT_GLASS_EFFECT != null) {
            try { SET_PAINT_GLASS_EFFECT.invoke(null, ((TextView) view).getPaint(), false); }
            catch (Throwable ignored) {}
        }
    }

    private static boolean drawNativeGlassText(TextView view, Canvas canvas) {
        if (view == null || canvas == null
                || SET_PAINT_GLASS_EFFECT == null
                || SET_MI_GLASS_CLIP == null) return false;
        CharSequence value = view.getText();
        if (value == null || value.length() == 0) return false;
        try {
            String text = value.toString();
            Paint paint = view.getPaint();
            SET_PAINT_GLASS_EFFECT.invoke(null, paint, true);
            paint.setColor(view.getCurrentTextColor());

            float textWidth = paint.measureText(text);
            int absoluteGravity = Gravity.getAbsoluteGravity(
                    view.getGravity(), view.getLayoutDirection());
            int horizontal = absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK;
            float contentLeft = view.getCompoundPaddingLeft();
            float contentRight = view.getWidth() - view.getCompoundPaddingRight();
            float x;
            if (horizontal == Gravity.RIGHT) {
                x = contentRight - textWidth;
            } else if (horizontal == Gravity.CENTER_HORIZONTAL) {
                x = contentLeft
                        + Math.max(0f, (contentRight - contentLeft - textWidth) * 0.5f);
            } else {
                x = contentLeft;
            }
            float baseline = view.getBaseline();

            Path path = new Path();
            paint.getTextPath(text, 0, text.length(), x, baseline, path);
            RectF bounds = new RectF();
            path.computeBounds(bounds, true);
            if (bounds.isEmpty()) return false;

            SET_MI_GLASS_CLIP.invoke(
                    null, view,
                    bounds.left - 50f,
                    bounds.top - 50f,
                    bounds.right + 50f,
                    bounds.bottom + 50f);
            canvas.drawPath(path, paint);
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(TAG + " native path draw wrapper failed", error);
            return false;
        }
    }

    private static float[] nativeClockGlassData(
            int tintR, int tintG, int tintB, int tintAlpha) {
        float[] data = new float[]{
                0.05f, 0.35f, 0.5f, 0.55f, 1.0f, 2.0f, 0.3f, 0.0f, 0.0f, 1.0f,
                0.05f, 1.0f, 1.0f, 1.0f, 0.4f, 0.8f, 0.0f, 1.1f, 1.0f, 30.0f,
                2.0f, 200.0f, 400.0f, 0.3f, 2.0f, -2.0f, 2.0f, -1.0f, 6.0f, 3.0f,
                0.3f, 1.1764705f, 1.33f, 1.0f, 1.0f, 1.0f, 0.0f, 0.8f, 0.82f,
                0.0f, 0.0f, 0.0f
        };
        int r = Math.max(0, Math.min(255, tintR));
        int g = Math.max(0, Math.min(255, tintG));
        int b = Math.max(0, Math.min(255, tintB));
        int a = Math.max(0, Math.min(255, tintAlpha));
        data[11] = r / 255f;
        data[12] = g / 255f;
        data[13] = b / 255f;
        float alpha = a / 255f;
        data[14] = alpha;
        data[16] = alpha;
        return data;
    }

    private static View resolveClockRootContainer(View member) {
        View current = parentView(member);
        View fallback = null;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (isClockPackageClass(current.getClass())) {
                fallback = current;
                if (classHierarchyContains(
                        current.getClass(), "com.miui.clock.MiuiBaseClock2")) {
                    return current;
                }
            }
            current = parentView(current);
        }
        return fallback;
    }

    private static boolean isClockHierarchyView(View view) {
        View current = view;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (isClockPackageClass(current.getClass())) return true;
            current = parentView(current);
        }
        return false;
    }

    private static boolean isClockPackageClass(Class<?> cls) {
        Class<?> current = cls;
        int depth = 0;
        while (current != null && depth++ < 16) {
            String name = current.getName();
            if (name != null && name.startsWith("com.miui.clock.")) return true;
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean classHierarchyContains(Class<?> cls, String expectedName) {
        Class<?> current = cls;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (expectedName.equals(current.getName())) return true;
            current = current.getSuperclass();
        }
        return false;
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
