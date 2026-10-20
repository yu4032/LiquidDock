package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Workstation Dock icon spacing and taskbar optical centering.
 *
 * Dock background and its icon RecyclerView may belong to separate ViewRoots. Both use
 * screen-space bounds; a shared root identity is not a valid ownership requirement.
 * Align during pre-draw AFTER the native layouts/parent transforms settle. Do not depend
 * on a one-shot postOnAnimation request during the asynchronous rotation handoff.
 */
final class WorkstationDockCustomizationHook {
    private static volatile boolean dockEnabled;
    private static volatile boolean taskbarEnabled;
    private static volatile int iconTopOffset;
    private static volatile int iconBottomOffset;
    private static final WeakHashMap<View, RowAlignment> OBSERVED_RECYCLERS =
            new WeakHashMap<>();

    private static final View.OnAttachStateChangeListener ROW_ATTACH_LISTENER =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    // Workstation and its floating Dock may attach in either order.
                    view.postOnAnimation(
                            WorkstationDockCustomizationHook::reconcileTaskbarRowsForFrame);
                }

                @Override public void onViewDetachedFromWindow(View view) {
                    synchronized (OBSERVED_RECYCLERS) {
                        RowAlignment row = OBSERVED_RECYCLERS.get(view);
                        if (row != null) row.restore(view);
                    }
                }
            };

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
            if (!nextTaskbar || !dockEnabled) {
                synchronized (OBSERVED_RECYCLERS) {
                    RowAlignment row = OBSERVED_RECYCLERS.get(recycler);
                    if (row != null) row.restore(recycler);
                }
            }
            if (!recycler.isAttachedToWindow()) continue;
            HookUtil.tryInvoke(recycler, "invalidateItemDecorations");
            recycler.requestLayout();
        }
    }

    /** Reset the row when exiting Workstation even if the settings keys did not change. */
    static void onWorkstationModeChanged(boolean active) {
        if (active) return;
        synchronized (OBSERVED_RECYCLERS) {
            for (java.util.Map.Entry<View, RowAlignment> entry : OBSERVED_RECYCLERS.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    entry.getValue().restore(entry.getKey());
                }
            }
        }
    }

    /**
     * Called by the existing Dock GPU host pre-draw observer after the native Dock position is
     * reconciled. No posts, timers or extra layout passes; correct only if geometry differs.
     */
    static void reconcileTaskbarRowsForFrame() {
        if (!dockEnabled || !taskbarEnabled || !WorkstationRuntimeState.isActive()) return;
        // This runs at the display refresh rate: iterate the weak registry directly and
        // retain per-row scratch buffers rather than allocating on every Prismal frame.
        synchronized (OBSERVED_RECYCLERS) {
            for (Map.Entry<View, RowAlignment> entry : OBSERVED_RECYCLERS.entrySet()) {
                View recycler = entry.getKey();
                RowAlignment row = entry.getValue();
                if (recycler == null || row == null || !recycler.isAttachedToWindow()) continue;
                float bodyCenter = Miuix307ZeroCopyRenderer.taskbarVisualCenterFor(recycler);
                if (!Float.isFinite(bodyCenter)) continue;
                float iconCenter = iconRowCenterOnScreen(recycler, row);
                if (!Float.isFinite(iconCenter)) continue;
                row.align(recycler, bodyCenter, iconCenter);
            }
        }
    }

    private static void observeRecycler(View recycler) {
        synchronized (OBSERVED_RECYCLERS) {
            if (OBSERVED_RECYCLERS.containsKey(recycler)) return;
            OBSERVED_RECYCLERS.put(recycler, new RowAlignment());
            recycler.addOnAttachStateChangeListener(ROW_ATTACH_LISTENER);
        }
        // The first item decoration may happen after this window's own pre-draw.
        recycler.postOnAnimation(WorkstationDockCustomizationHook::reconcileTaskbarRowsForFrame);
    }

    /** Median icon graphic center ignores the occasional dragging/animating item. */
    private static float iconRowCenterOnScreen(View recycler, RowAlignment row) {
        if (!(recycler instanceof ViewGroup)) return Float.NaN;
        ViewGroup group = (ViewGroup) recycler;
        int childCount = group.getChildCount();
        if (childCount == 0) return Float.NaN;
        row.ensureCapacity(childCount);
        float[] centers = row.centers;
        int count = 0;
        int[] position = row.screenPosition;
        float minWidth = 8f * recycler.getResources().getDisplayMetrics().density;
        for (int i = 0; i < childCount; i++) {
            View child = group.getChildAt(i);
            if (!child.isAttachedToWindow() || child.getVisibility() != View.VISIBLE
                    || child.getAlpha() <= 0f || child.getWidth() < minWidth
                    || child.getHeight() <= 0) continue;
            child.getLocationOnScreen(position);
            LauncherGlassIconGeometry.Bounds graphic = LauncherGlassIconGeometry.resolve(child);
            centers[count++] = position[1] + (graphic == null
                    ? child.getHeight() * 0.5f
                    : (graphic.top + graphic.bottom) * 0.5f);
        }
        if (count == 0) return Float.NaN;
        Arrays.sort(centers, 0, count);
        return (count & 1) == 1
                ? centers[count / 2]
                : 0.5f * (centers[count / 2 - 1] + centers[count / 2]);
    }

    private static final class RowAlignment {
        private float originalTranslationY = Float.NaN;
        private float appliedTranslationY = Float.NaN;
        private float[] centers = new float[0];
        private final int[] screenPosition = new int[2];

        void ensureCapacity(int childCount) {
            if (centers.length < childCount) centers = new float[childCount];
        }

        void align(View recycler, float targetCenter, float observedCenter) {
            float current = recycler.getTranslationY();
            if (!Float.isFinite(originalTranslationY)
                    || (Float.isFinite(appliedTranslationY)
                        && Math.abs(current - appliedTranslationY) > 0.5f)) {
                // If Launcher takes translation ownership during a rotation/animation,
                // regard the new vendor value as the baseline, not our previous result.
                originalTranslationY = current;
            }
            float delta = WorkstationTaskbarGeometry.iconCenterCorrection(
                    targetCenter, observedCenter);
            if (Math.abs(delta) < 1f) return;
            float next = current + delta;
            recycler.setTranslationY(next);
            appliedTranslationY = next;
        }

        void restore(View recycler) {
            float current = recycler.getTranslationY();
            if (Float.isFinite(originalTranslationY)
                    && Float.isFinite(appliedTranslationY)
                    && Math.abs(current - appliedTranslationY) <= 0.5f) {
                recycler.setTranslationY(originalTranslationY);
            }
            originalTranslationY = Float.NaN;
            appliedTranslationY = Float.NaN;
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
                        // Taskbar auto-centering supersedes the manual icon-decoration offsets.
                        if (dockEnabled && WorkstationRuntimeState.isActive() && !taskbarEnabled) {
                            Rect out = (Rect) chain.getArg(0);
                            out.top += iconTopOffset;
                            out.bottom += iconBottomOffset;
                        }
                        return result;
                    },
                    Rect.class, View.class, recyclerView, recyclerState);
            MainHook.log("[DC] workstation Dock icon vertical offsets top="
                    + iconTopOffset + " bottom=" + iconBottomOffset);
        } catch (Throwable error) {
            MainHook.log("[DC] workstation Dock icon offset hook unavailable: " + error);
        }
    }
}
