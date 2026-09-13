package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Launcher lifecycle adapter for the fixed Stage prefix and ordinary-desktop migration. */
final class StageWorkspaceRuntime {
    private static final String TAG = "[DC][StageWorkspace]";
    private static final String LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String PREFS_NAME = "liquiddock_stage_workspace";

    private static final Object LOCK = new Object();
    private static volatile boolean installed;
    private static volatile boolean activeForOrdinaryPlacement;
    private static volatile StageWorkspaceActivation activation;
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);

    private StageWorkspaceRuntime() {}

    static void install(ClassLoader classLoader, boolean customGridEnabled,
                        HomeGridProfile selectedProfile) {
        if (installed || classLoader == null || !customGridEnabled
                || selectedProfile != HomeGridProfile.GRID_8X4) {
            return;
        }
        try {
            Class<?> launcher = Class.forName(LAUNCHER, false, classLoader);
            installSetupViewsHook(launcher);
            installConfigurationHook(launcher);
            installed = true;
            MainHook.log(TAG + " runtime installed");
        } catch (Throwable error) {
            activeForOrdinaryPlacement = false;
            MainHook.log(TAG + " runtime unavailable: " + error);
        }
    }

    static boolean isActiveForOrdinaryPlacement() {
        return activeForOrdinaryPlacement && !MainHook.isWorkstationMode();
    }

    private static void installSetupViewsHook(Class<?> launcher) {
        HookUtil.hookMethod(launcher, "setupViews", new Class<?>[0], chain -> {
            Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
            reconcile(chain.getThisObject());
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
                        activeForOrdinaryPlacement = false;
                        return result;
                    }
                    reconcile(chain.getThisObject());
                    return result;
                });
    }

    private static void reconcile(Object launcher) {
        activeForOrdinaryPlacement = false;
        if (MainHook.isWorkstationMode() || !(launcher instanceof Context)) return;

        View workspace = workspaceFrom(launcher);
        if (workspace == null) return;
        Configuration configuration = workspace.getResources().getConfiguration();
        if (configuration == null
                || configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            return;
        }

        List<HomeGridItemPosition> current = collectPositions(workspace);
        if (current == null) return;

        try {
            StageWorkspaceActivation currentActivation = activationFor((Context) launcher, workspace);
            StageWorkspaceActivation.Result result = currentActivation.ensureMapped(current);
            if (!result.active()) return;
            workspaceRef = new WeakReference<>(workspace);
            activeForOrdinaryPlacement = true;
            if (result.changed()) HomeGridHook.scheduleAllPageRefresh();
            MainHook.log(TAG + " active items=" + current.size()
                    + " changed=" + result.changed());
        } catch (Throwable error) {
            activeForOrdinaryPlacement = false;
            MainHook.log(TAG + " reconcile failed: " + error);
        }
    }

    private static StageWorkspaceActivation activationFor(Context context, View workspace) {
        synchronized (LOCK) {
            SharedPreferences preferences = context.getSharedPreferences(
                    PREFS_NAME, Context.MODE_PRIVATE);
            StageWorkspaceMappedLayoutStore targetStore = new StageWorkspaceMappedLayoutStore(
                    new StageWorkspaceSharedPreferencesStore(preferences));
            activation = new StageWorkspaceActivation(
                    targetStore,
                    positions -> applyPositionsAtomically(workspace, positions));
            return activation;
        }
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

    /** Returns null on duplicate ids or malformed ItemInfo; partial captures are forbidden. */
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
                // Structural views may carry unrelated tags.
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

    /** Preflights all ids/screens and rolls back ItemInfo fields if a write cannot complete. */
    private static boolean applyPositionsAtomically(
            View workspace, Collection<HomeGridItemPosition> positions) {
        if (workspace == null || positions == null) return false;

        HashMap<Long, Object> tags = new HashMap<>();
        if (!collectItemTags(workspace, tags) || tags.size() != positions.size()) return false;

        ArrayList<HomeGridItemPosition> originals = new ArrayList<>(positions.size());
        for (HomeGridItemPosition target : positions) {
            Object tag = tags.get(target.itemId());
            if (tag == null) return false;
            try {
                long screenId = HookUtil.getLongField(tag, "screenId");
                if (screenId != target.screenId()) return false;
                originals.add(new HomeGridItemPosition(
                        target.itemId(),
                        screenId,
                        HookUtil.getIntField(tag, "cellX"),
                        HookUtil.getIntField(tag, "cellY"),
                        HookUtil.getIntField(tag, "spanX"),
                        HookUtil.getIntField(tag, "spanY")));
            } catch (Throwable error) {
                return false;
            }
        }

        try {
            for (HomeGridItemPosition target : positions) {
                Object tag = tags.get(target.itemId());
                HookUtil.setIntField(tag, "cellX", target.cellX());
                HookUtil.setIntField(tag, "cellY", target.cellY());
                HookUtil.setIntField(tag, "spanX", target.spanX());
                HookUtil.setIntField(tag, "spanY", target.spanY());
            }
            for (HomeGridItemPosition target : positions) {
                Object tag = tags.get(target.itemId());
                if (HookUtil.getLongField(tag, "screenId") != target.screenId()
                        || HookUtil.getIntField(tag, "cellX") != target.cellX()
                        || HookUtil.getIntField(tag, "cellY") != target.cellY()
                        || HookUtil.getIntField(tag, "spanX") != target.spanX()
                        || HookUtil.getIntField(tag, "spanY") != target.spanY()) {
                    throw new IllegalStateException("Stage ItemInfo verification failed");
                }
            }
        } catch (Throwable error) {
            restorePositions(tags, originals);
            MainHook.log(TAG + " atomic apply failed: " + error);
            return false;
        }

        requestLayoutRecursively(workspace);
        workspace.invalidate();
        return true;
    }

    private static void restorePositions(Map<Long, Object> tags,
                                         Collection<HomeGridItemPosition> originals) {
        for (HomeGridItemPosition original : originals) {
            Object tag = tags.get(original.itemId());
            if (tag == null) continue;
            try {
                HookUtil.setIntField(tag, "cellX", original.cellX());
                HookUtil.setIntField(tag, "cellY", original.cellY());
                HookUtil.setIntField(tag, "spanX", original.spanX());
                HookUtil.setIntField(tag, "spanY", original.spanY());
            } catch (Throwable ignored) {}
        }
    }

    private static boolean collectItemTags(View view, Map<Long, Object> out) {
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
                    if (out.put(id, tag) != null) return false;
                }
            } catch (Throwable ignored) {
                // Structural/non-ItemInfo tag.
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                if (!collectItemTags(group.getChildAt(index), out)) return false;
            }
        }
        return true;
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
