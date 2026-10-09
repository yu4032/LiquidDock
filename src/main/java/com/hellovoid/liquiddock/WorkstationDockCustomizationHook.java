package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;
import java.util.ArrayList;
import java.util.WeakHashMap;

/** Workstation-only HotSeats item spacing; mode authority comes from WorkstationRuntimeState. */
final class WorkstationDockCustomizationHook {
    private static volatile boolean dockEnabled;
    private static volatile int iconTopOffset;
    private static volatile int iconBottomOffset;
    private static final WeakHashMap<View, Boolean> OBSERVED_RECYCLERS = new WeakHashMap<>();
    private WorkstationDockCustomizationHook() {}

    static void applyLiveConfig(LiquidDockConfig.Workstation config) {
        if (config == null) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        int top = Math.round(config.iconTopOffset * scale);
        int bottom = Math.round(config.iconBottomOffset * scale);
        if (dockEnabled == config.dockEnabled
                && iconTopOffset == top && iconBottomOffset == bottom) return;
        dockEnabled = config.dockEnabled;
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
                        if (recycler instanceof View) {
                            synchronized (OBSERVED_RECYCLERS) {
                                OBSERVED_RECYCLERS.put((View) recycler, Boolean.TRUE);
                            }
                        }
                        if (dockEnabled && WorkstationRuntimeState.isActive()) {
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
