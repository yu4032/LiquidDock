package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Launcher adapter for per-orientation layout memory.
 *
 * <p>The adapter intentionally stays above MIUI's native occupancy implementation: it captures
 * and restores the same ItemInfo tag fields already used by LiquidDock's workstation layout
 * backup, while LayoutTransformRuleGridChanged keeps ownership of native rotation matrices.</p>
 */
final class HomeGridOrientationMemoryHook {
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String PREFS_NAME = "liquiddock_orientation_layout_memory";
    private static final long SETTLE_DELAY_MS = 500L;
    private static final long MID_DELAY_MS = 180L;

    private static final Object RUNTIME_LOCK = new Object();
    private static volatile HomeGridDimensions dimensions;
    private static volatile HomeGridOrientationRuntime runtime;
    private static volatile HomeGridOrientation lastOrientation;
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);

    private HomeGridOrientationMemoryHook() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridDimensions gridDimensions) {
        if (!customGridEnabled || gridDimensions == null) return;
        dimensions = gridDimensions;
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            installSetupViewsHook(launcher);
            installConfigurationHook(launcher);
            MainHook.log("[DC] orientation layout memory installed grid="
                    + gridDimensions.key());
        } catch (Throwable error) {
            MainHook.log("[DC] orientation layout memory unavailable: " + error);
        }
    }

    private static void installSetupViewsHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "setupViews", new Class[]{}, chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            try {
                Object owner = chain.getThisObject();
                View workspace = workspaceFrom(owner);
                HomeGridOrientationRuntime active = runtimeFor(owner);
                if (workspace == null || active == null) return result;
                workspaceRef = new WeakReference<>(workspace);
                HomeGridOrientation currentOrientation = orientationOf(
                        workspace.getResources().getConfiguration());
                lastOrientation = currentOrientation;
                scheduleTargetResolution(workspace, currentOrientation, active);
            } catch (Throwable error) {
                MainHook.log("[DC] orientation layout setup resolve failed: " + error);
            }
            return result;
        });
    }

    private static void installConfigurationHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "onConfigurationChanged",
                new Class[]{Configuration.class}, chain -> {
                    Object owner = chain.getThisObject();
                    Configuration targetConfig = (Configuration) chain.getArgs().get(0);
                    HomeGridOrientation targetOrientation = orientationOf(targetConfig);
                    HomeGridOrientation sourceOrientation = lastOrientation;
                    if (sourceOrientation == null) sourceOrientation = targetOrientation.other();

                    HomeGridOrientationRuntime active = runtimeFor(owner);
                    View sourceWorkspace = workspaceFrom(owner);
                    List<HomeGridItemPosition> sourcePositions = sourceWorkspace == null
                            ? null : collectPositions(sourceWorkspace);
                    boolean physicalRotation = sourceOrientation != targetOrientation;
                    if (physicalRotation && active != null && sourcePositions != null) {
                        active.captureCurrent(sourceOrientation, sourcePositions);
                    }

                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));

                    try {
                        View targetWorkspace = workspaceFrom(owner);
                        if (targetWorkspace != null) workspaceRef = new WeakReference<>(targetWorkspace);
                        lastOrientation = targetOrientation;
                        if (physicalRotation && targetWorkspace != null && active != null) {
                            scheduleTargetResolution(targetWorkspace, targetOrientation, active);
                        }
                    } catch (Throwable error) {
                        MainHook.log("[DC] orientation layout rotation resolve failed: " + error);
                    }
                    return result;
                });
    }

    private static HomeGridOrientationRuntime runtimeFor(Object launcher) {
        HomeGridOrientationRuntime current = runtime;
        if (current != null) return current;
        if (!(launcher instanceof Context) || dimensions == null) return null;
        synchronized (RUNTIME_LOCK) {
            if (runtime != null) return runtime;
            SharedPreferences preferences = ((Context) launcher).getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            HomeGridOrientationMemory memory = new HomeGridOrientationMemory(
                    new HomeGridSharedPreferencesMemoryStore(preferences));
            runtime = new HomeGridOrientationRuntime(dimensions, memory);
            return runtime;
        }
    }

    private static View workspaceFrom(Object launcher) {
        if (launcher == null) return workspaceRef.get();
        try {
            Object candidate = HookUtil.getField(launcher, "mWorkspace");
            if (candidate instanceof View) return (View) candidate;
        } catch (Throwable ignored) {}
        return workspaceRef.get();
    }

    private static HomeGridOrientation orientationOf(Configuration configuration) {
        return configuration != null
                && configuration.orientation == Configuration.ORIENTATION_PORTRAIT
                ? HomeGridOrientation.PORTRAIT
                : HomeGridOrientation.LANDSCAPE;
    }

    private static void scheduleTargetResolution(View workspace,
                                                 HomeGridOrientation targetOrientation,
                                                 HomeGridOrientationRuntime active) {
        if (workspace == null || targetOrientation == null || active == null) return;
        workspace.post(() -> resolveTarget(workspace, targetOrientation, active, false));
        workspace.postDelayed(
                () -> resolveTarget(workspace, targetOrientation, active, false), MID_DELAY_MS);
        workspace.postDelayed(
                () -> resolveTarget(workspace, targetOrientation, active, true), SETTLE_DELAY_MS);
    }

    private static void resolveTarget(View workspace,
                                      HomeGridOrientation targetOrientation,
                                      HomeGridOrientationRuntime active,
                                      boolean finalAttempt) {
        List<HomeGridItemPosition> current = collectPositions(workspace);
        if (current == null) return;
        HomeGridLayoutSnapshot remembered = active.rememberedTarget(targetOrientation, current);
        if (remembered != null) {
            if (applySnapshotAtomically(workspace, remembered)) {
                HomeGridHook.scheduleAllPageRefresh();
                return;
            }
            if (!finalAttempt) return;

            active.invalidate(targetOrientation);
            MainHook.log("[DC][HomeGridRestore] stale target invalidated orientation="
                    + targetOrientation);
        }
        if (finalAttempt) {
            HomeGridLayoutSnapshot captured = active.captureCurrent(targetOrientation, current);
            if (captured != null) {
                MainHook.log("[DC] orientation layout captured native target="
                        + targetOrientation + " items=" + captured.size());
            }
        }
    }

    /** Returns null on duplicate ids or unreadable item metadata; partial captures are forbidden. */
    private static List<HomeGridItemPosition> collectPositions(View root) {
        if (root == null) return null;
        ArrayList<HomeGridItemPosition> positions = new ArrayList<>();
        HashSet<Long> ids = new HashSet<>();
        if (!collectPositionsRecursive(root, positions, ids)) return null;
        return positions;
    }

    private static boolean collectPositionsRecursive(View view,
                                                     List<HomeGridItemPosition> out,
                                                     Set<Long> ids) {
        Object tag = view.getTag();
        if (tag != null) {
            try {
                long id = HookUtil.getLongField(tag, "id");
                if (id >= 0) {
                    long screenId = HookUtil.getLongField(tag, "screenId");
                    int cellX = HookUtil.getIntField(tag, "cellX");
                    int cellY = HookUtil.getIntField(tag, "cellY");
                    int spanX = HookUtil.getIntField(tag, "spanX");
                    int spanY = HookUtil.getIntField(tag, "spanY");
                    if (spanX <= 0 || spanY <= 0 || !ids.add(id)) return false;
                    out.add(new HomeGridItemPosition(
                            id, screenId, cellX, cellY, spanX, spanY));
                }
            } catch (Throwable ignored) {
                // Many structural views have arbitrary tags. Ignore tags that are not ItemInfo.
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (!collectPositionsRecursive(group.getChildAt(index), out, ids)) return false;
            }
        }
        return true;
    }

    /**
     * Restores a remembered orientation through the same CellLayout occupancy chain used by a
     * normal Launcher drop. ItemInfo, GridOccupancyController and LayoutParams must never diverge.
     */
    private static boolean applySnapshotAtomically(View workspace,
                                                   HomeGridLayoutSnapshot snapshot) {
        if (workspace == null || snapshot == null) return false;

        HashMap<Long, ItemBinding> bindings = new HashMap<>();
        if (!collectItemBindings(workspace, null, bindings)
                || bindings.size() != snapshot.size()) {
            MainHook.log("[DC][HomeGridRestore] defer: incomplete item/view binding");
            return false;
        }

        int expectedColumns = snapshot.dimensions().columns(snapshot.orientation());
        int expectedRows = snapshot.dimensions().rows(snapshot.orientation());
        ArrayList<ItemBinding> ordered = new ArrayList<>();
        ArrayList<HomeGridItemPosition> original = new ArrayList<>();

        for (HomeGridItemPosition target : snapshot.positions()) {
            ItemBinding binding = bindings.get(target.itemId());
            if (binding == null || binding.cellLayout == null) {
                MainHook.log("[DC][HomeGridRestore] defer: missing CellLayout item="
                        + target.itemId());
                return false;
            }
            try {
                long currentScreen = HookUtil.getLongField(binding.tag, "screenId");
                if (currentScreen != target.screenId()) return false;

                HookUtil.InvocationResult<Object> pageScreen =
                        HookUtil.tryInvoke(binding.cellLayout, "getScreenId");
                if (!pageScreen.succeeded()
                        || !(pageScreen.value() instanceof Long)
                        || ((Long) pageScreen.value()) != target.screenId()) {
                    return false;
                }

                int liveColumns = HookUtil.getIntField(binding.cellLayout, "mHCells");
                int liveRows = HookUtil.getIntField(binding.cellLayout, "mVCells");
                int[] xs = (int[]) HookUtil.getField(binding.cellLayout, "mXs");
                int[] ys = (int[]) HookUtil.getField(binding.cellLayout, "mYs");
                int cellWidth = HookUtil.getIntField(binding.cellLayout, "mCellWidth");
                int cellHeight = HookUtil.getIntField(binding.cellLayout, "mCellHeight");
                if (!HomeGridRestorePolicy.matrixReady(
                        expectedColumns, expectedRows,
                        liveColumns, liveRows, xs, ys)) {
                    MainHook.log("[DC][HomeGridRestore] defer: topology not ready expected="
                            + expectedColumns + "x" + expectedRows
                            + " live=" + liveColumns + "x" + liveRows);
                    return false;
                }
                if (!HomeGridRestorePolicy.gridEnvelopeInside(
                        binding.cellLayout.getWidth(),
                        binding.cellLayout.getHeight(),
                        cellWidth, cellHeight, xs, ys)) {
                    MainHook.log("[DC][HomeGridRestore] defer: physical grid outside CellLayout");
                    return false;
                }
                if (!HomeGridDropLegalityPolicy.isLegal(
                        liveColumns, liveRows,
                        target.cellX(), target.cellY(),
                        target.spanX(), target.spanY())) {
                    return false;
                }

                ordered.add(binding);
                original.add(new HomeGridItemPosition(
                        target.itemId(),
                        currentScreen,
                        HookUtil.getIntField(binding.tag, "cellX"),
                        HookUtil.getIntField(binding.tag, "cellY"),
                        HookUtil.getIntField(binding.tag, "spanX"),
                        HookUtil.getIntField(binding.tag, "spanY")));
            } catch (Throwable error) {
                MainHook.log("[DC][HomeGridRestore] preflight failed: " + error);
                return false;
            }
        }

        try {
            // Launcher frees a dragged item's old occupancy before assigning a new cell.
            for (ItemBinding binding : ordered) {
                HookUtil.requireInvoke(
                        binding.cellLayout, "updateCellOccupiedMarks",
                        binding.view, true, false);
            }

            int index = 0;
            for (HomeGridItemPosition target : snapshot.positions()) {
                ItemBinding binding = ordered.get(index++);
                HookUtil.setIntField(binding.tag, "cellX", target.cellX());
                HookUtil.setIntField(binding.tag, "cellY", target.cellY());
                HookUtil.setIntField(binding.tag, "spanX", target.spanX());
                HookUtil.setIntField(binding.tag, "spanY", target.spanY());
            }

            // Launcher marks the new position occupied only after ItemInfo has the final target.
            for (ItemBinding binding : ordered) {
                HookUtil.requireInvoke(
                        binding.cellLayout, "updateCellOccupiedMarks",
                        binding.view, false, false);
                binding.view.requestLayout();
                binding.cellLayout.requestLayout();
                binding.cellLayout.invalidate();
            }

            workspace.requestLayout();
            workspace.invalidate();
            MainHook.log("[DC][HomeGridRestore] restored target=" + snapshot.orientation()
                    + " items=" + snapshot.size()
                    + " topology=" + expectedColumns + "x" + expectedRows);
            return true;
        } catch (Throwable error) {
            rollbackBindings(ordered, original);
            MainHook.log("[DC][HomeGridRestore] transaction failed; rolled back: " + error);
            return false;
        }
    }

    private static void rollbackBindings(
            List<ItemBinding> bindings,
            List<HomeGridItemPosition> original) {
        int count = Math.min(bindings.size(), original.size());
        for (int i = 0; i < count; i++) {
            ItemBinding binding = bindings.get(i);
            try {
                HookUtil.tryInvoke(
                        binding.cellLayout, "updateCellOccupiedMarks",
                        binding.view, true, false);
            } catch (Throwable ignored) {}
        }
        for (int i = 0; i < count; i++) {
            ItemBinding binding = bindings.get(i);
            HomeGridItemPosition source = original.get(i);
            try {
                HookUtil.setIntField(binding.tag, "cellX", source.cellX());
                HookUtil.setIntField(binding.tag, "cellY", source.cellY());
                HookUtil.setIntField(binding.tag, "spanX", source.spanX());
                HookUtil.setIntField(binding.tag, "spanY", source.spanY());
                HookUtil.tryInvoke(
                        binding.cellLayout, "updateCellOccupiedMarks",
                        binding.view, false, false);
                binding.view.requestLayout();
                binding.cellLayout.requestLayout();
            } catch (Throwable ignored) {}
        }
    }

    private static boolean collectItemBindings(
            View view,
            View currentCellLayout,
            Map<Long, ItemBinding> out) {
        if (view == null) return true;
        View page = currentCellLayout;
        if ("com.miui.home.launcher.CellLayout".equals(view.getClass().getName())) {
            page = view;
        }

        Object tag = view.getTag();
        if (tag != null) {
            try {
                long id = HookUtil.getLongField(tag, "id");
                if (id >= 0) {
                    HookUtil.getLongField(tag, "screenId");
                    HookUtil.getIntField(tag, "cellX");
                    HookUtil.getIntField(tag, "cellY");
                    HookUtil.getIntField(tag, "spanX");
                    HookUtil.getIntField(tag, "spanY");
                    if (page == null || out.put(id, new ItemBinding(view, tag, page)) != null) {
                        return false;
                    }
                }
            } catch (Throwable ignored) {
                // Structural/non-ItemInfo tag.
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (!collectItemBindings(group.getChildAt(index), page, out)) return false;
            }
        }
        return true;
    }

    private static final class ItemBinding {
        final View view;
        final Object tag;
        final View cellLayout;

        ItemBinding(View view, Object tag, View cellLayout) {
            this.view = view;
            this.tag = tag;
            this.cellLayout = cellLayout;
        }
    }

    private static void requestLayoutRecursively(View view) {
        view.requestLayout();
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                requestLayoutRecursively(group.getChildAt(index));
            }
        }
    }
}
