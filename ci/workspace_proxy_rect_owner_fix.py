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

    boolean beginLaunchProxy() {
        if (launchProxyActive) return false;
        launchProxyActive = true;
        launchProxyRect = null;
        return true;
    }

    boolean updateLaunchProxyRect(float[] rect) {
        if (!launchProxyActive || !valid(rect)) return false;
        if (same(launchProxyRect, rect)) return false;
        launchProxyRect = rect.clone();
        return true;
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
    '''    private final LauncherGlassRootTransformTracker rootTransformMotion =
            new LauncherGlassRootTransformTracker();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    '''    private final LauncherGlassRootTransformTracker rootTransformMotion =
            new LauncherGlassRootTransformTracker();
    private final LauncherGlassVisualOwnerState visualOwnerState =
            new LauncherGlassVisualOwnerState();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    "static-node visual-owner state field",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''    private volatile boolean suppressedByFolderOpen;
    private volatile boolean suppressedByDrag;
    private volatile boolean suppressedByLaunchProxy;
''',
    '''    private volatile boolean suppressedByFolderOpen;
    private volatile boolean suppressedByDrag;
''',
    "remove launch-proxy suppression field",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''    void setSuppressedByLaunchProxy(boolean suppressed) {
        if (disposed || suppressedByLaunchProxy == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByLaunchProxy = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

''',
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

    void endLaunchProxy() {
        if (disposed || !visualOwnerState.endLaunchProxy()) return;
        geometryDirty = true;
        invalidateVisualOwnerGeometry();
        requestLifecycleRefresh();
    }

    private void invalidateVisualOwnerGeometry() {
        View material = materialRef.get();
        View root = material != null ? material.getRootView() : null;
        if (root != null && root.isAttachedToWindow()) root.postInvalidateOnAnimation();
    }

''',
    "replace proxy suppression with proxy geometry ownership",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View material = materialRef.get();
        GlassComponentStyle style = componentStyle();
        if (disposed || material == null || root == null || style == null || !style.enabled
                || !LauncherGlassHierarchy.isWorkspace(material)
                || suppressedByFolderOpen || suppressedByDrag || suppressedByLaunchProxy
                || !LauncherGlassVisibility.isVisible(material, root)) return null;
        int hostWidth = material.getWidth();
        int hostHeight = material.getHeight();
        if (hostWidth <= 0 || hostHeight <= 0 || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return null;
        }

        float localLeft = 0f;
''',
    '''    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View material = materialRef.get();
        GlassComponentStyle style = componentStyle();
        if (disposed || material == null || root == null || style == null || !style.enabled
                || suppressedByFolderOpen || suppressedByDrag) return null;
        int rootWidth = root.getWidth();
        int rootHeight = root.getHeight();
        if (rootWidth <= 0 || rootHeight <= 0) return null;

        int hostWidth = material.getWidth();
        int hostHeight = material.getHeight();
        float density = material.getResources().getDisplayMetrics().density;
        float requestedRadius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density : nativeCornerRadiusPx;

        if (visualOwnerState.isLaunchProxyActive()) {
            // While MIUI owns the icon with FloatingIconView2/FloatingIconLayer2, the source View
            // is not the visual geometry authority. Until WindowElement publishes its corrected
            // CLOSE_TO_HOME RectF, render no stale source glass.
            float[] proxyRect = visualOwnerState.copyLaunchProxyRect();
            if (proxyRect == null) return null;
            float proxyWidth = proxyRect[2] - proxyRect[0];
            float proxyHeight = proxyRect[3] - proxyRect[1];
            float referenceWidth = Math.max(1f, hostWidth);
            float referenceHeight = Math.max(1f, hostHeight);
            if (kind == LauncherGlassDragState.Kind.ICON) {
                LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(material);
                if (icon != null && icon.width() > 0f && icon.height() > 0f) {
                    referenceWidth = icon.width();
                    referenceHeight = icon.height();
                }
            }
            float radiusScale = Math.max(0.01f, Math.min(
                    proxyWidth / referenceWidth, proxyHeight / referenceHeight));
            return LauncherGlassGeometry.resolve(
                    rootWidth, rootHeight,
                    proxyRect[0], proxyRect[1], proxyRect[2], proxyRect[3],
                    LauncherGlassBoundsPolicy.capRadius(
                            requestedRadius * radiusScale, proxyWidth, proxyHeight));
        }

        if (!LauncherGlassHierarchy.isWorkspace(material)
                || !LauncherGlassVisibility.isVisible(material, root)) return null;
        if (hostWidth <= 0 || hostHeight <= 0) return null;

        float localLeft = 0f;
''',
    "static-node proxy rect capture",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''        float density = material.getResources().getDisplayMetrics().density;
        float[] styledBounds = LauncherGlassBoundsPolicy.apply(
''',
    '''        float[] styledBounds = LauncherGlassBoundsPolicy.apply(
''',
    "reuse capture density",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''        float requestedRadius = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density : nativeCornerRadiusPx;
        return LauncherGlassGeometry.resolve(
''',
    '''        return LauncherGlassGeometry.resolve(
''',
    "reuse capture requested radius",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''import android.graphics.drawable.Drawable;
''',
    '''import android.graphics.RectF;
import android.graphics.drawable.Drawable;
''',
    "static hook RectF import",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''            if (glassConfig.iconEnabled) installShortcutIconVisualOwnerHook(classLoader, glassConfig);
            MainHook.log(TAG + " widget/icon static glass hooks installed");
''',
    '''            if (glassConfig.iconEnabled) {
                installShortcutIconVisualOwnerHook(classLoader, glassConfig);
                installWindowElementVisualGeometryHook(classLoader);
            }
            MainHook.log(TAG + " widget/icon static glass hooks installed");
''',
    "install WindowElement proxy geometry hook",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''                        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
                        if (node != null) {
                            node.setSuppressedByLaunchProxy(visibility != View.VISIBLE);
                        }
                        if (visibility == View.VISIBLE) {
                            scheduleWorkspaceRecoveryFromHost(
                                    host, glassConfig, "anim-target-visible");
                        }
''',
    '''                        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
                        if (visibility == View.VISIBLE) {
                            if (node != null) node.endLaunchProxy();
                            scheduleWorkspaceRecoveryFromHost(
                                    host, glassConfig, "anim-target-visible");
                        } else if (node != null) {
                            node.beginLaunchProxy();
                        }
''',
    "ShortcutIcon visual-owner start/end",
)

anchor = '''    private static void scheduleWorkspaceRecoveryFromHost(
'''
method = '''    private static void installWindowElementVisualGeometryHook(ClassLoader classLoader) {
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
path = ROOT / "MiuixLauncherStaticGlassHook.java"
text = path.read_text()
if text.count(anchor) != 1:
    raise SystemExit("WindowElement proxy hook insertion anchor mismatch")
path.write_text(text.replace(anchor, method + anchor, 1))

print("Workspace CLOSE_TO_HOME vendor proxy-rect ownership patch applied")
