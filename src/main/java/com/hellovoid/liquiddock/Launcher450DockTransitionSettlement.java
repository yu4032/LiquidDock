package com.hellovoid.liquiddock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Launcher 4.50-specific settlement boundary for workstation -> normal Dock geometry.
 *
 * BlurBackground2.updateBackgroundSize(...) creates/replaces mViewRadiusAnimator while the vendor
 * shell is leaving laptop mode. LiquidDock lets Prismal follow those intermediate values, but the
 * custom foreground stroke is committed only after the current radius animator completes.
 */
final class Launcher450DockTransitionSettlement {
    private static final String BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final Map<Animator, Integer> OBSERVED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private Launcher450DockTransitionSettlement() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        installed = true;
        try {
            Class<?> backgroundClass = Class.forName(BACKGROUND_CLASS, false, classLoader);
            int hooked = 0;
            Class<?> cursor = backgroundClass;
            while (cursor != null && cursor != Object.class) {
                for (Method method : cursor.getDeclaredMethods()) {
                    if (!"updateBackgroundSize".equals(method.getName())
                            || Modifier.isStatic(method.getModifiers())) continue;
                    HookUtil.hook(method, chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) observe((View) owner);
                        return result;
                    });
                    hooked++;
                }
                cursor = cursor.getSuperclass();
            }
            MainHook.log("[DC] Launcher 4.50 Dock transition settlement hooks=" + hooked);
        } catch (Throwable error) {
            MainHook.log("[DC] Launcher 4.50 Dock transition settlement unavailable: " + error);
        }
    }

    private static void observe(View background) {
        DockWorkstationVisualTransition state = DockWorkstationVisualTransition.global();
        if (!state.isExiting()) return;
        final int generation = state.generation();

        Animator radiusAnimator = readAnimator(background, "mViewRadiusAnimator");
        if (radiusAnimator != null && radiusAnimator.isRunning()) {
            synchronized (OBSERVED) {
                Integer prior = OBSERVED.get(radiusAnimator);
                if (prior != null && prior == generation) return;
                OBSERVED.put(radiusAnimator, generation);
            }
            radiusAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override public void onAnimationEnd(Animator animation) {
                    synchronized (OBSERVED) { OBSERVED.remove(animation); }
                    if (!cancelled) settleExit(background, generation);
                }
            });
            return;
        }

        // With resize animation disabled Launcher ends mViewRadiusAnimator synchronously inside
        // updateBackgroundSize. At this point mCornerRadius is already the final vendor value.
        Animator animatorSet = readAnimator(background, "animatorSet");
        if (animatorSet == null || !animatorSet.isRunning()) settleExit(background, generation);
    }

    private static Animator readAnimator(View background, String fieldName) {
        try {
            Object value = HookUtil.getField(background, fieldName);
            return value instanceof Animator ? (Animator) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void settleExit(View background, int generation) {
        DockWorkstationVisualTransition state = DockWorkstationVisualTransition.global();
        if (!state.settleExit(generation)) return;
        try {
            LiquidDockConfig current = LiquidDockConfig.load();
            MiuixGlassHook.syncGeometry(background, current);
            MainHook.syncDockShadow(background, current.dock);
            MainHook.log("[DC] workstation exit Dock geometry settled generation=" + generation
                    + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background));
        } catch (Throwable error) {
            MainHook.log("[DC] workstation exit Dock geometry settlement failed: " + error);
        }
    }
}
