from pathlib import Path

R = Path('src/main/java/com/hellovoid/liquiddock')

def patch(name, pairs):
    p = R / name
    s = p.read_text()
    for old, new in pairs:
        if old not in s:
            raise SystemExit(f'missing anchor {name}: {old[:100]!r}')
        s = s.replace(old, new, 1)
    p.write_text(s)

patch('LauncherGlassSceneController.java', [
    ('    private static boolean vendorFolderCovered;\n',
     '    private static boolean vendorFolderCovered;\n    private static boolean vendorHomeTransitionPending;\n    private static boolean vendorUnlockTransitionPending;\n'),
    ('    private boolean recentsCovered;\n',
     '    private boolean recentsCovered;\n    private boolean homeTransitionPending;\n    private boolean unlockTransitionPending;\n'),
    ('        created.folderCovered = vendorFolderCovered;\n',
     '        created.folderCovered = vendorFolderCovered;\n        created.homeTransitionPending = vendorHomeTransitionPending;\n        created.unlockTransitionPending = vendorUnlockTransitionPending;\n'),
    ('    static void onWallpaperChangedForAll() {\n', '''    static void setHomeTransitionPendingForAll(boolean pending) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorHomeTransitionPending = pending;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setHomeTransitionPending(pending);
        }
    }

    static void setUnlockTransitionPendingForAll(boolean pending) {
        ArrayList<LauncherGlassSceneController> snapshot;
        synchronized (LauncherGlassSceneController.class) {
            vendorUnlockTransitionPending = pending;
            snapshot = new ArrayList<>(BY_ROOT.values());
        }
        for (LauncherGlassSceneController controller : snapshot) {
            if (controller != null) controller.setUnlockTransitionPending(pending);
        }
    }

    static void onWallpaperChangedForAll() {
'''),
    ('        if (layer == null) layer = LauncherGlassStaticLayer.acquire(root, session);\n        applyLayerVisibility();\n',
     '        if (layer == null) layer = LauncherGlassStaticLayer.acquire(root, session);\n        applyLayerVisibility();\n        if (isPresentationPending()) session.suspendWorkspaceProducer();\n'),
    ('    private void requestFreshBackdrop(long generation) {\n        if (state.state() == State.COVERED || generation != state.generation()) return;\n',
     '    private void requestFreshBackdrop(long generation) {\n        if (isPresentationPending()) return;\n        if (state.state() == State.COVERED || generation != state.generation()) return;\n'),
    ('    private synchronized void flushDeferredWallpaperPulse() {\n        if (state.state() == State.COVERED || wallpaperPulseInFlight) return;\n',
     '    private synchronized void flushDeferredWallpaperPulse() {\n        if (isPresentationPending()) return;\n        if (state.state() == State.COVERED || wallpaperPulseInFlight) return;\n'),
    ('    private void setFolderCovered(boolean covered) {\n', '''    private boolean isPresentationPending() {
        return homeTransitionPending || unlockTransitionPending;
    }

    private void setHomeTransitionPending(boolean pending) {
        boolean wasPending = isPresentationPending();
        homeTransitionPending = pending;
        onPresentationPendingChanged(wasPending, isPresentationPending(), "home");
    }

    private void setUnlockTransitionPending(boolean pending) {
        boolean wasPending = isPresentationPending();
        unlockTransitionPending = pending;
        onPresentationPendingChanged(wasPending, isPresentationPending(), "unlock");
    }

    private void onPresentationPendingChanged(boolean wasPending, boolean pending, String reason) {
        if (wasPending == pending) return;
        if (pending) {
            deferInFlightWallpaperPulse();
            state.onGenerationInvalidated();
            applyLayerVisibility();
            session.suspendWorkspaceProducer();
            MainHook.log(TAG + " presentation pending reason=" + reason
                    + " generation=" + state.generation());
            return;
        }
        if (state.state() != State.COVERED && state.state() != State.DETACHED) {
            MainHook.log(TAG + " presentation settled reason=" + reason
                    + " generation=" + state.generation());
            requestFreshBackdrop(state.generation());
        }
    }

    private void setFolderCovered(boolean covered) {
'''),
    ('        if (state.state() == State.COVERED || wallpaperPulseInFlight) {\n',
     '        if (state.state() == State.COVERED || isPresentationPending() || wallpaperPulseInFlight) {\n'),
])

patch('DockGlassCompositor.java', [
    ('    void refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,\n', '''    void invalidateUiScene() {
        lastFingerprint = Long.MIN_VALUE;
        lastOutputFingerprint = Long.MIN_VALUE;
        for (CachedItem item : cached) item.uiFingerprint = Long.MIN_VALUE;
    }

    void refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,
'''),
])

patch('Miuix307PassBlurTextureView.java', [
    ("    /**\n     * Reconnect SurfaceFlinger's PassBlur producer without rebuilding the attached TextureView.\n", '''    void requestDockSceneRefresh() {
        if (shuttingDown) return;
        postOnAnimation(() -> {
            if (shuttingDown) return;
            dockCompositor.invalidateUiScene();
            updateBackdropMapping();
            if (hasConsumedFrame) renderHandler.post(() -> drawLatestFrame(false));
            postInvalidateOnAnimation();
        });
    }

    /**
     * Reconnect SurfaceFlinger's PassBlur producer without rebuilding the attached TextureView.
'''),
])

patch('Miuix307ZeroCopyRenderer.java', [
    ('    static void clear() {\n', '''    static void requestDockSceneRefresh() {
        Miuix307PassBlurTextureView gpuBackdrop = gpuBackdropRef.get();
        if (gpuBackdrop != null) gpuBackdrop.requestDockSceneRefresh();
    }

    static void clear() {
'''),
])

patch('ModuleMain.java', [
    ('            LauncherGlassRecentsHook.install(classLoader, runtimeConfig);\n',
     '            LauncherGlassRecentsHook.install(classLoader, runtimeConfig);\n            LauncherGlassHomePresentationHook.install(classLoader);\n            DockGlassDropRefreshHook.install(classLoader);\n'),
])

manifest = Path('src/main/AndroidManifest.xml')
s = manifest.read_text()
key = '        android:name=".LiquidDockApp"\n'
if key not in s:
    raise SystemExit('manifest app name anchor missing')
manifest.write_text(s.replace(key, key + '        android:label="@string/app_name"\n', 1))

(R / 'LauncherGlassHomePresentationHook.java').write_text(r'''package com.hellovoid.liquiddock;

/** Gates Workspace capture until HyperOS 4.50 HOME/unlock presentation is actually finished. */
final class LauncherGlassHomePresentationHook {
    private static final String TAG = "[DC][GlassScene]";
    private static final String WINDOW_ELEMENT = "com.miui.home.recents.anim.WindowElement";
    private static final String HOME_END_CALLBACK =
            "com.miui.home.recents.anim.WindowElement$mRectFSpringAnimListener$1";
    private static final String CLOSE_TO_HOME = "CLOSE_TO_HOME";
    private static final String CLOSE_TO_HOME_CENTER = "CLOSE_TO_HOME_CENTER";
    private static final String UNLOCK_STATE =
            "com.miui.home.launcher.common.UnlockAnimationStateMachine";
    private static final String PREPARE = "PREPARE";
    private static final String PRESENT_CALLBACK =
            "com.miui.home.launcher.compat.UserPresentAnimationCompatV12Folme$1";
    private static final String VENDOR_RESET_COUNTER = "resetAnimationViewNum";
    private static boolean installed;
    private static boolean homeTransitionArmed;
    private static boolean unlockTransitionArmed;

    private LauncherGlassHomePresentationHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        hookHomeStart(classLoader);
        hookHomeEnd(classLoader);
        hookUnlockStart(classLoader);
        hookUnlockFinish(classLoader, "onComplete");
        hookUnlockFinish(classLoader, "onCancel");
        installed = true;
    }

    private static void hookHomeStart(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, WINDOW_ELEMENT, "animTo", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (containsHomeClose(args)) {
                    homeTransitionArmed = true;
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(true);
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME presentation start unavailable: " + error);
        }
    }

    private static void hookHomeEnd(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, HOME_END_CALLBACK, "onAnimationEnd", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (homeTransitionArmed) {
                    homeTransitionArmed = false;
                    LauncherGlassSceneController.setHomeTransitionPendingForAll(false);
                }
                return result;
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " HOME presentation end unavailable: " + error);
        }
    }

    private static boolean containsHomeClose(Object[] args) {
        if (args == null) return false;
        for (Object arg : args) {
            String token = String.valueOf(arg);
            if (token.contains(CLOSE_TO_HOME_CENTER) || token.contains(CLOSE_TO_HOME)) return true;
        }
        return false;
    }

    private static void hookUnlockStart(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader, UNLOCK_STATE, "setState", chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (args.length > 0 && PREPARE.equals(String.valueOf(args[0]))) {
                    unlockTransitionArmed = true;
                    LauncherGlassSceneController.setUnlockTransitionPendingForAll(true);
                }
                return chain.proceed(args);
            });
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock PREPARE unavailable: " + error);
        }
    }

    private static void hookUnlockFinish(ClassLoader classLoader, String method) {
        try {
            HookUtil.hookMethod(classLoader, PRESENT_CALLBACK, method, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (unlockTransitionArmed) {
                    unlockTransitionArmed = false;
                    LauncherGlassSceneController.setUnlockTransitionPendingForAll(false);
                }
                return result;
            });
            MainHook.log(TAG + " unlock " + method + " barrier installed " + VENDOR_RESET_COUNTER);
        } catch (Throwable error) {
            MainHook.log(TAG + " unlock " + method + " unavailable: " + error);
        }
    }
}
''')

(R / 'DockGlassDropRefreshHook.java').write_text(r'''package com.hellovoid.liquiddock;

/** Recomputes Dock glass after HyperOS commits the final hotseat drop geometry. */
final class DockGlassDropRefreshHook {
    private static final String TAG = "[DC][DockGlass]";
    private static final String HOTSEATS =
            "com.miui.home.launcher.hotseats.HotSeatsListContent";
    private static boolean installed;

    private DockGlassDropRefreshHook() {}

    static void install(ClassLoader classLoader) {
        if (installed) return;
        try {
            HookUtil.hookMethod(classLoader, HOTSEATS, "onDropAnimationFinish", chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Miuix307ZeroCopyRenderer.requestDockSceneRefresh();
                return result;
            });
            installed = true;
            MainHook.log(TAG + " hotseat drop-finish refresh hook installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " hotseat drop-finish refresh hook unavailable: " + error);
        }
    }
}
''')

print('bounded production patch applied')
