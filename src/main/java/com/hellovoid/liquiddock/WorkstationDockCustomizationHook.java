package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Workstation-only icon offsets. In taskbar mode the configured vertical offset directly
 * translates the complete RecyclerView instead of trying to infer an optical center from
 * icon graphics, screen coordinates or the floating Dock's separate window.
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
                    (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                            applyRowTranslation(view));
            recycler.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    view.post(() -> applyRowTranslation(view));
                }

                @Override public void onViewDetachedFromWindow(View view) {
                    applyRowTranslationForDetach(view);
                }
            });
        }
        // Wait until RecyclerView finishes its initial layout; translation does not
        // trigger remeasurement, so changing it cannot accumulate item inset changes.
        recycler.post(() -> applyRowTranslation(recycler));
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

        void apply(View recycler, boolean taskbar, int offsetPx) {
            float current = recycler.getTranslationY();
            if (!taskbar) {
                if (Float.isFinite(lastAppliedTranslation)
                        && Math.abs(current - lastAppliedTranslation) < 0.5f) {
                    recycler.setTranslationY(nativeTranslation);
                }
                nativeTranslation = Float.NaN;
                lastAppliedTranslation = Float.NaN;
                return;
            }
            // OEM animation/layout is allowed to replace translation. Retain the latest
            // vendor baseline, but never compound our previous offset on repeated calls.
            if (!Float.isFinite(nativeTranslation)
                    || (Float.isFinite(lastAppliedTranslation)
                            && Math.abs(current - lastAppliedTranslation) >= 0.5f)) {
                nativeTranslation = current;
            }
            float target = WorkstationTaskbarGeometry.iconRowTranslation(
                    nativeTranslation, offsetPx);
            if (Math.abs(current - target) >= 0.5f) recycler.setTranslationY(target);
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
