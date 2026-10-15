package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Folder-scoped presentation ownership of DockControllerImpl.mHotSeatsView.
 * DockWindowManager receives HotSeats, DockContainerView and dockViewContainer
 * separately: hiding only DockContainerView misses the view-switch path.
 * OEM FolderState remains authoritative for native window transactions.
 */
final class FolderDockVisibilityOwner {
    private static final String TAG = "[DC][FolderDockVisibility]";
    private static WeakReference<Object> launcherRef = new WeakReference<>(null);
    private static WeakReference<View> rootRef = new WeakReference<>(null);
    private static float originalAlpha = 1f;
    private static boolean open;
    private static boolean alphaHookInstalled;
    private static int interceptedAlphaWrites;

    /** HotSeats overrides View.setAlpha: each native animation frame rewrites
     * its private mAlpha and its children. Fence the exact writer, not preDraw.
     * No repeated View invalidation or source-level animation cancellation.
     */
    static void install(ClassLoader loader) {
        if (alphaHookInstalled || loader == null) return;
        try {
            Class<?> hotSeats = Class.forName(
                    "com.miui.home.launcher.hotseats.HotSeats", false, loader);
            Method setter = hotSeats.getDeclaredMethod("setAlpha", float.class);
            setter.setAccessible(true);
            HookUtil.hook(setter, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (open && chain.getThisObject() == rootRef.get()
                        && args.length == 1 && args[0] instanceof Number
                        && ((Number) args[0]).floatValue() != 0f) {
                    interceptedAlphaWrites++;
                    return chain.proceed(new Object[]{0f});
                }
                return chain.proceed(args);
            });
            alphaHookInstalled = true;
            MainHook.log(TAG + " HotSeats.setAlpha writer fence installed");
        } catch (Throwable error) {
            MainHook.log(TAG + " HotSeats.setAlpha writer fence unavailable: " + error);
        }
    }
    private static final View.OnAttachStateChangeListener ATTACH =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    if (!open || rootRef.get() != view) return;
                    // Reparenting can rebuild child presentation properties.
                    // Reassert the zero only once per actual attachment.
                    view.setAlpha(0f);
                }
                @Override public void onViewDetachedFromWindow(View view) {}
            };

    private FolderDockVisibilityOwner() {}

    static void rememberLauncher(Object launcher) {
        if (launcher != null &&
                "com.miui.home.launcher.Launcher".equals(launcher.getClass().getName())) {
            launcherRef = new WeakReference<>(launcher);
        }
    }

    static void onFolderOpened() { onFolderOpened(null); }

    static void onFolderOpened(Object nativeDispatcher) {
        Object launcher = launcherRef.get();
        if (launcher == null && nativeDispatcher != null) {
            try {
                // Verified against DockStateManager.java: Application.getLauncher()
                // is also used by the original vendor listener implementation.
                Class<?> application = Class.forName("com.miui.home.launcher.Application",
                        false, nativeDispatcher.getClass().getClassLoader());
                // Resolving all methods on Launcher/Application can load
                // unrelated optional NewHomeView signatures and fail before
                // getLauncher() is even invoked. Resolve only the known method.
                Method getLauncher = application.getDeclaredMethod("getLauncher");
                getLauncher.setAccessible(true);
                launcher = getLauncher.invoke(null);
                rememberLauncher(launcher);
            } catch (Throwable error) {
                MainHook.log(TAG + " Launcher fallback unavailable: " + error);
            }
        }
        if (launcher == null) return;
        try {
            // Launcher#getDockController() is exactly 'return mDockController'.
            // Do not use HookUtil.requireInvoke here: VendorMemberResolver
            // scans every declared Launcher method. Launcher 4.50 contains a
            // getNewHomeView(): NewHomeView method whose optional class is
            // absent on this device. Enumeration throws NoClassDefFoundError
            // before the unrelated getDockController can be resolved.
            Field dockControllerField = launcher.getClass().getDeclaredField("mDockController");
            dockControllerField.setAccessible(true);
            Object controller = dockControllerField.get(launcher);
            if (controller == null) {
                MainHook.log(TAG + " native Dock controller not initialized");
                return;
            }
            // Decompiled DockControllerImpl#setupDockEnv passes mHotSeatsView
            // to DockWindowManager next to DockContainerView and
            // dockViewContainer. HotSeats owns the real visual content in
            // either hosting mode, whereas DockContainerView owns only one.
            Field hotSeatsField = controller.getClass().getDeclaredField("mHotSeatsView");
            hotSeatsField.setAccessible(true);
            Object candidate = hotSeatsField.get(controller);
            if (!(candidate instanceof View)) {
                MainHook.log(TAG + " native HotSeats unavailable type="
                        + (candidate == null ? "null" : candidate.getClass().getName()));
                return;
            }
            View root = (View) candidate;
            if (open && rootRef.get() == root) {
                if (root.getAlpha() != 0f) root.setAlpha(0f);
                return;
            }
            releaseRoot();
            originalAlpha = root.getAlpha();
            rootRef = new WeakReference<>(root);
            open = true;
            interceptedAlphaWrites = 0;
            root.addOnAttachStateChangeListener(ATTACH);
            root.setAlpha(0f);
            MainHook.log(TAG + " HotSeats covered during folder"
                    + " attached=" + root.isAttachedToWindow()
                    + " parent=" + (root.getParent() == null ? "none"
                    : root.getParent().getClass().getSimpleName())
                    + " id=" + Integer.toHexString(System.identityHashCode(root)));
        } catch (Throwable error) {
            MainHook.log(TAG + " native HotSeats resolution failed: "
                    + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    static void onFolderClosed() {
        releaseRoot();
        launcherRef.clear();
    }

    private static void releaseRoot() {
        View root = rootRef.get();
        open = false;
        rootRef.clear();
        if (root == null) return;
        root.removeOnAttachStateChangeListener(ATTACH);
        root.setAlpha(originalAlpha);
        MainHook.log(TAG + " HotSeats restored alpha=" + originalAlpha
                + " preventedNativeAlphaWrites=" + interceptedAlphaWrites);
        originalAlpha = 1f;
        interceptedAlphaWrites = 0;
    }
}
