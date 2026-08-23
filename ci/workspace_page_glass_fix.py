from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


# Workspace page lifecycle: views on non-initial pages already exist and do not rerun constructors.
# Reconcile only the vendor's committed current CellLayout after setCurrentScreenInner(int).
patch_once(
    "MiuixLauncherStaticGlassHook.java",
    "import android.view.View;\n",
    "import android.view.View;\nimport android.view.ViewGroup;\n",
    "static hook ViewGroup import",
)

patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''        installed = any;
        if (any) MainHook.log(TAG + " widget/icon static glass hooks installed");
        return any;
''',
    '''        installed = any;
        if (any) {
            installWorkspacePageReconcileHook(classLoader, glassConfig);
            MainHook.log(TAG + " widget/icon static glass hooks installed");
        }
        return any;
''',
    "install page reconcile hook",
)

anchor = '''    private static boolean installHostClass(
'''
method = '''    private static void installWorkspacePageReconcileHook(
            ClassLoader classLoader, LiquidDockConfig.Glass glassConfig) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Workspace", "setCurrentScreenInner",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Object owner = chain.getThisObject();
                        if (owner instanceof View) {
                            View workspace = (View) owner;
                            // The vendor has committed mCurrentScreen/current screenId here. Wait one
                            // animation turn for the selected CellLayout's child transforms to settle.
                            workspace.postOnAnimation(() ->
                                    reconcileCurrentWorkspacePage(workspace, glassConfig));
                        }
                        return result;
                    }, int.class);
            MainHook.log(TAG + " Workspace current-page reconcile hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " Workspace page reconcile hook unavailable: " + error);
        }
    }

    private static void reconcileCurrentWorkspacePage(
            View workspace, LiquidDockConfig.Glass glassConfig) {
        if (!GlassRuntimeState.isEnabled() || workspace == null
                || !workspace.isAttachedToWindow()) return;
        Object current = HookUtil.invoke(workspace, "getCurrentCellLayout");
        if (!(current instanceof View)) {
            MainHook.log(TAG + " current Workspace page reconcile skipped: CellLayout unavailable");
            return;
        }
        int visited = reconcileStaticSubtree((View) current, glassConfig);
        MainHook.log(TAG + " current Workspace page reconciled views=" + visited);
    }

    private static int reconcileStaticSubtree(View view, LiquidDockConfig.Glass glassConfig) {
        if (view == null) return 0;
        reconcileExistingHost(view, glassConfig);
        int visited = 1;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                visited += reconcileStaticSubtree(group.getChildAt(i), glassConfig);
            }
        }
        return visited;
    }

'''
path = ROOT / "MiuixLauncherStaticGlassHook.java"
text = path.read_text()
if text.count(anchor) != 1:
    raise SystemExit("page reconcile method anchor mismatch")
path.write_text(text.replace(anchor, method + anchor, 1))

# LauncherGlassStaticNode itself already unregisters on detach and re-registers on attach. Disposing
# it here breaks normal Workspace page caching/reparenting and makes icon glass intermittent.
patch_once(
    "MiuixLauncherStaticGlassHook.java",
    '''                @Override public void onViewDetachedFromWindow(View v) {
                    DockGlassItemRegistry.unregister(v);
                    LauncherGlassStaticNode node = LauncherGlassStaticNode.find(v);
                    if (node != null) node.dispose();
                }
''',
    '''                @Override public void onViewDetachedFromWindow(View v) {
                    DockGlassItemRegistry.unregister(v);
                    // Preserve the Workspace static node across transient page cache detach.
                    // The node's own attach listener handles session unregister/re-register.
                }
''',
    "preserve static node across page detach",
)

# The exact snapshot-mode method is absent/unhookable on this device build. Fall back to the
# vendor's live-blur visibility semantic, which carries the same idle/live transition directly.
path = ROOT / "Miuix307MaterialPipeline.java"
text = path.read_text()
old = '''        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 vendor snapshot power hook unavailable: " + error);
        }
    }

    /**
     * A fullscreen workstation app can disconnect SurfaceFlinger's PassBlur producer while the
'''
new = '''        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 vendor snapshot power hook unavailable: " + error);
            installVendorStaticDockLiveBlurPowerFallback(classLoader);
        }
    }

    private static void installVendorStaticDockLiveBlurPowerFallback(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeats",
                    "setMingouStaticDockLiveBlurVisible",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        boolean liveBlurVisible = args.length > 0 && args[0] instanceof Boolean
                                && (Boolean) args[0];
                        vendorStaticSnapshotMode = !liveBlurVisible;
                        if (GlassRuntimeState.isEnabled() && !MainHook.isWorkstationMode()) {
                            Miuix307ZeroCopyRenderer.setProducerUpdatesEnabled(liveBlurVisible,
                                    "vendor-live-blur-visible");
                        }
                        MainHook.log("[DC][PBTX][Power] vendorLiveBlurVisible="
                                + liveBlurVisible);
                        return result;
                    }, boolean.class);
            MainHook.log("[DC] MiuiX 307 vendor live-blur power fallback installed");
        } catch (Throwable fallbackError) {
            MainHook.log("[DC] MiuiX 307 vendor live-blur power fallback unavailable: "
                    + fallbackError);
        }
    }

    /**
     * A fullscreen workstation app can disconnect SurfaceFlinger's PassBlur producer while the
'''
if text.count(old) != 1:
    raise SystemExit("vendor snapshot fallback anchor mismatch")
path.write_text(text.replace(old, new, 1))

print("Workspace page glass lifecycle / vendor live-blur fallback patch applied")
