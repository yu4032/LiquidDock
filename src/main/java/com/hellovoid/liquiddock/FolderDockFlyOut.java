package com.hellovoid.liquiddock;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Folder-only visual transition using the OEM Dock TransactionComposer's
 * animated off-screen Y transaction. Does not enter APP_STATE or alter the
 * Launcher/Dock state chart, gestures, drag ownership, or window visibility.
 */
final class FolderDockFlyOut {
    private static final String TAG = "[DC][FolderDockFlyOut]";
    // Decompiled DesktopStateManagerFactory.FolderEditState passes this shape
    // to makeTransaction$default when the Dock flies off the bottom.
    private static final int NATIVE_FLY_MASK_WITH_ALPHA = 413 & ~8;
    private static WeakReference<Object> launcherRef = new WeakReference<>(null);
    private static WeakReference<Object> animatedManagerRef = new WeakReference<>(null);

    private FolderDockFlyOut() {}

    static void rememberLauncher(Object launcher) {
        if (launcher != null
                && "com.miui.home.launcher.Launcher".equals(launcher.getClass().getName())) {
            launcherRef = new WeakReference<>(launcher);
        }
    }

    static void onFolderOpened() {
        if (!GlassRuntimeState.isEnabled() || animatedManagerRef.get() != null) return;
        Object launcher = launcherRef.get();
        if (launcher == null) return;
        try {
            Object controller = HookUtil.requireInvoke(launcher, "getDockController");
            if (controller == null) return;
            Object manager = HookUtil.getField(controller, "dockStateManager");
            if (manager == null || !"HOME_STATE".equals(
                    String.valueOf(HookUtil.requireInvoke(manager, "getState")))) return;
            Object composer = HookUtil.getField(manager, "transactionComposer");
            Object consumer = HookUtil.getField(manager, "transactionConsumer");
            if (composer == null || consumer == null) return;

            ClassLoader loader = manager.getClass().getClassLoader();
            Class<?> deviceConfig = Class.forName("com.miui.home.launcher.DeviceConfig", false, loader);
            Object height = HookUtil.requireInvokeStatic(deviceConfig, "getDockWindowHeight");
            if (!(height instanceof Number) || ((Number) height).floatValue() <= 0f) return;
            Class<?> composerType = composer.getClass();
            Method nativeTransaction = null;
            for (Method method : composerType.getDeclaredMethods()) {
                if ("makeTransaction$default".equals(method.getName())
                        && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == 12) {
                    nativeTransaction = method;
                    break;
                }
            }
            if (nativeTransaction == null) return;
            nativeTransaction.setAccessible(true);

            // FolderState has already sent alpha=0 through its OEM transaction.
            // Request a visible moving Dock instead: Y exits below the display
            // with the SAME native animation flags used by FolderEditState.
            Object transaction = nativeTransaction.invoke(null,
                    composer, null, Float.valueOf(((Number) height).floatValue()),
                    null, Float.valueOf(1f), null,
                    true, true, false, false, NATIVE_FLY_MASK_WITH_ALPHA, null);
            if (transaction == null) return;
            HookUtil.requireInvoke(consumer, "onNext", transaction);
            animatedManagerRef = new WeakReference<>(manager);
            MainHook.log(TAG + " native fly-out transaction sent");
        } catch (Throwable error) {
            MainHook.log(TAG + " native fly-out unavailable; OEM folder Dock retained: " + error);
        }
    }

    static void onFolderClosed() {
        // The OEM FolderState.popTransaction() restores the original state,
        // including the baseline Y and alpha, with native animation timing.
        if (animatedManagerRef.get() != null) MainHook.log(TAG + " native folder transaction restored");
        animatedManagerRef.clear();
        launcherRef.clear();
    }
}
