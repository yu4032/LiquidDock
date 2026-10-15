package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Folder-scoped presentation of the native DockContainerView.
 * OEM FolderState remains authoritative for the Dock window transaction.
 * No synthetic state-chart events or Dock window relocation.
 */
final class FolderDockVisibilityOwner {
    private static final String TAG = "[DC][FolderDockVisibility]";
    private static WeakReference<Object> launcherRef = new WeakReference<>(null);
    private static WeakReference<View> rootRef = new WeakReference<>(null);
    private static float originalAlpha = 1f;
    private static boolean open;
    private static ViewTreeObserver observer;

    private static final ViewTreeObserver.OnPreDrawListener PRE_DRAW = () -> {
        View root = rootRef.get();
        if (open && root != null && root.getAlpha() != 0f) {
            root.setAlpha(0f);
            MainHook.log(TAG + " competing Dock root-alpha write corrected");
        }
        return true;
    };
    private static final View.OnAttachStateChangeListener ATTACH =
            new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) {
                    if (!open || rootRef.get() != view) return;
                    observe(view);
                    if (view.getAlpha() != 0f) view.setAlpha(0f);
                }
                @Override public void onViewDetachedFromWindow(View view) {
                    if (rootRef.get() == view) unobserve();
                }
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
            // DockControllerImpl#getDockRootView() is the public interface
            // implementation returning getMDockRootView() and has a stable
            // ViewGroup result. Resolve this one exact zero-argument method,
            // never every method in DockControllerImpl or Launcher.
            Method dockRootGetter = controller.getClass().getDeclaredMethod("getDockRootView");
            dockRootGetter.setAccessible(true);
            Object candidate = dockRootGetter.invoke(controller);
            if (!(candidate instanceof View)) {
                MainHook.log(TAG + " native Dock root not a View controller="
                        + controller.getClass().getName());
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
            root.addOnAttachStateChangeListener(ATTACH);
            observe(root);
            root.setAlpha(0f);
            MainHook.log(TAG + " DockContainerView covered during folder"
                    + " attached=" + root.isAttachedToWindow());
        } catch (Throwable error) {
            MainHook.log(TAG + " native Dock root resolution failed: "
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
        unobserve();
        rootRef.clear();
        if (root == null) return;
        root.removeOnAttachStateChangeListener(ATTACH);
        root.setAlpha(originalAlpha);
        MainHook.log(TAG + " DockContainerView restored alpha=" + originalAlpha);
        originalAlpha = 1f;
    }

    private static void observe(View root) {
        ViewTreeObserver next = root.getViewTreeObserver();
        if (next == observer) return;
        unobserve();
        if (next.isAlive()) {
            next.addOnPreDrawListener(PRE_DRAW);
            observer = next;
        }
    }

    private static void unobserve() {
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(PRE_DRAW);
        observer = null;
    }
}
