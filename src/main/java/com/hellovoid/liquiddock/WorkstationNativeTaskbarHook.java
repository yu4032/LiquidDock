package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Point;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Owns the native material's taskbar shape while the replacement glass renderer is disabled. */
final class WorkstationNativeTaskbarHook {
    private static final WeakHashMap<View, WeakReference<Binding>> BINDINGS = new WeakHashMap<>();
    private static boolean enabled;

    private WorkstationNativeTaskbarHook() {}

    static void applyLiveConfig(LiquidDockConfig.Workstation config) {
        enabled = config != null && config.dockEnabled && config.taskbarMode;
        refresh();
    }

    static void refresh() {
        ArrayList<Binding> snapshot = new ArrayList<>();
        synchronized (BINDINGS) {
            for (WeakReference<Binding> reference : BINDINGS.values()) {
                Binding binding = reference.get();
                if (binding != null) snapshot.add(binding);
            }
        }
        for (Binding binding : snapshot) binding.apply();
    }

    static void bind(View background) {
        synchronized (BINDINGS) {
            WeakReference<Binding> reference = BINDINGS.get(background);
            if (reference != null && reference.get() != null) return;
            Binding binding = new Binding(background);
            BINDINGS.put(background, new WeakReference<>(binding));
            background.addOnAttachStateChangeListener(binding);
            binding.attach(background);
        }
    }

    private static final class Binding implements View.OnAttachStateChangeListener,
            ViewTreeObserver.OnPreDrawListener {
        private final WeakReference<View> reference;
        private final Matrix matrix = new Matrix();
        private final float[] points = new float[4];
        private final Point display = new Point();
        private final int[] position = new int[2];
        private ViewTreeObserver observer;
        private float nativeScale = Float.NaN, lastScale = Float.NaN;
        private float nativeX = Float.NaN, lastX = Float.NaN;
        private float nativeRadius = Float.NaN;
        private ViewOutlineProvider nativeOutline;
        private boolean applied;
        private final ViewOutlineProvider taskbarOutline = new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRect(0, 0, view.getWidth(), view.getHeight());
            }
        };

        Binding(View background) { reference = new WeakReference<>(background); }

        void attach(View view) {
            if (observer != null || !view.isAttachedToWindow()) return;
            observer = view.getViewTreeObserver();
            observer.addOnPreDrawListener(this);
        }

        @Override public void onViewAttachedToWindow(View view) { attach(view); }
        @Override public void onViewDetachedFromWindow(View view) {
            restore(view);
            if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
            observer = null;
        }
        @Override public boolean onPreDraw() { apply(); return true; }

        void apply() {
            View view = reference.get();
            if (view == null) return;
            if (!enabled || !WorkstationRuntimeState.isActive() || GlassRuntimeState.isEnabled()) {
                restore(view);
                return;
            }
            if (!view.isAttachedToWindow() || view.getDisplay() == null
                    || view.getWidth() <= 0 || view.getHeight() <= 0) return;
            float scale = view.getScaleX();
            float translation = view.getTranslationX();
            if (!same(scale, lastScale)) nativeScale = scale;
            if (!same(translation, lastX)) nativeX = translation;
            matrix.reset();
            view.transformMatrixToGlobal(matrix);
            points[0] = 0f; points[1] = 0f;
            points[2] = view.getWidth(); points[3] = 0f;
            matrix.mapPoints(points);
            float screenWidth = Math.abs(points[2] - points[0]);
            if (screenWidth <= .001f || scale <= .001f) return;
            float parentScale = screenWidth / (view.getWidth() * scale);
            view.getDisplay().getRealSize(display);
            float targetScale = WorkstationNativeTaskbarGeometry.scaleX(
                    scale, view.getWidth(), screenWidth, display.x);
            if (!same(scale, targetScale)) view.setScaleX(targetScale);
            lastScale = targetScale;
            view.getLocationOnScreen(position);
            float targetX = WorkstationNativeTaskbarGeometry.translationX(
                    translation, position[0], parentScale);
            if (!same(translation, targetX)) view.setTranslationX(targetX);
            lastX = targetX;

            HookUtil.InvocationResult<Object> radius = HookUtil.tryInvoke(view, "getBackgroundRadius");
            if (radius.succeeded() && radius.value() instanceof Number) {
                float observed = ((Number) radius.value()).floatValue();
                if (!applied || observed != 0f) nativeRadius = observed;
                if (observed != 0f) HookUtil.tryInvoke(view, "setBackgroundRadius", 0f);
            }
            if (view.getOutlineProvider() != taskbarOutline) {
                nativeOutline = view.getOutlineProvider();
                view.setOutlineProvider(taskbarOutline);
            }
            applied = true;
        }

        private void restore(View view) {
            if (!applied) return;
            if (same(view.getScaleX(), lastScale)) view.setScaleX(nativeScale);
            if (same(view.getTranslationX(), lastX)) view.setTranslationX(nativeX);
            if (Float.isFinite(nativeRadius)) {
                HookUtil.InvocationResult<Object> radius = HookUtil.tryInvoke(view, "getBackgroundRadius");
                if (radius.succeeded() && radius.value() instanceof Number
                        && ((Number) radius.value()).floatValue() == 0f) {
                    HookUtil.tryInvoke(view, "setBackgroundRadius", nativeRadius);
                }
            }
            if (view.getOutlineProvider() == taskbarOutline) view.setOutlineProvider(nativeOutline);
            nativeScale = lastScale = nativeX = lastX = nativeRadius = Float.NaN;
            nativeOutline = null;
            applied = false;
        }

        private static boolean same(float a, float b) {
            return Float.isFinite(a) && Float.isFinite(b) && Math.abs(a - b) < .001f;
        }
    }
}
