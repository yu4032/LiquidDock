package com.hellovoid.liquiddock;

import android.app.Activity;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.lang.ref.WeakReference;

/**
 * Keeps a transparent Stage host aligned with the reserved first two columns of the live 8x4 HOME
 * grid. The host lives at Activity content-root level so it never becomes part of CellLayout page
 * ancestry. Stage content is intentionally not rendered here.
 */
final class StageWorkspaceOverlayRuntime {
    private static final String TAG = "[DC][StageOverlay]";
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String CELL_LAYOUT = "com.miui.home.launcher.CellLayout";

    private static final StageWorkspaceOverlayState overlayState =
            new StageWorkspaceOverlayState();

    private static volatile boolean installed;
    private static volatile HomeGridProfile profile;
    private static volatile Class<?> cellLayoutClass;
    private static volatile long boundGeneration;
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);
    private static WeakReference<ViewGroup> contentRootRef = new WeakReference<>(null);
    private static WeakReference<StageWorkspaceOverlayHostView> hostRef =
            new WeakReference<>(null);

    private StageWorkspaceOverlayRuntime() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || classLoader == null || !customGridEnabled
                || selectedProfile != HomeGridProfile.GRID_8X4) {
            return;
        }
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            Class<?> cellLayout = Class.forName(CELL_LAYOUT, false, classLoader);
            profile = selectedProfile;
            cellLayoutClass = cellLayout;
            installSetupViewsHook(launcher);
            installConfigurationHook(launcher);
            installCellLayoutHook(cellLayout);
            installed = true;
            MainHook.log(TAG + " runtime installed");
        } catch (Throwable error) {
            hideHost();
            MainHook.log(TAG + " runtime unavailable: " + error);
        }
    }

    private static void installSetupViewsHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "setupViews", new Class<?>[0], chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            bindLauncher(chain.getThisObject());
            return result;
        });
    }

    private static void installConfigurationHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "onConfigurationChanged",
                new Class<?>[]{Configuration.class}, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Configuration configuration = (Configuration) chain.getArg(0);
                    if (configuration == null
                            || configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
                        hideHost();
                        return result;
                    }
                    bindLauncher(chain.getThisObject());
                    return result;
                });
    }

    private static void installCellLayoutHook(Class<?> cellLayout) {
        HookUtil.hookMethod(cellLayout, "onLayout",
                new Class<?>[]{boolean.class, int.class, int.class, int.class, int.class},
                chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) reconcileFromCellLayout((View) owner);
                    return result;
                });
    }

    private static void bindLauncher(Object launcher) {
        if (!(launcher instanceof Activity)) {
            hideHost();
            return;
        }
        View workspace = workspaceFrom(launcher);
        if (workspace == null) {
            hideHost();
            return;
        }
        View content = ((Activity) launcher).findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            hideHost();
            return;
        }

        ViewGroup contentRoot = (ViewGroup) content;
        StageWorkspaceOverlayHostView host = ensureHost(contentRoot);
        if (host == null) {
            hideHost();
            return;
        }

        long generation = overlayState.bind(contentRoot, workspace);
        boundGeneration = generation;
        workspaceRef = new WeakReference<>(workspace);
        contentRootRef = new WeakReference<>(contentRoot);

        // Reconcile after all setupViews interceptors have returned. This avoids depending on hook
        // registration nesting while still using lifecycle state rather than a settle timer.
        workspace.post(() -> {
            if (workspaceRef.get() != workspace || contentRootRef.get() != contentRoot) return;
            View cellLayout = findCellLayout(workspace);
            if (cellLayout != null) {
                reconcileFromCellLayout(cellLayout, generation, contentRoot, workspace);
            } else {
                applyOverlayAction(StageWorkspaceOverlayState.Action.HIDE, host, 0, 0, 0, 0);
            }
        });
    }

    private static StageWorkspaceOverlayHostView ensureHost(ViewGroup contentRoot) {
        StageWorkspaceOverlayHostView current = hostRef.get();
        if (current != null && current.getParent() == contentRoot) return current;
        if (current != null && current.getParent() instanceof ViewGroup) {
            ((ViewGroup) current.getParent()).removeView(current);
        }
        try {
            StageWorkspaceOverlayHostView host = new StageWorkspaceOverlayHostView(
                    contentRoot.getContext());
            host.setVisibility(View.GONE);
            contentRoot.addView(host, new ViewGroup.LayoutParams(1, 1));
            host.bringToFront();
            hostRef = new WeakReference<>(host);
            return host;
        } catch (Throwable error) {
            MainHook.log(TAG + " host attach failed: " + error);
            return null;
        }
    }

    private static void reconcileFromCellLayout(View cellLayout) {
        View workspace = workspaceRef.get();
        ViewGroup contentRoot = contentRootRef.get();
        if (workspace == null || contentRoot == null) return;
        reconcileFromCellLayout(cellLayout, boundGeneration, contentRoot, workspace);
    }

    private static void reconcileFromCellLayout(View cellLayout,
                                                long generation,
                                                ViewGroup contentRoot,
                                                View workspace) {
        StageWorkspaceOverlayHostView host = hostRef.get();
        if (host == null || host.getParent() != contentRoot) return;

        boolean belongsToWorkspace = isDescendantOf(cellLayout, workspace);
        boolean stageActive = StageWorkspaceRuntime.isActiveForOrdinaryPlacement();
        Configuration configuration = cellLayout == null
                ? null : cellLayout.getResources().getConfiguration();
        boolean landscapeHome = configuration != null
                && configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                && !MainHook.isWorkstationMode();

        if (!belongsToWorkspace || !stageActive || !landscapeHome) {
            StageWorkspaceOverlayState.Action action = overlayState.evaluate(
                    generation,
                    contentRoot,
                    workspace,
                    belongsToWorkspace,
                    stageActive,
                    landscapeHome,
                    false);
            applyOverlayAction(action, host, 0, 0, 0, 0);
            return;
        }

        try {
            int columns = HookUtil.getIntField(cellLayout, "mHCells");
            int rows = HookUtil.getIntField(cellLayout, "mVCells");
            Object xsValue = HookUtil.getField(cellLayout, "mXs");
            if (!(xsValue instanceof int[])
                    || cellLayout.getWidth() <= 0
                    || cellLayout.getHeight() <= 0) {
                StageWorkspaceOverlayState.Action action = overlayState.evaluate(
                        generation, contentRoot, workspace,
                        true, true, true, false);
                applyOverlayAction(action, host, 0, 0, 0, 0);
                return;
            }
            int[] physicalXs = ((int[]) xsValue).clone();

            int[] cellLocation = new int[2];
            int[] rootLocation = new int[2];
            cellLayout.getLocationOnScreen(cellLocation);
            contentRoot.getLocationOnScreen(rootLocation);
            int top = cellLocation[1] - rootLocation[1];
            int bottom = top + cellLayout.getHeight();

            StageWorkspaceOverlayGeometry.Geometry geometry =
                    StageWorkspaceOverlayGeometry.resolve(
                            profile,
                            columns,
                            rows,
                            true,
                            physicalXs,
                            top,
                            bottom);
            StageWorkspaceOverlayState.Action action = overlayState.evaluate(
                    generation,
                    contentRoot,
                    workspace,
                    true,
                    true,
                    true,
                    geometry != null);
            if (geometry == null) {
                applyOverlayAction(action, host, 0, 0, 0, 0);
                return;
            }

            int left = cellLocation[0] - rootLocation[0] + geometry.left();
            applyOverlayAction(
                    action,
                    host,
                    left,
                    geometry.top(),
                    geometry.width(),
                    geometry.height());
        } catch (Throwable error) {
            StageWorkspaceOverlayState.Action action = overlayState.evaluate(
                    generation,
                    contentRoot,
                    workspace,
                    true,
                    true,
                    true,
                    false);
            applyOverlayAction(action, host, 0, 0, 0, 0);
            MainHook.log(TAG + " geometry reconcile failed: " + error);
        }
    }

    private static void applyOverlayAction(StageWorkspaceOverlayState.Action action,
                                           StageWorkspaceOverlayHostView host,
                                           int left,
                                           int top,
                                           int width,
                                           int height) {
        if (action == StageWorkspaceOverlayState.Action.IGNORE) return;
        if (action == StageWorkspaceOverlayState.Action.HIDE) {
            if (host != null) host.setVisibility(View.GONE);
            return;
        }
        if (action != StageWorkspaceOverlayState.Action.SHOW
                || host == null || width <= 0 || height <= 0) {
            return;
        }
        applyGeometry(host, left, top, width, height);
        host.setVisibility(View.VISIBLE);
        host.bringToFront();
    }

    private static void applyGeometry(StageWorkspaceOverlayHostView host,
                                      int left,
                                      int top,
                                      int width,
                                      int height) {
        ViewGroup.LayoutParams layoutParams = host.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new ViewGroup.LayoutParams(width, height);
        } else {
            layoutParams.width = width;
            layoutParams.height = height;
        }
        host.setLayoutParams(layoutParams);
        host.setX(left);
        host.setY(top);
        host.requestLayout();
    }

    private static View workspaceFrom(Object launcher) {
        if (launcher != null) {
            try {
                Object candidate = HookUtil.getField(launcher, "mWorkspace");
                if (candidate instanceof View) return (View) candidate;
            } catch (Throwable ignored) {}
        }
        return workspaceRef.get();
    }

    private static View findCellLayout(View view) {
        if (view == null) return null;
        Class<?> liveCellLayoutClass = cellLayoutClass;
        if (liveCellLayoutClass != null && liveCellLayoutClass.isInstance(view)) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View found = findCellLayout(group.getChildAt(index));
            if (found != null) return found;
        }
        return null;
    }

    private static boolean isDescendantOf(View child, View ancestor) {
        if (child == null || ancestor == null) return false;
        View current = child;
        while (current != null) {
            if (current == ancestor) return true;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static void hideHost() {
        StageWorkspaceOverlayHostView host = hostRef.get();
        if (host != null) host.setVisibility(View.GONE);
    }
}
