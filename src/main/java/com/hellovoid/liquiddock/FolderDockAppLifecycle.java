package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;

/**
 * Scoped Dock-only app lifecycle, using the same state-chart transitions as
 * LaunchApp and LauncherStart. No Dock View alpha or surface hiding overrides.
 *
 * Only enter from the native HOME_STATE after the original FolderStatus event,
 * and only return when this session still owns the synthetic APP_STATE.
 */
final class FolderDockAppLifecycle {
    private static final String TAG = "[DC][FolderDockLifecycle]";
    private static WeakReference<Object> ownedManager = new WeakReference<>(null);
    private static WeakReference<Object> ownedLauncher = new WeakReference<>(null);

    private FolderDockAppLifecycle() {}

    static void onFolderOpened(Object nativeDispatcher) {
        if (nativeDispatcher == null || !GlassRuntimeState.isEnabled()
                || ownedManager.get() != null) return;
        try {
            ClassLoader loader = nativeDispatcher.getClass().getClassLoader();
            Class<?> application = Class.forName("com.miui.home.launcher.Application", false, loader);
            Object launcher = HookUtil.requireInvokeStatic(application, "getLauncher");
            if (launcher == null) return;
            Object controller = HookUtil.requireInvoke(launcher, "getDockController");
            if (controller == null) return;
            Object manager = HookUtil.getField(controller, "dockStateManager");
            if (manager == null || !FolderDockAppLifecyclePolicy.shouldEnter(
                    String.valueOf(HookUtil.requireInvoke(manager, "getState")))) return;

            // Decompiled DockControllerImpl.onLauncherItemClicked delegates to
            // DockStateManager.onLauncherItemClick -> MainStateManager.LaunchApp.
            // This is the native App lifecycle and its composer transactions.
            HookUtil.requireInvoke(manager, "onLauncherItemClick");
            if (!"APP_STATE".equals(String.valueOf(HookUtil.requireInvoke(manager, "getState")))) {
                MainHook.log(TAG + " native App lifecycle declined folder transition");
                return;
            }
            ownedManager = new WeakReference<>(manager);
            ownedLauncher = new WeakReference<>(launcher);
            MainHook.log(TAG + " Dock entered native APP_STATE for folder");
        } catch (Throwable error) {
            MainHook.log(TAG + " native App lifecycle unavailable; OEM FolderState retained: " + error);
        }
    }

    static void onFolderClosed() {
        Object manager = ownedManager.get();
        Object launcher = ownedLauncher.get();
        ownedManager.clear();
        ownedLauncher.clear();
        if (manager == null) return;
        try {
            boolean launchedRealApp = launcher != null
                    && Boolean.TRUE.equals(HookUtil.requireInvoke(launcher, "hasLaunchedAppsFromFolder"));
            boolean launcherPaused = launcher != null && HookUtil.getBooleanField(launcher, "mIsPause");
            String state = String.valueOf(HookUtil.requireInvoke(manager, "getState"));
            if (!FolderDockAppLifecyclePolicy.shouldRestore(
                    state, launchedRealApp, launcherPaused)) {
                MainHook.log(TAG + " native Dock state owned by real app/other scene: " + state);
                return;
            }

            // The matching native APP_STATE -> HOME_STATE lifecycle transition.
            // Do not write alpha, translationY or DockAssistantView visibility.
            ClassLoader loader = manager.getClass().getClassLoader();
            Class<?> launcherStart = Class.forName(
                    "com.miui.home.launcher.dock.v3.LauncherStart", false, loader);
            Constructor<?> ctor = launcherStart.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object event = ctor.newInstance();
            Object mainStateManager = HookUtil.getField(manager, "mainStateManager");
            HookUtil.requireInvoke(mainStateManager, "sendEvent", event);
            MainHook.log(TAG + " Dock returned via native LauncherStart lifecycle");
        } catch (Throwable error) {
            MainHook.log(TAG + " native Dock lifecycle restore unavailable: " + error);
        }
    }
}
