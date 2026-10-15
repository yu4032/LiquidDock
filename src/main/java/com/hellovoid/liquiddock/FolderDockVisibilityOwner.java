package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.ref.WeakReference;

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
                launcher = HookUtil.requireInvokeStatic(application, "getLauncher");
                rememberLauncher(launcher);
            } catch (Throwable error) {
                MainHook.log(TAG + " Launcher fallback unavailable: " + error);
            }
        }
        if (launcher == null) return;
        try {
            Object controller = HookUtil.requireInvoke(launcher, "getDockController");
            if (controller == null) return;
            // Do NOT gate on DockStateManager.getState() == HOME_STATE:
            // native window transitions are independent of FolderCling.open,
            // and the state can still be settling as the folder appears.
            Object candidate = HookUtil.requireInvoke(controller, "getMDockRootView");
            if (!(candidate instanceof View)) return;
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
            MainHook.log(TAG + " native Dock root unavailable; OEM FolderState remains: " + error);
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
