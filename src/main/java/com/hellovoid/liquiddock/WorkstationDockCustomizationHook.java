package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;
import java.lang.ref.WeakReference;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Workstation icon placement. Taskbar alignment maps the native icon graphic through its
 * complete view transform; the user's offset remains an additional row-local adjustment.
 */
final class WorkstationDockCustomizationHook {
    private static volatile boolean dockEnabled;
    private static volatile boolean taskbarEnabled;
    private static volatile int iconTopOffset;
    private static volatile int iconBottomOffset;
    private static final WeakHashMap<View, RowTranslation> OBSERVED_RECYCLERS =
            new WeakHashMap<>();

    private WorkstationDockCustomizationHook() {}

    static void applyLiveConfig(LiquidDockConfig.Workstation config) {
        if (config == null) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int top = Math.round(config.iconTopOffset * scale);
        int bottom = Math.round(config.iconBottomOffset * scale);
        boolean nextTaskbar = config.dockEnabled && config.taskbarMode;
        if (dockEnabled == config.dockEnabled && taskbarEnabled == nextTaskbar
                && iconTopOffset == top && iconBottomOffset == bottom) return;

        dockEnabled = config.dockEnabled;
        taskbarEnabled = nextTaskbar;
        iconTopOffset = top;
        iconBottomOffset = bottom;
        ArrayList<View> observed;
        synchronized (OBSERVED_RECYCLERS) {
            observed = new ArrayList<>(OBSERVED_RECYCLERS.keySet());
        }
        for (View recycler : observed) {
            if (recycler == null) continue;
            applyRowTranslation(recycler);
            if (!recycler.isAttachedToWindow()) continue;
            // Item decorations own the old top/bottom insets in normal mode only.
            HookUtil.tryInvoke(recycler, "invalidateItemDecorations");
            recycler.requestLayout();
        }
    }

    static void onWorkstationModeChanged(boolean active) {
        synchronized (OBSERVED_RECYCLERS) {
            for (java.util.Map.Entry<View, RowTranslation> entry
                    : OBSERVED_RECYCLERS.entrySet()) {
                View recycler = entry.getKey();
                RowTranslation state = entry.getValue();
                if (recycler != null && state != null) {
                    state.apply(recycler, active && dockEnabled && taskbarEnabled,
                            iconTopOffset);
                }
            }
        }
    }

    private static void applyRowTranslation(View recycler) {
        synchronized (OBSERVED_RECYCLERS) {
            RowTranslation state = OBSERVED_RECYCLERS.get(recycler);
            if (state != null) {
                state.apply(recycler,
                        dockEnabled && taskbarEnabled && WorkstationRuntimeState.isActive(),
                        iconTopOffset);
            }
        }
    }

    private static void observeRecycler(View recycler) {
        synchronized (OBSERVED_RECYCLERS) {
            if (OBSERVED_RECYCLERS.containsKey(recycler)) return;
            OBSERVED_RECYCLERS.put(recycler, new RowTranslation());
            recycler.addOnLayoutChangeListener(
                    (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                        // Recalibrate optical centering only when native layout changes,
                        // never on an ordinary native translation-animation frame.
                        RowTranslation state = OBSERVED_RECYCLERS.get(view);
                        if (state != null) state.invalidateRestAlignment();
                        applyRowTranslation(view);
                    });
            recycler.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    installPreDrawAlignment(view);
                    view.post(() -> applyRowTranslation(view));
                }

                @Override public void onViewDetachedFromWindow(View view) {
                    removePreDrawAlignment(view);
                    applyRowTranslationForDetach(view);
                }
            });
        }
        installPreDrawAlignment(recycler);
        // Wait until RecyclerView finishes its initial layout; translation does not
        // trigger remeasurement, so changing it cannot accumulate item inset changes.
        recycler.post(() -> applyRowTranslation(recycler));
    }

    private static void installPreDrawAlignment(View recycler) {
        synchronized (OBSERVED_RECYCLERS) {
            RowTranslation state = OBSERVED_RECYCLERS.get(recycler);
            if (state == null || !recycler.isAttachedToWindow() || state.observer != null) return;
            WeakReference<View> reference = new WeakReference<>(recycler);
            state.preDraw = () -> {
                View row = reference.get();
                if (row != null && taskbarEnabled) applyRowTranslation(row);
                return true;
            };
            state.observer = recycler.getViewTreeObserver();
            state.observer.addOnPreDrawListener(state.preDraw);
        }
    }

    private static void removePreDrawAlignment(View recycler) {
        synchronized (OBSERVED_RECYCLERS) {
            RowTranslation state = OBSERVED_RECYCLERS.get(recycler);
            if (state == null) return;
            if (state.observer != null && state.observer.isAlive())
                state.observer.removeOnPreDrawListener(state.preDraw);
            state.observer = null;
            state.preDraw = null;
        }
    }

    private static void applyRowTranslationForDetach(View recycler) {
        synchronized (OBSERVED_RECYCLERS) {
            RowTranslation state = OBSERVED_RECYCLERS.get(recycler);
            if (state != null) state.apply(recycler, false, 0);
        }
    }

    private static final class RowTranslation {
        private float nativeTranslation = Float.NaN;
        private float lastAppliedTranslation = Float.NaN;
        private float stableCorrection = Float.NaN;
        private boolean vendorMotionActive;
        private int alignedUserOffset;

        void invalidateRestAlignment() {
            stableCorrection = Float.NaN;
            vendorMotionActive = false;
        }
        private ViewTreeObserver observer;
        private ViewTreeObserver.OnPreDrawListener preDraw;
        private final Matrix matrix = new Matrix();
        private final float[] points = new float[4];
        private final int[] screenPosition = new int[2];

        private View findGraphic(View view, int depth) {
            if (depth > 4 || view.getVisibility() != View.VISIBLE || view.getAlpha() <= 0f
                    || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
            if (view instanceof TextView) {
                Drawable[] icons = ((TextView) view).getCompoundDrawables();
                if (icons[1] != null) return view;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) {
                    View found = findGraphic(group.getChildAt(i), depth + 1);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private float graphicScreenCenter(View graphic) {
            LauncherGlassIconGeometry.Bounds bounds = LauncherGlassIconGeometry.resolve(graphic);
            if (bounds == null) return Float.NaN;
            matrix.reset();
            graphic.transformMatrixToGlobal(matrix);
            points[0] = 0f; points[1] = 0f;
            points[2] = (bounds.left + bounds.right) * .5f;
            points[3] = (bounds.top + bounds.bottom) * .5f;
            matrix.mapPoints(points);
            float center = points[3];
            View root = graphic.getRootView();
            matrix.reset();
            root.transformMatrixToGlobal(matrix);
            points[0] = 0f; points[1] = 0f;
            matrix.mapPoints(points, 0, points, 0, 1);
            root.getLocationOnScreen(screenPosition);
            // Only round the stable window origin. Rounding the moving icon's origin
            // would make half-pixel targets alternate corrections on successive frames.
            return center + screenPosition[1] - points[1];
        }

        void apply(View recycler, boolean taskbar, int offsetPx) {
            float current = recycler.getTranslationY();
            if (!taskbar) {
                if (Float.isFinite(lastAppliedTranslation)
                        && Math.abs(current - lastAppliedTranslation) < 0.5f) {
                    recycler.setTranslationY(nativeTranslation);
                }
                nativeTranslation = Float.NaN;
                lastAppliedTranslation = Float.NaN;
                invalidateRestAlignment();
                return;
            }
            // A vendor animation that writes translationY is authoritative. Do not
            // recalculate against a fixed visual target on each following frame;
            // merely reapply the stable optical/user offset to the new vendor value.
            if (Float.isFinite(lastAppliedTranslation)
                    && Math.abs(current - lastAppliedTranslation) >= 0.5f) {
                nativeTranslation = current;
                if (Float.isFinite(stableCorrection)) vendorMotionActive = true;
            } else if (!Float.isFinite(nativeTranslation)) {
                nativeTranslation = current;
            }
            if (vendorMotionActive && Float.isFinite(stableCorrection)) {
                float animated = WorkstationTaskbarGeometry.animatedIconRowTranslation(
                        current, stableCorrection, offsetPx, alignedUserOffset);
                if (Math.abs(current - animated) >= 0.5f) recycler.setTranslationY(animated);
                stableCorrection = animated - current;
                alignedUserOffset = offsetPx;
                lastAppliedTranslation = animated;
                return;
            }
            View graphic = findGraphic(recycler, 0);
            if (graphic == null) return;
            float center = graphicScreenCenter(graphic);
            matrix.reset();
            if (!(recycler.getParent() instanceof View)) return;
            ((View) recycler.getParent()).transformMatrixToGlobal(matrix);
            points[0] = 0f; points[1] = 0f; points[2] = 0f; points[3] = 1f;
            matrix.mapPoints(points);
            float target = WorkstationTaskbarGeometry.centeredIconRowTranslation(
                    current, Miuix307ZeroCopyRenderer.taskbarCenterFor(recycler), center,
                    points[3] - points[1], offsetPx);
            if (Math.abs(current - target) >= 0.5f) recycler.setTranslationY(target);
            stableCorrection = target - current;
            alignedUserOffset = offsetPx;
            lastAppliedTranslation = target;
        }
    }

    static void install(ClassLoader classLoader, LiquidDockConfig.Workstation config) {
        applyLiveConfig(config);
        try {
            Class<?> recyclerView = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView", false, classLoader);
            Class<?> recyclerState = Class.forName(
                    "androidx.recyclerview.widget.RecyclerView$State", false, classLoader);
            HookUtil.hookMethod(
                    classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentLayoutManager$OffsetDecoration",
                    "getItemOffsets",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object recycler = chain.getArg(2);
                        if (recycler instanceof View) observeRecycler((View) recycler);
                        if (dockEnabled && WorkstationRuntimeState.isActive() && !taskbarEnabled) {
                            Rect out = (Rect) chain.getArg(0);
                            out.top += iconTopOffset;
                            out.bottom += iconBottomOffset;
                        }
                        return result;
                    },
                    Rect.class, View.class, recyclerView, recyclerState);
            MainHook.log("[DC] workstation Dock icon offsets top="
                    + iconTopOffset + " bottom=" + iconBottomOffset);
        } catch (Throwable error) {
            MainHook.log("[DC] workstation Dock icon offset hook unavailable: " + error);
        }
    }
}
