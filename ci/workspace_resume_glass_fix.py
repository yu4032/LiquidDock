from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


# HyperOS can hide the *current CellLayout* rather than the ShortcutIcon itself. In particular,
# Launcher.applyMingouDesktopIconBlur() -> hideMingouDesktopIconBlurSourceIfNeeded() sets the
# current Workspace screen alpha to 0, then restoreMingouDesktopIconBlurSourceIfNeeded() restores
# the saved alpha after the return-home blur animation. Static nodes therefore must treat ancestor
# effective-alpha transitions as geometry invalidations, not only changes on the leaf host View.
tracker = ROOT / "LauncherGlassEffectiveVisibilityTracker.java"
tracker.write_text('''package com.hellovoid.liquiddock;

/** Detects effective ancestor-visibility transitions without depending on Android View APIs. */
final class LauncherGlassEffectiveVisibilityTracker {
    private static final float EPSILON = 0.001f;

    private boolean initialized;
    private float lastAlpha;

    boolean update(float effectiveAlpha) {
        float next = Float.isFinite(effectiveAlpha) ? effectiveAlpha : Float.NaN;
        if (!initialized) {
            initialized = true;
            lastAlpha = next;
            return true;
        }
        boolean same = Float.isNaN(lastAlpha)
                ? Float.isNaN(next)
                : Float.isFinite(next) && Math.abs(lastAlpha - next) < EPSILON;
        if (same) return false;
        lastAlpha = next;
        return true;
    }
}
''')

patch_once(
    "LauncherGlassStaticNode.java",
    '''    private final LauncherGlassScrollMotionTracker workspaceScrollMotion =
            new LauncherGlassScrollMotionTracker();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    '''    private final LauncherGlassScrollMotionTracker workspaceScrollMotion =
            new LauncherGlassScrollMotionTracker();
    private final LauncherGlassEffectiveVisibilityTracker effectiveVisibilityTracker =
            new LauncherGlassEffectiveVisibilityTracker();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    "static-node effective visibility tracker field",
)

patch_once(
    "LauncherGlassStaticNode.java",
    '''        changed |= consumeWorkspaceScrollMotion();
        Object parent = material.getParent();
''',
    '''        changed |= consumeWorkspaceScrollMotion();
        View sceneRoot = material.getRootView();
        changed |= effectiveVisibilityTracker.update(
                LauncherGlassVisibility.effectiveAlpha(material, sceneRoot));
        Object parent = material.getParent();
''',
    "static-node ancestor effective visibility sync",
)

# The decompiled vendor return-home chain is:
# Launcher.onResume() -> Workspace.onResume() -> post(mRestoreBlurRunnable), and independently
# resetMingouLaunchWallpaperBlurIfNeeded(true) drives the Mingou animation whose terminal path is
# setMingouDesktopIconBlurRadius(0) -> restoreMingouDesktopIconBlurSourceIfNeeded(). Hook both real
# boundaries: onResume covers ordinary HOME re-entry; the private restore method covers the exact
# point where the current CellLayout alpha becomes visible again.
patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''        if (any) {
            installWorkspacePageReconcileHook(classLoader, glassConfig);
            MainHook.log(TAG + " widget/icon static glass hooks installed");
        }
''',
    '''        if (any) {
            installWorkspacePageReconcileHook(classLoader, glassConfig);
            installWorkspaceResumeRecoveryHooks(classLoader, glassConfig);
            MainHook.log(TAG + " widget/icon static glass hooks installed");
        }
''',
    "install Workspace resume recovery hooks",
)

anchor = '''    private static boolean installHostClass(
'''
methods = '''    private static void installWorkspaceResumeRecoveryHooks(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher", "onResume",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        scheduleWorkspaceHomeRecovery(
                                chain.getThisObject(), glassConfig, "launcher-onResume");
                        return result;
                    });
            MainHook.log(TAG + " Launcher HOME resume recovery hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Launcher HOME resume recovery hook unavailable: " + error);
        }

        try {
            HookUtil.hookMethod(classLoader, "com.miui.home.launcher.Launcher",
                    "restoreMingouDesktopIconBlurSourceIfNeeded",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        scheduleWorkspaceHomeRecovery(
                                chain.getThisObject(), glassConfig, "mingou-source-restored");
                        return result;
                    });
            MainHook.log(TAG + " Mingou Workspace source restore recovery hook installed");
        } catch (Throwable error) {
            // Optional across Launcher versions. Generic ancestor visibility tracking remains the
            // fallback and Launcher.onResume still provides a stable HOME lifecycle boundary.
            MainHook.log(TAG + " Mingou Workspace source restore recovery hook unavailable: "
                    + error);
        }
    }

    private static void scheduleWorkspaceHomeRecovery(
            Object launcher, LiquidDockConfig.Glass glassConfig, String reason) {
        if (!GlassRuntimeState.isEnabled() || launcher == null) return;
        Object value = HookUtil.invoke(launcher, "getWorkspace");
        if (!(value instanceof View)) return;
        View workspace = (View) value;
        workspace.postOnAnimation(() -> {
            if (!GlassRuntimeState.isEnabled() || !workspace.isAttachedToWindow()) return;
            reconcileCurrentWorkspacePage(workspace, glassConfig);
            View root = LauncherGlassSessionRegistry.resolveStableRoot(workspace);
            if (root != null) LauncherGlassSceneController.requestFreshForRoot(root);
            MainHook.log(TAG + " Workspace HOME recovery reason=" + reason);
        });
    }

'''
path = ROOT / "MiuixLauncherStaticGlassHook.java"
text = path.read_text()
if text.count(anchor) != 1:
    raise SystemExit("Workspace resume recovery method anchor mismatch")
path.write_text(text.replace(anchor, methods + anchor, 1))

print("Workspace app-return glass recovery patch applied")
