from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


(ROOT / "LauncherGlassProxyVisibility.java").write_text(r'''package com.hellovoid.liquiddock;

/** Mirrors Launcher 4.50's own final FloatingIcon consumer visibility rules. */
final class LauncherGlassProxyVisibility {
    private LauncherGlassProxyVisibility() {}

    /** FloatingIconView2.setAlpha(f > 0.1f ? 1 : 0). */
    static boolean isView2Visible(float alpha, boolean drawIcon) {
        return drawIcon && Float.isFinite(alpha) && alpha > 0.1f;
    }

    /** FloatingIconLayer2 SurfaceControl alpha is visible only for f > 0. */
    static boolean isLayer2Visible(float alpha, boolean drawIcon) {
        return drawIcon && Float.isFinite(alpha) && alpha > 0f;
    }
}
''')

patch_once(
    "LauncherGlassVisualOwnerState.java",
    '''    /** The first valid final-consumer geometry frame acquires proxy ownership. */
    boolean updateLaunchProxyRect(float[] rect) {
''',
    '''    /** Own the visual slot while MIUI's proxy exists but has not made its icon visible. */
    boolean holdLaunchProxyHidden() {
        boolean changed = !launchProxyActive || launchProxyRect != null;
        launchProxyActive = true;
        launchProxyRect = null;
        return changed;
    }

    /** The first valid vendor-visible final-consumer geometry frame publishes proxy geometry. */
    boolean updateLaunchProxyRect(float[] rect) {
''',
    "visual-owner hidden proxy state",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''    boolean updateLaunchProxyGeometry(float left, float top, float right, float bottom) {
        if (disposed) return false;
        boolean wasActive = visualOwnerState.isLaunchProxyActive();
        if (!visualOwnerState.updateLaunchProxyRect(
                new float[]{left, top, right, bottom})) return false;
        if (!wasActive) resetPressInteraction(false);
        geometryDirty = true;
        invalidateVisualOwnerGeometry();
        LauncherGlassSession live = session;
        if (live != null) live.requestStaticRedraw();
        return !wasActive;
    }
''',
    '''    boolean holdLaunchProxyHidden() {
        if (disposed) return false;
        boolean wasActive = visualOwnerState.isLaunchProxyActive();
        if (!visualOwnerState.holdLaunchProxyHidden()) return false;
        if (!wasActive) resetPressInteraction(false);
        geometryDirty = true;
        invalidateVisualOwnerGeometry();
        LauncherGlassSession live = session;
        if (live != null) live.requestStaticRedraw();
        return true;
    }

    boolean updateLaunchProxyGeometry(float left, float top, float right, float bottom) {
        if (disposed) return false;
        boolean wasActive = visualOwnerState.isLaunchProxyActive();
        boolean hadGeometry = visualOwnerState.copyLaunchProxyRect() != null;
        if (!visualOwnerState.updateLaunchProxyRect(
                new float[]{left, top, right, bottom})) return false;
        if (!wasActive) resetPressInteraction(false);
        geometryDirty = true;
        invalidateVisualOwnerGeometry();
        LauncherGlassSession live = session;
        if (live != null) live.requestStaticRedraw();
        return !hadGeometry;
    }
''',
    "static-node hidden proxy ownership",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''            // While MIUI owns the icon with FloatingIconView2/FloatingIconLayer2, the source View
            // is not the visual geometry authority. The final vendor proxy consumer publishes the
            // same CLOSE_TO_HOME RectF it actually draws.
            float[] proxyRect = visualOwnerState.copyLaunchProxyRect();
            if (proxyRect == null) return null;
''',
    '''            // While MIUI owns the icon with FloatingIconView2/FloatingIconLayer2, the source View
            // is not the visual geometry authority. A null proxy rect intentionally means the vendor
            // proxy exists but its icon is still hidden; never expand glass over the task-sized rect.
            float[] proxyRect = visualOwnerState.copyLaunchProxyRect();
            if (proxyRect == null) return null;
''',
    "hidden proxy capture comment",
)

old = '''                                if (node != null) {
                                    // FloatingIconView2 draws iconRect; FloatingIconLayer2 places
                                    // its SurfaceControl from rotationIconRect.
                                    RectF proxyRect = (RectF) args[useRotationRect ? 1 : 0];
                                    boolean acquired = node.updateLaunchProxyGeometry(
                                            proxyRect.left, proxyRect.top,
                                            proxyRect.right, proxyRect.bottom);
                                    if (acquired) {
                                        MainHook.log(TAG + " proxy owner acquired class="
                                                + owner.getClass().getSimpleName()
                                                + " target=" + target.getClass().getSimpleName()
                                                + " rect=" + proxyRect);
                                    }
                                }
'''
new = '''                                if (node != null && args[2] instanceof Number) {
                                    float proxyAlpha = ((Number) args[2]).floatValue();
                                    boolean drawIcon;
                                    if (useRotationRect) {
                                        // FloatingIconLayer2.isDrawIcon() returns true unconditionally
                                        // in Launcher 4.50; its SurfaceControl uses the actual field.
                                        try {
                                            drawIcon = HookUtil.getBooleanField(owner, "mIsDrawIcon");
                                        } catch (Throwable ignored) {
                                            drawIcon = false;
                                        }
                                    } else {
                                        Object draw = HookUtil.invoke(owner, "isDrawIcon");
                                        drawIcon = draw instanceof Boolean && ((Boolean) draw);
                                    }
                                    boolean proxyVisible = useRotationRect
                                            ? LauncherGlassProxyVisibility.isLayer2Visible(
                                                    proxyAlpha, drawIcon)
                                            : LauncherGlassProxyVisibility.isView2Visible(
                                                    proxyAlpha, drawIcon);
                                    if (!proxyVisible) {
                                        if (node.holdLaunchProxyHidden()) {
                                            MainHook.log(TAG + " proxy owner hidden class="
                                                    + owner.getClass().getSimpleName()
                                                    + " target=" + target.getClass().getSimpleName()
                                                    + " alpha=" + proxyAlpha);
                                        }
                                    } else {
                                        // FloatingIconView2 draws iconRect; FloatingIconLayer2 places
                                        // its SurfaceControl from rotationIconRect. Do not publish the
                                        // task-sized morph rect until MIUI itself shows the icon proxy.
                                        RectF proxyRect = (RectF) args[useRotationRect ? 1 : 0];
                                        boolean firstVisible = node.updateLaunchProxyGeometry(
                                                proxyRect.left, proxyRect.top,
                                                proxyRect.right, proxyRect.bottom);
                                        if (firstVisible) {
                                            MainHook.log(TAG + " proxy geometry visible class="
                                                    + owner.getClass().getSimpleName()
                                                    + " target=" + target.getClass().getSimpleName()
                                                    + " alpha=" + proxyAlpha
                                                    + " rect=" + proxyRect);
                                        }
                                    }
                                }
'''
patch_once(
    "MiuixLauncherStaticGlassHook.java",
    old,
    new,
    "gate final proxy geometry by vendor visibility",
)

print("Workspace hidden FloatingIcon proxy visibility patch applied")
