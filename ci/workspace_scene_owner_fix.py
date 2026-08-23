from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


# Parent-only Workspace effects (page transition/edit scale/rotation/translation) do not mutate the
# ShortcutIcon leaf. Track the material's complete root-space quad so pre-draw can invalidate cached
# geometry whenever any ancestor transform moves the visual, without refreshing the wallpaper source.
tracker = ROOT / "LauncherGlassRootTransformTracker.java"
tracker.write_text('''package com.hellovoid.liquiddock;

/** Detects changes to a material's complete root-space quad without Android dependencies. */
final class LauncherGlassRootTransformTracker {
    private static final float EPSILON = 0.001f;
    private final float[] last = new float[8];
    private boolean initialized;

    boolean update(float[] points) {
        if (!initialized) {
            initialized = true;
            copyCanonical(points, last);
            return true;
        }
        for (int i = 0; i < last.length; i++) {
            float next = canonical(points, i);
            float previous = last[i];
            boolean same = Float.isNaN(previous)
                    ? Float.isNaN(next)
                    : Float.isFinite(next) && Math.abs(previous - next) < EPSILON;
            if (!same) {
                copyCanonical(points, last);
                return true;
            }
        }
        return false;
    }

    private static void copyCanonical(float[] source, float[] target) {
        for (int i = 0; i < target.length; i++) target[i] = canonical(source, i);
    }

    private static float canonical(float[] source, int index) {
        if (source == null || source.length != 8 || index < 0 || index >= source.length) {
            return Float.NaN;
        }
        float value = source[index];
        return Float.isFinite(value) ? value : Float.NaN;
    }
}
''')

patch_once(
    "LauncherGlassStaticNode.java",
    '''    private final LauncherGlassEffectiveVisibilityTracker effectiveVisibilityTracker =
            new LauncherGlassEffectiveVisibilityTracker();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    '''    private final LauncherGlassEffectiveVisibilityTracker effectiveVisibilityTracker =
            new LauncherGlassEffectiveVisibilityTracker();
    private final LauncherGlassRootTransformTracker rootTransformMotion =
            new LauncherGlassRootTransformTracker();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    "static-node root transform tracker field",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''    private volatile boolean suppressedByFolderOpen;
    private volatile boolean suppressedByDrag;
''',
    '''    private volatile boolean suppressedByFolderOpen;
    private volatile boolean suppressedByDrag;
    private volatile boolean suppressedByLaunchProxy;
''',
    "static-node launch proxy state",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || suppressedByDrag == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByDrag = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

''',
    '''    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || suppressedByDrag == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByDrag = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

    void setSuppressedByLaunchProxy(boolean suppressed) {
        if (disposed || suppressedByLaunchProxy == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByLaunchProxy = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

''',
    "static-node launch proxy setter",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''        changed |= consumeWorkspaceScrollMotion();
        View sceneRoot = material.getRootView();
''',
    '''        changed |= consumeWorkspaceScrollMotion();
        changed |= consumeRootSpaceTransformMotion(material);
        View sceneRoot = material.getRootView();
''',
    "static-node root-space transform sync",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''    private boolean consumeWorkspaceScrollMotion() {
''',
    '''    private boolean consumeRootSpaceTransformMotion(View material) {
        if (material == null || !material.isAttachedToWindow()) {
            return rootTransformMotion.update(null);
        }
        View root = material.getRootView();
        if (root == null || !root.isAttachedToWindow()) return rootTransformMotion.update(null);
        int width = material.getWidth();
        int height = material.getHeight();
        if (width <= 0 || height <= 0) return rootTransformMotion.update(null);
        float[] points = new float[]{
                0f, 0f,
                width, 0f,
                0f, height,
                width, height
        };
        Matrix materialGlobal = new Matrix();
        material.transformMatrixToGlobal(materialGlobal);
        materialGlobal.mapPoints(points);
        Matrix rootGlobal = new Matrix();
        root.transformMatrixToGlobal(rootGlobal);
        Matrix globalToRoot = new Matrix();
        if (!rootGlobal.invert(globalToRoot)) return rootTransformMotion.update(null);
        globalToRoot.mapPoints(points);
        return rootTransformMotion.update(points);
    }

    private boolean consumeWorkspaceScrollMotion() {
''',
    "static-node root-space transform method",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''                || suppressedByFolderOpen || suppressedByDrag
                || !LauncherGlassVisibility.isVisible(material, root)) return null;
''',
    '''                || suppressedByFolderOpen || suppressedByDrag || suppressedByLaunchProxy
                || !LauncherGlassVisibility.isVisible(material, root)) return null;
''',
    "static-node launch proxy geometry suppression",
)

# Startup race: constructor hooks often observe ShortcutIcon before its final Workspace ancestry and
# before ViewRoot/window-token geometry is usable. A later page/bootstrap reconcile must reschedule
# an already-observed attached host, and a failed session acquire must remain retryable.
patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''        synchronized (BOOTSTRAP_OBSERVERS) {
            if (BOOTSTRAP_OBSERVERS.containsKey(host)) return;
''',
    '''        synchronized (BOOTSTRAP_OBSERVERS) {
            if (BOOTSTRAP_OBSERVERS.containsKey(host)) {
                if (host.isAttachedToWindow()) scheduleBind(host, kind, glassConfig, 0);
                return;
            }
''',
    "reschedule already observed host",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''        if (domain != LauncherGlassHierarchy.Domain.WORKSPACE) {
            if (node != null) node.dispose();
            return;
        }
''',
    '''        if (domain != LauncherGlassHierarchy.Domain.WORKSPACE) {
            if (node != null) node.dispose();
            if (domain == LauncherGlassHierarchy.Domain.OTHER && node == null
                    && attempt < MAX_BIND_ATTEMPTS) {
                host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            }
            return;
        }
''',
    "retry transient unclassified host",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''            node = LauncherGlassStaticNode.attachToMaterial(host, kind, radius, glassConfig);
        } else {
            node.requestLifecycleRefresh();
        }
        if (node != null && kind == LauncherGlassDragState.Kind.WIDGET) {
''',
    '''            node = LauncherGlassStaticNode.attachToMaterial(host, kind, radius, glassConfig);
        } else {
            node.requestLifecycleRefresh();
        }
        if (node == null && attempt < MAX_BIND_ATTEMPTS) {
            host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            return;
        }
        if (node != null && kind == LauncherGlassDragState.Kind.WIDGET) {
''',
    "retry failed Workspace session acquire",
)

# Launcher 4.50 hands ShortcutIcon visual ownership to FloatingIconView2/FloatingIconLayer2 by
# calling setAnimTargetVisibility(INVISIBLE), then restores ownership with VISIBLE/showIcon().
# ShortcutIcon implements that protocol by swapping its compound drawable, not View visibility, so
# generic ancestor visibility cannot observe it. Suppress the static source while the proxy owns the
# icon and recover exactly after the vendor hands ownership back.
patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''            installWorkspacePageReconcileHook(classLoader, glassConfig);
            installWorkspaceResumeRecoveryHooks(classLoader, glassConfig);
            MainHook.log(TAG + " widget/icon static glass hooks installed");
''',
    '''            installWorkspacePageReconcileHook(classLoader, glassConfig);
            installWorkspaceResumeRecoveryHooks(classLoader, glassConfig);
            if (glassConfig.iconEnabled) installShortcutIconVisualOwnerHook(classLoader, glassConfig);
            MainHook.log(TAG + " widget/icon static glass hooks installed");
''',
    "install ShortcutIcon visual owner hook",
)

anchor = '''    private static void installWorkspaceResumeRecoveryHooks(
'''
method = '''    private static void installShortcutIconVisualOwnerHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    "setAnimTargetVisibility",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        Object owner = chain.getThisObject();
                        if (!(owner instanceof View) || args.length == 0
                                || !(args[0] instanceof Number)) return result;
                        View host = (View) owner;
                        if (!LauncherGlassHierarchy.isWorkspace(host)) return result;
                        int visibility = ((Number) args[0]).intValue();
                        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
                        if (node != null) {
                            node.setSuppressedByLaunchProxy(visibility != View.VISIBLE);
                        }
                        if (visibility == View.VISIBLE) {
                            scheduleWorkspaceRecoveryFromHost(
                                    host, glassConfig, "anim-target-visible");
                        }
                        return result;
                    }, int.class);
            MainHook.log(TAG + " ShortcutIcon launch-proxy ownership hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " ShortcutIcon launch-proxy ownership hook unavailable: " + error);
        }
    }

    private static void scheduleWorkspaceRecoveryFromHost(
            View host, LiquidDockConfig.Glass glassConfig, String reason) {
        if (!GlassRuntimeState.isEnabled() || host == null) return;
        View workspace = findWorkspaceAncestor(host);
        if (workspace == null) return;
        workspace.postOnAnimation(() -> {
            if (!GlassRuntimeState.isEnabled() || !workspace.isAttachedToWindow()) return;
            reconcileCurrentWorkspacePage(workspace, glassConfig);
            View root = LauncherGlassSessionRegistry.resolveStableRoot(workspace);
            if (root != null) {
                // Consume geometryDirty/root-space owner changes before the one-shot producer pulse.
                root.postInvalidateOnAnimation();
                LauncherGlassSceneController.requestFreshForRoot(root);
            }
            MainHook.log(TAG + " Workspace visual owner recovery reason=" + reason);
        });
    }

    private static View findWorkspaceAncestor(View host) {
        View cursor = host;
        while (cursor != null) {
            Class<?> type = cursor.getClass();
            if ("com.miui.home.launcher.Workspace".equals(type.getName())
                    || "Workspace".equals(type.getSimpleName())) return cursor;
            android.view.ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

'''
path = ROOT / "MiuixLauncherStaticGlassHook.java"
text = path.read_text()
if text.count(anchor) != 1:
    raise SystemExit("ShortcutIcon visual-owner method anchor mismatch")
path.write_text(text.replace(anchor, method + anchor, 1))

print("Workspace startup / root-transform / visual-owner patch applied")
