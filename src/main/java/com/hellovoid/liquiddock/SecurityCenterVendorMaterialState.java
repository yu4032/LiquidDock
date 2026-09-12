package com.hellovoid.liquiddock;

import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mirrors vendor material intent at stable View APIs while LiquidDock temporarily owns carriers.
 *
 * <p>The tracker is installed before Security Center creates the sidebar. Vendor writes are
 * remembered. Once a carrier is claimed, later vendor writes update the remembered state but are
 * suppressed on-screen; LiquidDock writes run under an explicit guard. Releasing an owner replays
 * the latest remembered vendor state without calling any private Security Center restore method.
 */
final class SecurityCenterVendorMaterialState {
    private static final Object LOCK = new Object();
    private static final ThreadLocal<Integer> MODULE_MUTATION_DEPTH =
            ThreadLocal.withInitial(() -> 0);
    private static final Map<View, Snapshot> OBSERVED = new WeakHashMap<>();
    private static final Map<View, WeakReference<Object>> OWNER_BY_VIEW = new WeakHashMap<>();
    private static final Map<Object, List<WeakReference<View>>> VIEWS_BY_OWNER = new WeakHashMap<>();

    private static Method setBackground;
    private static Method setPassWindowBlurEnabled;
    private static Method setMiViewBlurMode;
    private static Method setMiBackgroundBlurMode;
    private static Method setMiBackgroundBlurRadius;
    private static Method setMiBackgroundBlendColors;
    private static Method clearMiBackgroundBlendColor;
    private static Method setMiBloomStroke;
    private static Method setMiShadow;
    private static boolean installed;

    private SecurityCenterVendorMaterialState() {}

    static synchronized boolean install() {
        if (installed) return true;
        try {
            setBackground = requireTrackable(
                    "setBackground", new Class<?>[]{Drawable.class});
            setPassWindowBlurEnabled = requireTrackable(
                    "setPassWindowBlurEnabled", new Class<?>[]{boolean.class});
            setMiViewBlurMode = requireTrackable(
                    "setMiViewBlurMode", new Class<?>[]{int.class});
            setMiBackgroundBlurMode = requireTrackable(
                    "setMiBackgroundBlurMode", new Class<?>[]{int.class});
            setMiBackgroundBlurRadius = requireTrackable(
                    "setMiBackgroundBlurRadius", new Class<?>[]{int.class});
            setMiBackgroundBlendColors = requireTrackable(
                    "setMiBackgroundBlendColors", new Class<?>[]{ArrayList.class});
            clearMiBackgroundBlendColor = requireTrackable(
                    "clearMiBackgroundBlendColor", new Class<?>[0]);
            setMiBloomStroke = requireTrackable(
                    "setMiBloomStroke", new Class<?>[]{float[].class});
            setMiShadow = requireTrackable(
                    "setMiShadow",
                    new Class<?>[]{int.class, float.class, float.class, float.class, float.class});

            hook(setBackground, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.backgroundKnown = true;
                snapshot.background = args.isEmpty() ? null : (Drawable) args.get(0);
            });
            hook(setPassWindowBlurEnabled, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.passEnabledKnown = true;
                snapshot.passEnabled = Boolean.TRUE.equals(args.get(0));
            });
            hook(setMiViewBlurMode, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.viewBlurModeKnown = true;
                snapshot.viewBlurMode = ((Number) args.get(0)).intValue();
            });
            hook(setMiBackgroundBlurMode, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.backgroundBlurModeKnown = true;
                snapshot.backgroundBlurMode = ((Number) args.get(0)).intValue();
            });
            hook(setMiBackgroundBlurRadius, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.backgroundBlurRadiusKnown = true;
                snapshot.backgroundBlurRadius = ((Number) args.get(0)).intValue();
            });
            hook(setMiBackgroundBlendColors, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.blendColorsKnown = true;
                snapshot.blendColors = copyBlendColors(args.isEmpty() ? null : args.get(0));
            });
            hook(clearMiBackgroundBlendColor, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.blendColorsKnown = true;
                snapshot.blendColors = null;
            });
            hook(setMiBloomStroke, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.bloomKnown = true;
                Object raw = args.isEmpty() ? null : args.get(0);
                snapshot.bloom = raw instanceof float[] ? ((float[]) raw).clone() : null;
            });
            hook(setMiShadow, (target, args) -> {
                Snapshot snapshot = snapshot(target);
                snapshot.shadowKnown = true;
                snapshot.shadowColor = ((Number) args.get(0)).intValue();
                snapshot.shadowX = ((Number) args.get(1)).floatValue();
                snapshot.shadowY = ((Number) args.get(2)).floatValue();
                snapshot.shadowRadius = ((Number) args.get(3)).floatValue();
                snapshot.shadowAlpha = ((Number) args.get(4)).floatValue();
            });
            installed = true;
            return true;
        } catch (Throwable error) {
            log("stable vendor material interception unavailable", error);
            return false;
        }
    }

    static void claimOwner(Object owner, View... targets) {
        if (owner == null) throw new IllegalArgumentException("owner == null");
        synchronized (LOCK) {
            List<WeakReference<View>> owned = VIEWS_BY_OWNER.computeIfAbsent(
                    owner, ignored -> new ArrayList<>());
            if (targets == null) return;
            for (View target : targets) {
                if (target == null) continue;
                WeakReference<Object> ownerRef = OWNER_BY_VIEW.get(target);
                Object existingOwner = ownerRef != null ? ownerRef.get() : null;
                if (existingOwner != null && existingOwner != owner) {
                    throw new IllegalStateException("Security Center material carrier already claimed");
                }
                if (existingOwner == owner) continue;

                Snapshot snapshot = snapshot(target);
                snapshot.backgroundKnown = true;
                snapshot.background = target.getBackground();
                OWNER_BY_VIEW.put(target, new WeakReference<>(owner));
                owned.add(new WeakReference<>(target));
            }
        }
    }

    static void restoreOwner(Object owner) {
        if (owner == null) return;
        List<RestoreEntry> restore = new ArrayList<>();
        synchronized (LOCK) {
            List<WeakReference<View>> owned = VIEWS_BY_OWNER.remove(owner);
            if (owned == null) return;
            for (WeakReference<View> ref : owned) {
                View target = ref.get();
                if (target == null) continue;
                WeakReference<Object> currentOwnerRef = OWNER_BY_VIEW.get(target);
                Object currentOwner = currentOwnerRef != null ? currentOwnerRef.get() : null;
                if (currentOwner != owner) continue;
                OWNER_BY_VIEW.remove(target);
                Snapshot snapshot = OBSERVED.get(target);
                if (snapshot != null) restore.add(new RestoreEntry(target, new Snapshot(snapshot)));
            }
        }

        Throwable firstFailure = null;
        for (RestoreEntry entry : restore) {
            try {
                replay(entry.target, entry.snapshot);
            } catch (Throwable error) {
                if (firstFailure == null) firstFailure = error;
                log("vendor material replay failed for " + entry.target.getClass().getName(), error);
            }
        }
        if (firstFailure != null) {
            throw new IllegalStateException("Security Center vendor material replay failed", firstFailure);
        }
    }

    static void runModuleMutation(Runnable mutation) {
        if (mutation == null) return;
        withModuleMutation(() -> {
            mutation.run();
            return null;
        });
    }

    static <T> T withModuleMutation(Mutation<T> mutation) {
        if (mutation == null) return null;
        int depth = MODULE_MUTATION_DEPTH.get();
        MODULE_MUTATION_DEPTH.set(depth + 1);
        try {
            return mutation.run();
        } finally {
            if (depth == 0) MODULE_MUTATION_DEPTH.remove();
            else MODULE_MUTATION_DEPTH.set(depth);
        }
    }

    private static Method requireTrackable(String name, Class<?>[] parameterTypes) throws Exception {
        Method method = HookUtil.findMethodExact(View.class, name, parameterTypes);
        Class<?> result = method.getReturnType();
        if (result != void.class && result != boolean.class && result != Boolean.class) {
            throw new IllegalStateException("unsupported tracked View API return type: " + method);
        }
        return method;
    }

    private static void hook(Method method, Recorder recorder) {
        HookUtil.hook(method, chain -> {
            if (isModuleMutation()) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            Object receiver = chain.getThisObject();
            if (!(receiver instanceof View)) {
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            }
            View target = (View) receiver;
            synchronized (LOCK) {
                recorder.record(target, chain.getArgs());
                if (isClaimedLocked(target)) return successfulSuppressionResult(method);
            }
            return chain.proceed(chain.getArgs().toArray(new Object[0]));
        });
    }

    private static boolean isModuleMutation() {
        return MODULE_MUTATION_DEPTH.get() > 0;
    }

    private static boolean isClaimedLocked(View target) {
        WeakReference<Object> ref = OWNER_BY_VIEW.get(target);
        Object owner = ref != null ? ref.get() : null;
        if (owner != null) return true;
        if (ref != null) OWNER_BY_VIEW.remove(target);
        return false;
    }

    private static Object successfulSuppressionResult(Method method) {
        Class<?> result = method.getReturnType();
        if (result == boolean.class || result == Boolean.class) return Boolean.TRUE;
        return null;
    }

    private static Snapshot snapshot(View target) {
        Snapshot snapshot = OBSERVED.get(target);
        if (snapshot == null) {
            snapshot = new Snapshot();
            OBSERVED.put(target, snapshot);
        }
        return snapshot;
    }

    private static ArrayList<Object> copyBlendColors(Object raw) {
        if (!(raw instanceof ArrayList<?>)) return null;
        ArrayList<Object> copy = new ArrayList<>();
        for (Object value : (ArrayList<?>) raw) {
            if (value instanceof Point) copy.add(new Point((Point) value));
            else copy.add(value);
        }
        return copy;
    }

    private static void replay(View target, Snapshot snapshot) {
        withModuleMutation(() -> {
            if (snapshot.passEnabledKnown) {
                invoke(setPassWindowBlurEnabled, target, snapshot.passEnabled);
            }
            if (snapshot.backgroundBlurModeKnown) {
                invoke(setMiBackgroundBlurMode, target, snapshot.backgroundBlurMode);
            }
            if (snapshot.backgroundBlurRadiusKnown) {
                invoke(setMiBackgroundBlurRadius, target, snapshot.backgroundBlurRadius);
            }
            if (snapshot.viewBlurModeKnown) {
                invoke(setMiViewBlurMode, target, snapshot.viewBlurMode);
            }
            if (snapshot.blendColorsKnown) {
                if (snapshot.blendColors == null) {
                    invoke(clearMiBackgroundBlendColor, target);
                } else {
                    invoke(setMiBackgroundBlendColors, target,
                            new ArrayList<>(snapshot.blendColors));
                }
            }
            if (snapshot.bloomKnown) {
                invoke(setMiBloomStroke, target,
                        (Object) (snapshot.bloom != null ? snapshot.bloom.clone() : null));
            }
            if (snapshot.shadowKnown) {
                invoke(setMiShadow, target,
                        snapshot.shadowColor,
                        snapshot.shadowX,
                        snapshot.shadowY,
                        snapshot.shadowRadius,
                        snapshot.shadowAlpha);
            }
            if (snapshot.backgroundKnown) {
                invoke(setBackground, target, snapshot.background);
            }
            return null;
        });
    }

    private static Object invoke(Method method, Object target, Object... args) {
        if (method == null) throw new IllegalStateException("tracked View API missing");
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("tracked View API invocation failed", cause);
        } catch (Throwable error) {
            throw new IllegalStateException("tracked View API invocation failed", error);
        }
    }

    private static void log(String message, Throwable error) {
        try { Api101Bridge.log("[DC][SecurityCenterGlass] " + message, error); }
        catch (Throwable ignored) {}
    }

    private interface Recorder {
        void record(View target, List<?> args);
    }

    interface Mutation<T> {
        T run();
    }

    private static final class RestoreEntry {
        final View target;
        final Snapshot snapshot;

        RestoreEntry(View target, Snapshot snapshot) {
            this.target = target;
            this.snapshot = snapshot;
        }
    }

    private static final class Snapshot {
        boolean backgroundKnown;
        Drawable background;
        boolean passEnabledKnown;
        boolean passEnabled;
        boolean viewBlurModeKnown;
        int viewBlurMode;
        boolean backgroundBlurModeKnown;
        int backgroundBlurMode;
        boolean backgroundBlurRadiusKnown;
        int backgroundBlurRadius;
        boolean blendColorsKnown;
        ArrayList<Object> blendColors;
        boolean bloomKnown;
        float[] bloom;
        boolean shadowKnown;
        int shadowColor;
        float shadowX;
        float shadowY;
        float shadowRadius;
        float shadowAlpha;

        Snapshot() {}

        Snapshot(Snapshot other) {
            backgroundKnown = other.backgroundKnown;
            background = other.background;
            passEnabledKnown = other.passEnabledKnown;
            passEnabled = other.passEnabled;
            viewBlurModeKnown = other.viewBlurModeKnown;
            viewBlurMode = other.viewBlurMode;
            backgroundBlurModeKnown = other.backgroundBlurModeKnown;
            backgroundBlurMode = other.backgroundBlurMode;
            backgroundBlurRadiusKnown = other.backgroundBlurRadiusKnown;
            backgroundBlurRadius = other.backgroundBlurRadius;
            blendColorsKnown = other.blendColorsKnown;
            blendColors = other.blendColors == null ? null : new ArrayList<>(other.blendColors);
            bloomKnown = other.bloomKnown;
            bloom = other.bloom == null ? null : other.bloom.clone();
            shadowKnown = other.shadowKnown;
            shadowColor = other.shadowColor;
            shadowX = other.shadowX;
            shadowY = other.shadowY;
            shadowRadius = other.shadowRadius;
            shadowAlpha = other.shadowAlpha;
        }
    }
}
