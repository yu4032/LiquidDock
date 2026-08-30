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
 * custom foreground stroke is committed only after the current radius animator completes AND the
 * real native Dock geometry has become usable. Device logs show Launcher can emit an early 0-radius
 * update before the settled ordinary Dock exists, so animator completion alone is insufficient.
 */
final class Launcher450DockTransitionSettlement {
    private static final String BACKGROUND_CLASS =
            "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2";
    private static final Map<Animator, Integer> OBSERVED =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;
    private static int lastDeferredGeneration = Integer.MIN_VALUE;

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
                        if (owner instanceof View && settleIfReady((View) owner)) {
                            commitSettledGeometry((View) owner);
                        }
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

    /**
     * Returns true only when this call changes the visual phase from EXITING_WORKSTATION to NORMAL.
     * Callers already inside syncGeometry can then commit the stroke in the same frame; hook and
     * animator-listener callers use commitSettledGeometry() after this returns true.
     */
    static boolean settleIfReady(View background) {
        DockWorkstationVisualTransition state = DockWorkstationVisualTransition.global();
        if (!state.isExiting()) return false;
        final int generation = state.generation();

        if (!MiuixGlassHook.hasReadyNativeGeometry(background)) {
            if (lastDeferredGeneration != generation) {
                lastDeferredGeneration = generation;
                MainHook.log("[DC] workstation exit settlement deferred generation=" + generation
                        + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background)
                        + " size=" + background.getWidth() + "x" + background.getHeight());
            }
            return false;
        }

        Animator radiusAnimator = readAnimator(background, "mViewRadiusAnimator");
        if (radiusAnimator != null && radiusAnimator.isRunning()) {
            observeAnimator(background, radiusAnimator, generation);
            return false;
        }

        Animator animatorSet = readAnimator(background, "animatorSet");
        if (animatorSet != null && animatorSet.isRunning()) return false;

        if (!state.settleExit(generation)) return false;
        lastDeferredGeneration = Integer.MIN_VALUE;
        MainHook.log("[DC] workstation exit Dock geometry settled generation=" + generation
                + " radius=" + MiuixGlassHook.readNativeOpticsRadius(background)
                + " size=" + background.getWidth() + "x" + background.getHeight());
        return true;
    }

    private static void observeAnimator(
            View background, Animator radiusAnimator, int generation) {
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
                if (!cancelled && settleIfReady(background)) {
                    commitSettledGeometry(background);
                }
            }
        });
    }

    private static Animator readAnimator(View background, String fieldName) {
        try {
            Object value = HookUtil.getField(background, fieldName);
            return value instanceof Animator ? (Animator) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void commitSettledGeometry(View background) {
        try {
            LiquidDockConfig current = LiquidDockConfig.load();
            MiuixGlassHook.syncGeometry(background, current);
            MainHook.syncDockShadow(background, current.dock);
        } catch (Throwable error) {
            MainHook.log("[DC] workstation exit Dock geometry settlement failed: " + error);
        }
    }
}
