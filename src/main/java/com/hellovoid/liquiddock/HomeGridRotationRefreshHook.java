package com.hellovoid.liquiddock;

import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns HOME grid refresh scheduling across setup, rotation and Workstation mode changes.
 *
 * <p>The existing 180/500 ms refreshes are intentionally preserved verbatim as timing debt in this
 * structural refactor. This class does not make them source/freshness authority.</p>
 */
final class HomeGridRotationRefreshHook {
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);

    private HomeGridRotationRefreshHook() {}

    static void install(ClassLoader classLoader) {
        installRotationRefresh(classLoader);
        installWorkspaceRefresh(classLoader);
    }

    static void scheduleAllPageRefresh() {
        View workspace = workspaceRef.get();
        if (workspace == null) return;
        workspace.post(() -> refreshWorkspaceGrid(workspace));
        workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 180L);
        workspace.postDelayed(() -> refreshWorkspaceGrid(workspace), 500L);
    }

    private static void installRotationRefresh(ClassLoader classLoader) {
        final Class<?> launcher;
        try {
            launcher = Class.forName("com.miui.home.launcher.Launcher", false, classLoader);
        } catch (ClassNotFoundException error) {
            throw new RuntimeException(error);
        }
        HookUtil.hookMethod(launcher, "onConfigurationChanged",
                new Class<?>[]{Configuration.class}, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    try {
                        Object candidate = HookUtil.getField(chain.getThisObject(), "mWorkspace");
                        if (!(candidate instanceof View)) return result;
                        View workspace = (View) candidate;
                        workspaceRef = new WeakReference<>(workspace);
                        scheduleStableRotationRefresh(workspace);
                    } catch (Throwable error) {
                        MainHook.log("[DC] rotation refresh hook failed: " + error);
                    }
                    return result;
                });
    }

    private static void scheduleStableRotationRefresh(final View workspace) {
        workspace.requestLayout();
        workspace.post(new Runnable() {
            private int lastWidth = -1;
            private int lastHeight = -1;
            private int stableFrames;
            private int frames;

            @Override
            public void run() {
                if (!workspace.isAttachedToWindow()) return;
                int width = workspace.getWidth();
                int height = workspace.getHeight();
                if (width > 0 && height > 0) {
                    if (width == lastWidth && height == lastHeight) {
                        stableFrames++;
                    } else {
                        lastWidth = width;
                        lastHeight = height;
                        stableFrames = 0;
                    }
                }
                frames++;
                boolean orientationReady = width > 0 && height > 0
                        && HomeGridCellGeometryHook.sizeMatchesOrientation(
                                workspace, width, height);
                if (orientationReady && stableFrames >= 2) {
                    refreshWorkspaceGrid(workspace);
                    workspace.postDelayed(
                            () -> refreshWorkspaceGridIfReady(workspace), 180L);
                    workspace.postDelayed(
                            () -> refreshWorkspaceGridIfReady(workspace), 500L);
                    return;
                }
                if (frames >= 180) {
                    MainHook.log("[DC] rotation grid wait timed out: ws="
                            + width + "x" + height + " orientation="
                            + workspace.getResources().getConfiguration().orientation);
                    return;
                }
                workspace.postOnAnimation(this);
            }
        });
    }

    private static void installWorkspaceRefresh(ClassLoader classLoader) {
        HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher",
                "setupViews", chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    try {
                        Object candidate = HookUtil.getField(
                                chain.getThisObject(), "mWorkspace");
                        if (!(candidate instanceof View)) return result;
                        workspaceRef = new WeakReference<>((View) candidate);
                        scheduleAllPageRefresh();
                    } catch (Throwable error) {
                        MainHook.log("[DC] workspace refresh bind failed: " + error);
                    }
                    return result;
                });
    }

    private static void refreshWorkspaceGridIfReady(View workspace) {
        int width = workspace.getWidth();
        int height = workspace.getHeight();
        if (width <= 0 || height <= 0
                || !HomeGridCellGeometryHook.sizeMatchesOrientation(
                        workspace, width, height)) {
            return;
        }
        refreshWorkspaceGrid(workspace);
    }

    private static void refreshWorkspaceGrid(View workspace) {
        try {
            ArrayList<View> pages = new ArrayList<>();
            collectWorkspaceCellLayouts(workspace, pages);
            if (pages.isEmpty()) {
                MainHook.log("[DC] rotation grid refresh: no CellLayout descendants");
                return;
            }
            for (View page : pages) {
                if (!HomeGridCellGeometryHook.sizeMatchesOrientation(
                        page, page.getWidth(), page.getHeight())) {
                    continue;
                }
                HookUtil.InvocationResult<Object> refreshResult =
                        HookUtil.tryInvoke(page, "calculateXsAndYs");
                if (!refreshResult.succeeded()) {
                    MainHook.log("[DC] rotation grid page refresh unavailable: "
                            + refreshResult.failure());
                }
                page.forceLayout();
                page.requestLayout();
                page.invalidate();
            }
            workspace.forceLayout();
            workspace.requestLayout();
            workspace.invalidate();
            MainHook.log("[DC] rotation grid refreshed pages=" + pages.size()
                    + " ws=" + workspace.getWidth() + "x" + workspace.getHeight());
        } catch (Throwable error) {
            MainHook.log("[DC] rotation grid refresh failed: " + error);
        }
    }

    private static void collectWorkspaceCellLayouts(View view, List<View> out) {
        if (view == null) return;
        if ("com.miui.home.launcher.CellLayout".equals(view.getClass().getName())) {
            out.add(view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectWorkspaceCellLayouts(group.getChildAt(i), out);
        }
    }
}
