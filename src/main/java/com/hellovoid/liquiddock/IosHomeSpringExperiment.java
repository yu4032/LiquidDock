package com.hellovoid.liquiddock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Opt-in HOME-only spring experiment inspired by Stealth2115/Ios26-anim.
 *
 * <p>The iOS tweak changes SpringBoard response/damping globally and adds a full-screen blur.
 * Here we preserve Launcher's RectFSpringAnim, its live window leash, icon target, gesture
 * velocity and shell completion. Only existing per-instance SpringForce values are adjusted.
 * Missing or incompatible vendor internals are a no-op, never a second animation producer.</p>
 */
final class IosHomeSpringExperiment {
    private static final String TAG = "[DC][IosHomeSpring]";
    // Experimental transfer of the tweak's 0.90 / 0.60 feel, NOT iOS 26 timings.
    // 0.90 is a relative stiffness multiplier here; iOS response uses different units.
    static final float STIFFNESS_SCALE = 0.90f;
    static final float DAMPING_RATIO = 0.60f;
    private static final Map<Object, List<SpringSnapshot>> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean enabled;

    private IosHomeSpringExperiment() {}

    static synchronized void configure(boolean nextEnabled) {
        if (enabled && !nextEnabled) {
            for (List<SpringSnapshot> snapshots : new ArrayList<>(ACTIVE.values())) {
                restore(snapshots);
            }
            ACTIVE.clear();
        }
        enabled = nextEnabled;
    }

    static synchronized void onHomeStarted(Object animation) {
        if (!enabled || animation == null || ACTIVE.containsKey(animation)) return;
        List<SpringSnapshot> snapshots = new ArrayList<>();
        // HyperOS RectFSpringAnim owns SpringForce fields directly through SpringBundle /
        // MultiSpringDynamicAnimation; AOSP RectFSpringAnim may instead own SpringAnimation.
        // Deduplicate by identity so aliases never apply the stiffness multiplier twice.
        java.util.IdentityHashMap<Object, Boolean> seenForces = new java.util.IdentityHashMap<>();
        Class<?> owner = animation.getClass();
        for (Class<?> type = owner; type != null && type != Object.class;
                type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String fieldType = field.getType().getSimpleName();
                boolean directForce = "SpringForce".equals(fieldType)
                        && isMorphForceField(field.getName());
                boolean wrappedForce = "SpringAnimation".equals(fieldType);
                if (!directForce && !wrappedForce) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(animation);
                    if (value == null) continue;
                    Object force;
                    if (directForce) {
                        force = value;
                    } else {
                        Method getSpring = value.getClass().getMethod("getSpring");
                        getSpring.setAccessible(true);
                        force = getSpring.invoke(value);
                    }
                    if (force == null || seenForces.put(force, Boolean.TRUE) != null) continue;
                    SpringSnapshot snapshot = SpringSnapshot.capture(force);
                    if (snapshot != null && snapshot.apply()) snapshots.add(snapshot);
                } catch (Throwable ignored) {
                    // Unknown Launcher internals must leave its original spring untouched.
                }
            }
        }
        if (snapshots.isEmpty()) {
            MainHook.log(TAG + " no compatible per-instance SpringForce found; native HOME kept");
            return;
        }
        ACTIVE.put(animation, snapshots);
        MainHook.log(TAG + " HOME springs tuned=" + snapshots.size()
                + " damping=" + DAMPING_RATIO + " stiffnessScale=" + STIFFNESS_SCALE);
    }

    private static boolean isMorphForceField(String fieldName) {
        // Do not modify alpha: Launcher owns the exact window -> floating-icon handoff.
        return fieldName != null
                && (fieldName.startsWith("mRect") && fieldName.endsWith("Force")
                        || "mRadiusForce".equals(fieldName));
    }

    static synchronized void onHomeFinished(Object animation) {
        if (animation == null) return;
        restore(ACTIVE.remove(animation));
    }

    private static void restore(List<SpringSnapshot> snapshots) {
        if (snapshots == null) return;
        for (SpringSnapshot snapshot : snapshots) snapshot.restore();
    }

    private static final class SpringSnapshot {
        private final Object force;
        private final Method stiffnessSetter;
        private final Method dampingSetter;
        private final float stiffness;
        private final float damping;

        private SpringSnapshot(Object force, Method stiffnessSetter, Method dampingSetter,
                float stiffness, float damping) {
            this.force = force;
            this.stiffnessSetter = stiffnessSetter;
            this.dampingSetter = dampingSetter;
            this.stiffness = stiffness;
            this.damping = damping;
        }

        static SpringSnapshot capture(Object force) {
            try {
                Class<?> type = force.getClass();
                Method getStiffness = type.getMethod("getStiffness");
                Method getDamping = type.getMethod("getDampingRatio");
                Method setStiffness = type.getMethod("setStiffness", float.class);
                Method setDamping = type.getMethod("setDampingRatio", float.class);
                getStiffness.setAccessible(true);
                getDamping.setAccessible(true);
                setStiffness.setAccessible(true);
                setDamping.setAccessible(true);
                Object s = getStiffness.invoke(force);
                Object d = getDamping.invoke(force);
                if (!(s instanceof Number) || !(d instanceof Number)) return null;
                float stiffness = ((Number) s).floatValue();
                float damping = ((Number) d).floatValue();
                if (!Float.isFinite(stiffness) || stiffness <= 0f
                        || !Float.isFinite(damping) || damping < 0f) return null;
                float target = stiffness * STIFFNESS_SCALE;
                if (!Float.isFinite(target) || target <= 0f) return null;
                return new SpringSnapshot(force, setStiffness, setDamping, stiffness, damping);
            } catch (Throwable ignored) {
                return null;
            }
        }

        boolean apply() {
            try {
                stiffnessSetter.invoke(force, stiffness * STIFFNESS_SCALE);
                dampingSetter.invoke(force, DAMPING_RATIO);
                return true;
            } catch (Throwable ignored) {
                restore();
                return false;
            }
        }

        void restore() {
            try { stiffnessSetter.invoke(force, stiffness); }
            catch (Throwable ignored) {}
            try { dampingSetter.invoke(force, damping); }
            catch (Throwable ignored) {}
        }
    }
}
