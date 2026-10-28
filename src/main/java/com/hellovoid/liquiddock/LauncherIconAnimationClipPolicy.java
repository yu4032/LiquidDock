package com.hellovoid.liquiddock;

/** Restrict replacement to the native floating icon's initialization. */
final class LauncherIconAnimationClipPolicy {
    // ShortcutIcon returns zero for its real drawable and four for its placeholder.
    static boolean isOemIconVisible(int visibility) { return visibility == 0; }
    static boolean isFloatingIconInit(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            String name = frame.getClassName();
            String method = frame.getMethodName();
            if (name.equals("com.miui.home.recents.views.FloatingIconView2")
                    && method.equals("init")) return true;
        }
        return false;
    }
    static boolean isFloatingIconRefresh(StackTraceElement[] stack) {
        for (StackTraceElement frame : stack) {
            if (frame.getClassName().equals("com.miui.home.recents.views.FloatingIconView2")
                    && (frame.getMethodName().startsWith("notifyBackHome$lambda$")
                        || frame.getMethodName().startsWith("update$lambda$"))) return true;
        }
        return false;
    }
    private LauncherIconAnimationClipPolicy() {}
}
