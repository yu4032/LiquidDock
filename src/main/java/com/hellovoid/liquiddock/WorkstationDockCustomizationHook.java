package com.hellovoid.liquiddock;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.WeakHashMap;

/** Workstation HotSeats spacing and reversible taskbar icon-row centering. */
final class WorkstationDockCustomizationHook {
    private static volatile boolean dockEnabled;
    private static volatile boolean taskbarEnabled;
    private static volatile int iconTopOffset;
    private static volatile int iconBottomOffset;
    private static final WeakHashMap<View, RowAlignment> OBSERVED_RECYCLERS =
            new WeakHashMap<>();

    private static final View.OnLayoutChangeListener ROW_LAYOUT_LISTENER =
            (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    scheduleRowAlignment(view);
    private static final View.OnAttachStateChangeListener ROW_ATTACH_LISTENER =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    scheduleRowAlignment(view);
                }

                @Override public void onViewDetachedFromWindow(View view) {
                    synchronized (OBSERVED_RECYCLERS) {
                        RowAlignment row = OBSERVED_RECYCLERS.get(view);
                        if (row != null) row.restore(view);
                    }
                    // Keep the weakly tracked owner across Launcher detach/reattach so the
                    // same RecyclerView re-centers even when no item decoration is rebound.
                }
            };

    private WorkstationDockCustomizationHook() {}

    static void applyLiveConfig(LiquidDockConfig.Workstation config) {
        if (config == null) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int top = Math.round(config.iconTopOffset * scale);
        int bottom = Math.round(config.iconBottomOffset * scale);
        boolean newTaskbar = config.dockEnabled && config.taskbarMode;
        if (dockEnabled == config.dockEnabled && taskbarEnabled == newTaskbar
                && iconTopOffset == top && iconBottomOffset == bottom) return;

        dockEnabled = config.dockEnabled;
        taskbarEnabled = newTaskbar;
        iconTopOffset = top;
        iconBottomOffset = bottom;

        ArrayList<View> observed;
        synchronized (OBSERVED_RECYCLERS) {
            observed = new ArrayList<>(OBSERVED_RECYCLERS.keySet());
        }
        for (View recycler : observed) {
            if (recycler == null || !recycler.isAttachedToWindow()) continue;
            HookUtil.tryInvoke(recycler, "invalidateItemDecorations");
            recycler.requestLayout();
            scheduleRowAlignment(recycler);
        }
    }

    /** Called after DockContainer's bottom translation or workstation-mode handoff. */
    static void refreshTaskbarIconCentering() {
        ArrayList<View> observed;
        synchronized (OBSERVED_RECYCLERS) {
            observed = new ArrayList<>(OBSERVED_RECYCLERS.keySet());
        }
        for (View recycler : observed) scheduleRowAlignment(recycler);
    }

    private static void observeRecycler(View recycler) {
        synchronized (OBSERVED_RECYCLERS) {
            if (!OBSERVED_RECYCLERS.containsKey(recycler)) {
                OBSERVED_RECYCLERS.put(recycler, new RowAlignment());
                recycler.addOnLayoutChangeListener(ROW_LAYOUT_LISTENER);
                recycler.addOnAttachStateChangeListener(ROW_ATTACH_LISTENER);
            }
        }
        if (taskbarEnabled) scheduleRowAlignment(recycler);
    }

    private static void scheduleRowAlignment(View recycler) {
        if (recycler == null) return;
        RowAlignment row;
        synchronized (OBSERVED_RECYCLERS) {
            row = OBSERVED_RECYCLERS.get(recycler);
            if (row == null || row.pending) return;
            row.pending = true;
        }
        recycler.postOnAnimation(() -> {
            synchronized (OBSERVED_RECYCLERS) {
                row.pending = false;
                if (OBSERVED_RECYCLERS.get(recycler) != row) return;
            }
            if (!recycler.isAttachedToWindow()) return;
            int nativeHeight = Miuix307ZeroCopyRenderer.taskbarNativeHeightFor(recycler);
            if (!dockEnabled || !taskbarEnabled || !WorkstationRuntimeState.isActive()
                    || nativeHeight <= 0) {
                row.restore(recycler);
                return;
            }
            Display display = recycler.getDisplay();
            if (display == null) return;
            Point screen = new Point();
            display.getRealSize(screen);
            float iconCenter = iconRowCenterOnScreen(recycler);
            if (screen.y <= nativeHeight || !Float.isFinite(iconCenter)) return;
            row.align(recycler, WorkstationTaskbarGeometry.visibleCenter(
                    screen.y, nativeHeight), iconCenter);
        });
    }

    /** Prefer the true icon graphic over the RecyclerView cell (which includes label insets). */
    private static float iconRowCenterOnScreen(View recycler) {
        if (!(recycler instanceof ViewGroup)) return Float.NaN;
        ViewGroup group = (ViewGroup) recycler;
        int count = 0;
        float sum = 0f;
        int[] position = new int[2];
        float minWidth = 8f * recycler.getResources().getDisplayMetrics().density;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE || child.getAlpha() <= 0f
                    || child.getWidth() < minWidth || child.getHeight() <= 0) continue;
            child.getLocationOnScreen(position);
            LauncherGlassIconGeometry.Bounds graphic = LauncherGlassIconGeometry.resolve(child);
            sum += position[1] + (graphic == null
                    ? child.getHeight() * 0.5f : (graphic.top + graphic.bottom) * 0.5f);
            count++;
        }
        return count > 0 ? sum / count : Float.NaN;
    }

    private static final class RowAlignment {
        private float originalTranslationY = Float.NaN;
        private float appliedTranslationY = Float.NaN;
        boolean pending;

        void align(View recycler, float targetCenter, float observedCenter) {
            float current = recycler.getTranslationY();
            if (Float.isNaN(originalTranslationY)
                    || (Float.isFinite(appliedTranslationY)
                            && Math.abs(current - appliedTranslationY) > 0.5f)) {
                originalTranslationY = current;
            }
            float delta = WorkstationTaskbarGeometry.iconCenterCorrection(
                    targetCenter, observedCenter);
            if (Math.abs(delta) < 0.5f) return;
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
                        // Taskbar mode centers the real row after layout; do not stack persisted
                        // decoration offsets onto the new optical alignment.
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
