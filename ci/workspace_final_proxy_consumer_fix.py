from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


(ROOT / "LauncherGlassVisualOwnerState.java").write_text(r'''package com.hellovoid.liquiddock;

/** Pure visual-owner state for one static Launcher node. */
final class LauncherGlassVisualOwnerState {
    private static final float EPSILON = 0.001f;
    private boolean launchProxyActive;
    private float[] launchProxyRect;

    /** The first valid final-consumer geometry frame acquires proxy ownership. */
    boolean updateLaunchProxyRect(float[] rect) {
        if (!valid(rect)) return false;
        boolean changed = !launchProxyActive || !same(launchProxyRect, rect);
        launchProxyActive = true;
        if (!same(launchProxyRect, rect)) launchProxyRect = rect.clone();
        return changed;
    }

    boolean endLaunchProxy() {
        if (!launchProxyActive && launchProxyRect == null) return false;
        launchProxyActive = false;
        launchProxyRect = null;
        return true;
    }

    boolean isLaunchProxyActive() { return launchProxyActive; }

    float[] copyLaunchProxyRect() {
        return launchProxyRect != null ? launchProxyRect.clone() : null;
    }

    private static boolean valid(float[] rect) {
        return rect != null && rect.length == 4
                && Float.isFinite(rect[0]) && Float.isFinite(rect[1])
                && Float.isFinite(rect[2]) && Float.isFinite(rect[3])
                && rect[2] > rect[0] && rect[3] > rect[1];
    }

    private static boolean same(float[] first, float[] second) {
        if (first == second) return true;
        if (!valid(first) || !valid(second)) return false;
        for (int i = 0; i < 4; i++) {
            if (Math.abs(first[i] - second[i]) >= EPSILON) return false;
        }
        return true;
    }
}
''')

patch_once(
    "LauncherGlassStaticNode.java",
    '''    void beginLaunchProxy() {
        if (disposed || !visualOwnerState.beginLaunchProxy()) return;
        resetPressInteraction(false);
        geometryDirty = true;
        invalidateVisualOwnerGeometry();
        requestLifecycleRefresh();
    }

    void updateLaunchProxyGeometry(float left, float top, float right, float bottom) {
        if (disposed || !visualOwnerState.updateLaunchProxyRect(
                new float[]{left, top, right, bottom})) return;
        geometryDirty = true;
        invalidateVisualOwnerGeometry();
        LauncherGlassSession live = session;
        if (live != null) live.requestStaticRedraw();
    }
''',
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
    "acquire proxy owner from final geometry",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''            // While MIUI owns the icon with FloatingIconView2/FloatingIconLayer2, the source View
            // is not the visual geometry authority. Until WindowElement publishes its corrected
            // CLOSE_TO_HOME RectF, render no stale source glass.
''',
    '''            // While MIUI owns the icon with FloatingIconView2/FloatingIconLayer2, the source View
            // is not the visual geometry authority. The final vendor proxy consumer publishes the
            // same CLOSE_TO_HOME RectF it actually draws.
''',
    "proxy geometry authority comment",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''            if (glassConfig.iconEnabled) {
                installShortcutIconVisualOwnerHook(classLoader, glassConfig);
                installWindowElementVisualGeometryHook(classLoader);
            }
''',
    '''            if (glassConfig.iconEnabled) {
                installShortcutIconVisualOwnerHook(classLoader, glassConfig);
                installFloatingProxyVisualGeometryHooks(classLoader);
            }
''',
    "install final floating proxy hooks",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''                        if (visibility == View.VISIBLE) {
                            if (node != null) node.endLaunchProxy();
                            scheduleWorkspaceRecoveryFromHost(
                                    host, glassConfig, "anim-target-visible");
                        } else if (node != null) {
                            node.beginLaunchProxy();
                        }
''',
    '''                        if (visibility == View.VISIBLE) {
                            if (node != null) node.endLaunchProxy();
                            scheduleWorkspaceRecoveryFromHost(
                                    host, glassConfig, "anim-target-visible");
                        }
''',
    "visibility releases but does not acquire proxy owner",
)

old_method = '''    private static void installWindowElementVisualGeometryHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.recents.anim.WindowElement",
                    "updateTaskView",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        if (args.length == 0 || !(args[0] instanceof RectF)) return result;
                        Object owner = chain.getThisObject();
                        // Launcher 4.50 exposes this semantic directly: running + CLOSE_TO_HOME.
                        // Do not feed OPEN_FROM_HOME or other WindowElement animations into glass.
                        Object closing = HookUtil.invoke(owner, "isClosingAnimRunning");
                        if (!(closing instanceof Boolean) || !((Boolean) closing)) return result;
                        Object target = HookUtil.invoke(owner, "getLauncherTargetView");
                        if (!(target instanceof View)) return result;
                        LauncherGlassStaticNode node = LauncherGlassStaticNode.find((View) target);
                        if (node == null) return result;
                        // updateTaskView() has already applied dock target drift and ancestor offset
                        // to this exact RectF before FloatingIconView2/FloatingIconLayer2 consume it.
                        RectF currentRect = (RectF) args[0];
                        node.updateLaunchProxyGeometry(
                                currentRect.left, currentRect.top,
                                currentRect.right, currentRect.bottom);
                        return result;
                    }, RectF.class, float.class);
            MainHook.log(TAG + " WindowElement CLOSE_TO_HOME proxy geometry hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " WindowElement CLOSE_TO_HOME proxy geometry hook unavailable: "
                    + error);
        }
    }

'''
new_method = '''    private static void installFloatingProxyVisualGeometryHooks(ClassLoader classLoader) {
        installFloatingProxyVisualGeometryHook(classLoader,
                "com.miui.home.recents.views.FloatingIconView2", false);
        installFloatingProxyVisualGeometryHook(classLoader,
                "com.miui.home.recents.views.FloatingIconLayer2", true);
    }

    private static void installFloatingProxyVisualGeometryHook(
            ClassLoader classLoader, String className, boolean useRotationRect) {
        try {
            HookUtil.hookMethod(classLoader, className, "update",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        // Launcher 4.50 WindowElement and FastLaunchWindowElement both pass
                        // (animType != CLOSE_TO_HOME) as the second boolean in this overload.
                        if (args.length == 10 && args[0] instanceof RectF
                                && args[1] instanceof RectF && args[6] instanceof Boolean
                                && !((Boolean) args[6])) {
                            Object owner = chain.getThisObject();
                            Object target = HookUtil.invoke(owner, "getAnimTarget");
                            if (target instanceof View
                                    && LauncherGlassHierarchy.isWorkspace((View) target)) {
                                LauncherGlassStaticNode node =
                                        LauncherGlassStaticNode.find((View) target);
                                if (node != null) {
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
                            }
                        }
                        return chain.proceed(args);
                    }, RectF.class, RectF.class,
                    float.class, float.class, float.class,
                    boolean.class, boolean.class, boolean.class,
                    float.class, boolean.class);
            MainHook.log(TAG + " final floating proxy geometry hook installed class=" + className);
        } catch (Throwable error) {
            MainHook.log(TAG + " final floating proxy geometry hook unavailable class="
                    + className + ": " + error);
        }
    }

'''
patch_once(
    "MiuixLauncherStaticGlassHook.java",
    old_method,
    new_method,
    "replace WindowElement tap with final floating consumer tap",
)

print("Workspace final FloatingIcon proxy-consumer ownership patch applied")
