package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

/** Read-only workspace scan used by GUI and runtime grid preflight. */
final class HomeGridWorkspaceSpanPreflight {
    static final class Result {
        final boolean available;
        final boolean compatible;
        final int blockingSpanX;
        final int blockingSpanY;
        final int multiCellItems;

        Result(
                boolean available,
                boolean compatible,
                int blockingSpanX,
                int blockingSpanY,
                int multiCellItems) {
            this.available = available;
            this.compatible = compatible;
            this.blockingSpanX = blockingSpanX;
            this.blockingSpanY = blockingSpanY;
            this.multiCellItems = multiCellItems;
        }

        static Result unavailable() {
            return new Result(false, false, 0, 0, 0);
        }

        static Result compatible(int multiCellItems) {
            return new Result(true, true, 0, 0, multiCellItems);
        }

        static Result incompatible(int spanX, int spanY, int multiCellItems) {
            return new Result(true, false, spanX, spanY, multiCellItems);
        }
    }

    private HomeGridWorkspaceSpanPreflight() {}

    static Result scanWorkspace(View workspace, int columns, int rows) {
        if (workspace == null || columns <= 0 || rows <= 0) {
            return Result.unavailable();
        }
        ArrayList<View> pages = new ArrayList<>();
        collectCellLayouts(workspace, pages);
        if (pages.isEmpty()) return Result.unavailable();

        int multiCellItems = 0;
        for (View page : pages) {
            if (!(page instanceof ViewGroup)) continue;
            ViewGroup group = (ViewGroup) page;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                Object tag = child.getTag();
                if (tag == null) continue;
                final int spanX;
                final int spanY;
                try {
                    spanX = HookUtil.getIntField(tag, "spanX");
                    spanY = HookUtil.getIntField(tag, "spanY");
                } catch (Throwable ignored) {
                    continue;
                }
                if (spanX <= 1 && spanY <= 1) continue;
                multiCellItems++;
                if (!HomeGridDropLegalityPolicy.fitsBothOrientations(
                        columns, rows, spanX, spanY)) {
                    return Result.incompatible(spanX, spanY, multiCellItems);
                }
            }
        }
        return Result.compatible(multiCellItems);
    }

    static Result scanCellLayout(View owner, int columns, int rows) {
        if (!(owner instanceof ViewGroup) || columns <= 0 || rows <= 0) {
            return Result.unavailable();
        }
        ViewGroup group = (ViewGroup) owner;
        int multiCellItems = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Object tag = child.getTag();
            if (tag == null) continue;
            final int spanX;
            final int spanY;
            try {
                spanX = HookUtil.getIntField(tag, "spanX");
                spanY = HookUtil.getIntField(tag, "spanY");
            } catch (Throwable ignored) {
                continue;
            }
            if (spanX <= 1 && spanY <= 1) continue;
            multiCellItems++;
            if (!HomeGridDropLegalityPolicy.fitsBothOrientations(
                    columns, rows, spanX, spanY)) {
                return Result.incompatible(spanX, spanY, multiCellItems);
            }
        }
        return Result.compatible(multiCellItems);
    }

    private static void collectCellLayouts(View view, ArrayList<View> out) {
        if (view == null) return;
        if ("com.miui.home.launcher.CellLayout".equals(view.getClass().getName())) {
            out.add(view);
            return;
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectCellLayouts(group.getChildAt(i), out);
        }
    }
}
