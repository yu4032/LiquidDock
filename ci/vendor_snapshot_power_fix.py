from pathlib import Path

ROOT = Path("src/main/java/com/hellovoid/liquiddock")


def patch_once(name, old, new, label):
    path = ROOT / name
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    path.write_text(text.replace(old, new, 1))


# Bridge: Dock needs a persistent resume operation, not Workspace's one-shot pulse/scheduled pause.
patch_once(
    "Miuix307PassBlurBridge.java",
    '''    /** Workspace-only idle suspension. Dock never calls this. */
    static void pauseUpdates(Binding binding) {
''',
    '''    /** Persistent resume used by Dock when HyperOS leaves its HOME snapshot state. */
    static void resumeUpdates(Binding binding) {
        if (binding == null) return;
        setUpdatesEnabled(binding, true);
    }

    /** Workspace idle suspension and vendor-snapshot Dock suspension. */
    static void pauseUpdates(Binding binding) {
''',
    "bridge resume operation",
)

# TextureView: remember requested producer state across theme/rotation BufferQueue rebinds.
patch_once(
    "Miuix307PassBlurTextureView.java",
    '''    private volatile boolean producerRebindPending;
    private volatile int configRotation;
''',
    '''    private volatile boolean producerRebindPending;
    private volatile boolean producerUpdatesEnabled = true;
    private volatile int configRotation;
''',
    "PBTX persistent producer state",
)

patch_once(
    "Miuix307PassBlurTextureView.java",
    '''    void rebindProducer(String reason) {
''',
    '''    void setProducerUpdatesEnabled(boolean enabled, String reason) {
        if (shuttingDown) return;
        producerUpdatesEnabled = enabled;
        renderHandler.post(() -> {
            Miuix307PassBlurBridge.Binding current = binding;
            if (shuttingDown || current == null || !current.bound) return;
            if (enabled) Miuix307PassBlurBridge.resumeUpdates(current);
            else Miuix307PassBlurBridge.pauseUpdates(current);
            MainHook.log(TAG + " producer updates=" + enabled + " reason=" + reason);
        });
    }

    void rebindProducer(String reason) {
''',
    "PBTX producer state API",
)

patch_once(
    "Miuix307PassBlurTextureView.java",
    '''        binding = next;
        producerRebindPending = false;
''',
    '''        binding = next;
        if (!producerUpdatesEnabled) {
            // SetPassBlurSurface always starts continuous. Preserve vendor HOME snapshot state
            // across a producer replacement instead of silently reviving full-rate rendering.
            Miuix307PassBlurBridge.pauseUpdates(next);
        }
        producerRebindPending = false;
''',
    "PBTX apply state after bind",
)

# Renderer facade keeps the vendor state hook independent from TextureView instance churn.
patch_once(
    "Miuix307ZeroCopyRenderer.java",
    '''    static void rebindProducer(String reason) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.rebindProducer(reason);
    }

''',
    '''    static void rebindProducer(String reason) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.rebindProducer(reason);
    }

    static void setProducerUpdatesEnabled(boolean enabled, String reason) {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.setProducerUpdatesEnabled(enabled, reason);
    }

''',
    "zero-copy producer state facade",
)

# HyperOS already has the correct idle/live semantic for its static HOME Dock:
# request snapshot => snapshotMode(false), successful overlay => snapshotMode(true).
# Mirror that event rather than inventing timers or a fixed FPS cap.
patch_once(
    "Miuix307MaterialPipeline.java",
    '''    private static boolean installed;
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    '''    private static boolean installed;
    private static volatile boolean vendorStaticSnapshotMode;
    private static WeakReference<View> workspaceRef = new WeakReference<>(null);
''',
    "pipeline snapshot state field",
)

patch_once(
    "Miuix307MaterialPipeline.java",
    '''            installHotSeatsAttachRecovery(classLoader, config);
            installWorkstationResumeProducerRecovery(classLoader);

''',
    '''            installHotSeatsAttachRecovery(classLoader, config);
            installWorkstationResumeProducerRecovery(classLoader);
            installVendorStaticDockSnapshotPowerHook(classLoader);

''',
    "pipeline snapshot hook install",
)

anchor = '''    /**
     * A fullscreen workstation app can disconnect SurfaceFlinger's PassBlur producer while the
'''
method = '''    /**
     * HyperOS itself switches the static HOME Dock between live blur and a captured overlay.
     * Its snapshot refresh gate is tied to LauncherState.NORMAL, so mirroring this boolean saves
     * idle HOME work without degrading Floating Dock/app realtime behavior.
     */
    private static void installVendorStaticDockSnapshotPowerHook(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeats",
                    "setMingouStaticDockSnapshotMode",
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        Object result = chain.proceed(args);
                        boolean snapshotMode = args.length > 0 && args[0] instanceof Boolean
                                && (Boolean) args[0];
                        vendorStaticSnapshotMode = snapshotMode;
                        if (GlassRuntimeState.isEnabled() && !MainHook.isWorkstationMode()) {
                            Miuix307ZeroCopyRenderer.setProducerUpdatesEnabled(!snapshotMode,
                                    "vendor-static-dock-snapshot");
                        }
                        MainHook.log("[DC][PBTX][Power] vendorStaticSnapshotMode=" + snapshotMode);
                        return result;
                    }, boolean.class);
            MainHook.log("[DC] MiuiX 307 vendor static-Dock snapshot power hook installed");
        } catch (Throwable error) {
            MainHook.log("[DC] MiuiX 307 vendor snapshot power hook unavailable: " + error);
        }
    }

'''
path = ROOT / "Miuix307MaterialPipeline.java"
text = path.read_text()
if text.count(anchor) != 1:
    raise SystemExit("pipeline snapshot method anchor mismatch")
path.write_text(text.replace(anchor, method + anchor, 1))

patch_once(
    "Miuix307MaterialPipeline.java",
    '''        } else {
            MainHook.syncDockShadow(background, config.dock);
            observeBoundHierarchy(background, config, classLoader);
        }
        return installedNow;
''',
    '''        } else {
            MainHook.syncDockShadow(background, config.dock);
            observeBoundHierarchy(background, config, classLoader);
            Miuix307ZeroCopyRenderer.setProducerUpdatesEnabled(!vendorStaticSnapshotMode,
                    "vendor-snapshot-state-after-bind");
        }
        return installedNow;
''',
    "pipeline reapply snapshot after bind",
)

print("vendor HOME snapshot power-state patch applied")
