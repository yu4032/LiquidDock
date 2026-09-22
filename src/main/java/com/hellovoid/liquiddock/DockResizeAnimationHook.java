package com.hellovoid.liquiddock;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.WeakHashMap;

/** Owns the optional replacement Dock resize animation and its per-view animator lifecycle. */
final class DockResizeAnimationHook {
    private static final WeakHashMap<View, ValueAnimator> ANIMATORS = new WeakHashMap<>();

    private DockResizeAnimationHook() {}

    static void install(
            ClassLoader classLoader, boolean smoothAnimation, int durationMs) {
        try {
            HookUtil.hookMethod(
                    classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentBlurBackground2",
                    "updateBackgroundSize",
                    chain -> {
                        int oldW = 0;
                        int oldH = 0;
                        float oldR = 0f;
                        try {
                            oldW = HookUtil.getIntField(chain.getThisObject(), "mWidth");
                            oldH = HookUtil.getIntField(chain.getThisObject(), "mHeight");
                            Object radius = HookUtil.getField(
                                    chain.getThisObject(), "mCornerRadius");
                            if (radius instanceof Float) oldR = (Float) radius;
                        } catch (Throwable ignored) {}

                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        try {
                            Object set = HookUtil.getField(chain.getThisObject(), "animatorSet");
                            if (set instanceof Animator) ((Animator) set).end();
                            Object radiusAnimator = HookUtil.getField(
                                    chain.getThisObject(), "mViewRadiusAnimator");
                            if (radiusAnimator instanceof Animator) {
                                ((Animator) radiusAnimator).end();
                            }
                        } catch (Throwable ignored) {}

                        View view = (View) chain.getThisObject();
                        if (smoothAnimation) {
                            animateFromPrevious(view, oldW, oldH, oldR, durationMs);
                        } else {
                            DockCustomizationHook.syncAll(view);
                        }
                        return result;
                    },
                    int.class, int.class, float.class);
            MainHook.log("[DC] Dock resize animation disabled");
        } catch (Throwable error) {
            MainHook.log("[DC] Dock resize animation bypass unavailable: " + error);
        }
    }

    private static void animateFromPrevious(
            View view, int startW, int startH, float startR, int durationMs) {
        try {
            int targetW = HookUtil.getIntField(view, "mWidth");
            int targetH = HookUtil.getIntField(view, "mHeight");
            float targetR = ((Number) HookUtil.getField(view, "mCornerRadius")).floatValue();
            synchronized (ANIMATORS) {
                ValueAnimator previous = ANIMATORS.remove(view);
                if (previous != null) {
                    startW = HookUtil.getIntField(view, "mWidth");
                    startH = HookUtil.getIntField(view, "mHeight");
                    startR = ((Number) HookUtil.getField(view, "mCornerRadius")).floatValue();
                    previous.cancel();
                }
                if (startW == targetW && startH == targetH
                        && Math.abs(startR - targetR) < .01f) {
                    DockCustomizationHook.syncAll(view);
                    return;
                }

                final int fromW = startW;
                final int fromH = startH;
                final float fromR = startR;
                HookUtil.setIntField(view, "mWidth", fromW);
                HookUtil.setIntField(view, "mHeight", fromH);
                HookUtil.setField(view, "mCornerRadius", fromR);

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setDuration(durationMs);
                animator.setInterpolator(new PathInterpolator(.2f, 0f, 0f, 1f));
                animator.addUpdateListener(valueAnimator -> {
                    float t = (Float) valueAnimator.getAnimatedValue();
                    HookUtil.setIntField(
                            view, "mWidth", Math.round(fromW + (targetW - fromW) * t));
                    HookUtil.setIntField(
                            view, "mHeight", Math.round(fromH + (targetH - fromH) * t));
                    HookUtil.setField(
                            view, "mCornerRadius", fromR + (targetR - fromR) * t);
                    HookUtil.tryInvoke(view, "triggerMeasure");
                    view.requestLayout();
                    DockCustomizationHook.syncAll(view);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        synchronized (ANIMATORS) {
                            ANIMATORS.remove(view);
                        }
                    }
                });
                ANIMATORS.put(view, animator);
                animator.start();
            }
        } catch (Throwable error) {
            DockCustomizationHook.syncAll(view);
            MainHook.log("[DC] smooth Dock resize failed: " + error);
        }
    }
}
