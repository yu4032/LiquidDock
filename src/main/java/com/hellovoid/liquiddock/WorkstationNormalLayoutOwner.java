package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.util.HashMap;
import java.util.Map;

/** Owns the reversible normal-HOME placement snapshot used around Workstation mode. */
final class WorkstationNormalLayoutOwner {
    private static final Map<Long, HomeItemPosition> BACKUP = new HashMap<>();

    private WorkstationNormalLayoutOwner() {}

    static void backupFromActiveDockRoot() {
        BACKUP.clear();
        View dockBg = DockShadowOwnership.activeBackground();
        View root = dockBg == null ? null : dockBg.getRootView();
        if (root != null) collectHomeItemPositions(root, false);
        MainHook.log("[DC] normal 8x4 layout backup items=" + BACKUP.size());
    }

    static void scheduleRestoreFromActiveDockRoot() {
        View dockBg = DockShadowOwnership.activeBackground();
        View root = dockBg == null ? null : dockBg.getRootView();
        if (root == null || BACKUP.isEmpty()) return;
        root.post(() -> restore(root));
        root.postDelayed(() -> restore(root), 250L);
        root.postDelayed(() -> restore(root), 700L);
    }

    private static void restore(View root) {
        if (WorkstationRuntimeState.isActive()) return;
        collectHomeItemPositions(root, true);
        root.requestLayout();
        root.invalidate();
        MainHook.log("[DC] normal 8x4 layout restored from backup items=" + BACKUP.size());
    }

    private static void collectHomeItemPositions(View view, boolean restore) {
        Object tag = view.getTag();
        if (tag != null) {
            try {
                long id = HookUtil.getLongField(tag, "id");
                if (id >= 0) {
                    if (!restore) {
                        BACKUP.put(id, new HomeItemPosition(
                                HookUtil.getLongField(tag, "screenId"),
                                HookUtil.getIntField(tag, "cellX"),
                                HookUtil.getIntField(tag, "cellY"),
                                HookUtil.getIntField(tag, "spanX"),
                                HookUtil.getIntField(tag, "spanY")));
                    } else {
                        HomeItemPosition saved = BACKUP.get(id);
                        if (saved != null) {
                            HookUtil.setLongField(tag, "screenId", saved.screenId);
                            HookUtil.setIntField(tag, "cellX", saved.cellX);
                            HookUtil.setIntField(tag, "cellY", saved.cellY);
                            HookUtil.setIntField(tag, "spanX", saved.spanX);
                            HookUtil.setIntField(tag, "spanY", saved.spanY);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectHomeItemPositions(group.getChildAt(i), restore);
            }
        }
    }

    private static final class HomeItemPosition {
        final long screenId;
        final int cellX;
        final int cellY;
        final int spanX;
        final int spanY;

        HomeItemPosition(long screenId, int cellX, int cellY, int spanX, int spanY) {
            this.screenId = screenId;
            this.cellX = cellX;
            this.cellY = cellY;
            this.spanX = spanX;
            this.spanY = spanY;
        }
    }
}
