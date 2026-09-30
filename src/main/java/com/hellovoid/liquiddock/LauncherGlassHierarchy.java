package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewParent;

/** Classifies Launcher views into mutually-exclusive GPU glass domains. */
final class LauncherGlassHierarchy {
    enum Domain { WORKSPACE, DOCK, OTHER }

    private LauncherGlassHierarchy() {}

    static Domain classify(View view) {
        View cursor = view;
        while (cursor != null) {
            String name = cursor.getClass().getName();
            String simple = cursor.getClass().getSimpleName();
            if ("com.miui.home.launcher.Workspace".equals(name) || "Workspace".equals(simple)) {
                return Domain.WORKSPACE;
            }
            if ("com.miui.home.launcher.hotseats.HotSeats".equals(name)
                    || "HotSeats".equals(simple)
                    || name.startsWith("com.miui.home.launcher.hotseats.")) {
                return Domain.DOCK;
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return Domain.OTHER;
    }

    static boolean isWorkspace(View view) { return classify(view) == Domain.WORKSPACE; }
    static boolean isDock(View view) { return classify(view) == Domain.DOCK; }

    static View findDockRoot(View view) {
        View cursor = view;
        while (cursor != null) {
            String name = cursor.getClass().getName();
            String simple = cursor.getClass().getSimpleName();
            if ("com.miui.home.launcher.hotseats.HotSeats".equals(name) || "HotSeats".equals(simple)) {
                return cursor;
            }
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }
}
