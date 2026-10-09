package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.View;

/** Workstation-only HotSeats item spacing; mode authority comes from WorkstationRuntimeState. */
final class WorkstationDockCustomizationHook {
    private static volatile boolean dockEnabled;
    private static volatile int iconTopOffset;
    private static volatile int iconBottomOffset;
    private WorkstationDockCustomizationHook() {}

    static void applyLiveConfig(LiquidDockConfig.Workstation config) {
        if (config == null) return;
        float scale = config.dimensionsDp
                ? android.content.res.Resources.getSystem().getDisplayMetrics().density : 1f;
        dockEnabled = config.dockEnabled;
        iconTopOffset = Math.round(config.iconTopOffset * scale);
        iconBottomOffset = Math.round(config.iconBottomOffset * scale);
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
