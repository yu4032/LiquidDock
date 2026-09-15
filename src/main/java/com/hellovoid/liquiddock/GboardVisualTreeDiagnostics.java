package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

/** One-shot runtime diagnostics for locating residual opaque Gboard floating-keyboard layers. */
final class GboardVisualTreeDiagnostics {
    private static final String TAG = "[DC][GboardFloatingGlass]";
    private static final int MAX_CHILD_DEPTH = 4;

    private GboardVisualTreeDiagnostics() {}

    static void dump(View keyboardArea) {
        if (keyboardArea == null) return;
        try {
            log("visual-tree begin");
            dumpAncestors(keyboardArea);
            dumpSubtree(keyboardArea, 0, "area");
            log("visual-tree end");
        } catch (Throwable error) {
            log("visual-tree failed cause=" + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    private static void dumpAncestors(View view) {
        View current = view;
        int level = 0;
        while (current != null && level < 8) {
            log("visual-tree ancestor[" + level + "] " + describe(current));
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
            level++;
        }
    }

    private static void dumpSubtree(View view, int depth, String path) {
        if (view == null || depth > MAX_CHILD_DEPTH) return;
        log("visual-tree node path=" + path + " depth=" + depth + " " + describe(view));
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            dumpSubtree(group.getChildAt(i), depth + 1, path + "/" + i);
        }
    }

    private static String describe(View view) {
        int[] location = new int[2];
        try { view.getLocationInWindow(location); } catch (Throwable ignored) {}
        Drawable background = null;
        try { background = view.getBackground(); } catch (Throwable ignored) {}
        String backgroundName = background == null ? "null" : background.getClass().getName();
        String resourceName = "?";
        try {
            int id = view.getId();
            if (id != View.NO_ID) resourceName = view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {}
        return "class=" + view.getClass().getName()
                + " id=0x" + Integer.toHexString(view.getId())
                + " name=" + resourceName
                + " xy=" + location[0] + "," + location[1]
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " vis=" + view.getVisibility()
                + " alpha=" + view.getAlpha()
                + " elevation=" + view.getElevation()
                + " bg=" + backgroundName;
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
