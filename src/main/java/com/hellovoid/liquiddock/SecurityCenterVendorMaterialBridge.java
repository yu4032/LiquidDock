package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Stable View-API boundary for Security Center Dock material ownership. */
final class SecurityCenterVendorMaterialBridge {
    private static volatile SecurityCenterVendorMaterialBridge activeBridge;

    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method setMiBloomStroke;
    private final Method setMiShadow;
    private WeakReference<Object> claimedOwner = new WeakReference<>(null);
    private WeakReference<Object> frameworkOwner = new WeakReference<>(null);
    private WeakReference<View> frameworkDock = new WeakReference<>(null);

    SecurityCenterVendorMaterialBridge(
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            int videoMainContentResId) {
        if (contract == null) throw new IllegalArgumentException("contract == null");
        if (videoMainContentResId == 0) {
            throw new IllegalArgumentException("videoMainContentResId == 0");
        }
        SecurityCenterEarlyPrepareHook.install(contract, videoMainContentResId);
        try {
            setMiViewBlurMode = HookUtil.findMethodExact(
                    View.class, "setMiViewBlurMode", new Class<?>[]{int.class});
            clearMiBackgroundBlendColor = HookUtil.findMethodExact(
                    View.class, "clearMiBackgroundBlendColor", new Class<?>[0]);
            setMiBloomStroke = HookUtil.findMethodExact(
                    View.class, "setMiBloomStroke", new Class<?>[]{float[].class});
            setMiShadow = HookUtil.findMethodExact(
                    View.class, "setMiShadow",
                    new Class<?>[]{int.class, float.class, float.class, float.class, float.class});
        } catch (Throwable error) {
            throw new IllegalStateException("Security Center vendor material API unavailable", error);
        }
        activeBridge = this;
    }

    /** Eager framework-material claim used once the semantic Dock carrier is ready. */
    static boolean claimFrameworkDock(View turboLayout, View dockLayout) {
        SecurityCenterVendorMaterialBridge bridge = activeBridge;
        return bridge != null && bridge.claimFrameworkDockInternal(turboLayout, dockLayout);
    }

    /** Full teardown of either framework or legacy custom material ownership. */
    static void releaseClaim() {
        SecurityCenterVendorMaterialBridge bridge = activeBridge;
        if (bridge != null) bridge.releaseClaimInternal();
    }

    void protectVendorFallback(
            Object turboLayout, View dockLayout, View boxMaterialView, View allAppsLayout) {
        if (!(turboLayout instanceof View) || dockLayout == null) {
            throw new IllegalArgumentException("missing Security Center material owner");
        }
        if (!protectVendorFallbackInternal(
                (View) turboLayout, dockLayout, boxMaterialView, allAppsLayout)) {
            throw new IllegalStateException("Security Center vendor fallback unavailable");
        }
    }

    void claimCustom(
            Object turboLayout, View dockLayout, View boxMaterialView, View allAppsLayout) {
        if (!(turboLayout instanceof View) || dockLayout == null) {
            throw new IllegalArgumentException("missing Security Center material owner");
        }
        if (!claimCustomInternal(
                (View) turboLayout, dockLayout, boxMaterialView, allAppsLayout)) {
            throw new IllegalStateException("Security Center Dock material unavailable");
        }
    }

    void restoreVendor(Object turboLayout) {
        if (turboLayout == null) throw new IllegalArgumentException("turboLayout == null");
        if (frameworkOwner.get() == turboLayout) {
            if (SecurityCenterMaterialModePolicy.retainFrameworkDockOnPanelClose()) return;
            releaseFrameworkDockInternal(turboLayout);
            return;
        }
        releaseClaimInternal(turboLayout);
    }

    private synchronized boolean claimFrameworkDockInternal(View turboLayout, View dockLayout) {
        if (turboLayout == null || dockLayout == null) return false;
        Object previousFrameworkOwner = frameworkOwner.get();
        if (previousFrameworkOwner != null && previousFrameworkOwner != turboLayout) {
            releaseFrameworkDockInternal(previousFrameworkOwner);
        }
        Object previousCustomOwner = claimedOwner.get();
        if (previousCustomOwner != null) {
            releaseClaimInternal(previousCustomOwner);
        }
        if (!SecurityCenterMaterialModePolicy.prepareBind(turboLayout)) return false;

        try {
            SecurityCenterVendorMaterialState.claimOwner(turboLayout, dockLayout);
            SecurityCenterVendorMaterialState.runModuleMutation(
                    () -> clearVendorTarget(dockLayout));
            if (!SecurityCenterMaterialModePolicy.configureAdvancedMaterial(
                    turboLayout, dockLayout)) {
                throw new IllegalStateException("framework Dock material apply failed");
            }
            frameworkOwner = new WeakReference<>(turboLayout);
            frameworkDock = new WeakReference<>(dockLayout);
            return true;
        } catch (Throwable error) {
            SecurityCenterMaterialModePolicy.releaseAdvancedMaterial();
            try { SecurityCenterVendorMaterialState.restoreOwner(turboLayout); }
            catch (Throwable ignored) {}
            frameworkOwner = new WeakReference<>(null);
            frameworkDock = new WeakReference<>(null);
            SecurityCenterMaterialModePolicy.resetLifecycle();
            return false;
        }
    }

    private synchronized boolean protectVendorFallbackInternal(
            View turboLayout, View dockLayout, View boxMaterialView, View allAppsLayout) {
        if (turboLayout == null || dockLayout == null) return false;
        Object previousOwner = claimedOwner.get();
        if (previousOwner != null && previousOwner != turboLayout) {
            releaseClaimInternal(previousOwner);
        }
        if (!SecurityCenterMaterialModePolicy.prepareBind(turboLayout)) return false;

        try {
            SecurityCenterVendorMaterialState.claimOwner(
                    turboLayout, dockLayout, boxMaterialView, allAppsLayout);
            claimedOwner = new WeakReference<>(turboLayout);
            return true;
        } catch (Throwable error) {
            try { SecurityCenterVendorMaterialState.restoreOwner(turboLayout); }
            catch (Throwable ignored) {}
            claimedOwner = new WeakReference<>(null);
            return false;
        }
    }

    private synchronized boolean claimCustomInternal(
            View turboLayout, View dockLayout, View boxMaterialView, View allAppsLayout) {
        if (!protectVendorFallbackInternal(
                turboLayout, dockLayout, boxMaterialView, allAppsLayout)) return false;
        try {
            SecurityCenterVendorMaterialState.runModuleMutation(() -> {
                clearVendorTarget(dockLayout);
                clearVendorTarget(boxMaterialView);
                clearVendorTarget(allAppsLayout);
            });
            return true;
        } catch (Throwable error) {
            try { SecurityCenterVendorMaterialState.restoreOwner(turboLayout); }
            catch (Throwable ignored) {}
            claimedOwner = new WeakReference<>(null);
            return false;
        }
    }

    private synchronized void releaseClaimInternal() {
        boolean released = false;
        Object owner = frameworkOwner.get();
        if (owner != null) {
            releaseFrameworkDockInternal(owner);
            released = true;
        }
        Object customOwner = claimedOwner.get();
        if (customOwner != null) {
            releaseClaimInternal(customOwner);
            released = true;
        }
        if (!released) {
            SecurityCenterMaterialModePolicy.releaseAdvancedMaterial();
            SecurityCenterMaterialModePolicy.resetLifecycle();
        }
    }

    private synchronized void releaseFrameworkDockInternal(Object owner) {
        if (owner == null) return;
        SecurityCenterMaterialModePolicy.releaseAdvancedMaterial();
        SecurityCenterVendorMaterialState.restoreOwner(owner);
        if (frameworkOwner.get() == owner) {
            frameworkOwner = new WeakReference<>(null);
            frameworkDock = new WeakReference<>(null);
        }
        SecurityCenterMaterialModePolicy.resetLifecycle();
    }

    private synchronized void releaseClaimInternal(Object owner) {
        if (owner == null) return;
        SecurityCenterVendorMaterialState.restoreOwner(owner);
        if (claimedOwner.get() == owner) {
            claimedOwner = new WeakReference<>(null);
        }
        SecurityCenterMaterialModePolicy.resetLifecycle();
    }

    private void clearVendorTarget(View target) {
        if (target == null) return;
        resetVendorMaterial(target);
        MiBlurBridge.clearPassWindowBlur(target);
        target.setBackground(null);
    }

    private void resetVendorMaterial(View target) {
        invoke(setMiViewBlurMode, target, 0);
        invoke(clearMiBackgroundBlendColor, target);
        invoke(setMiBloomStroke, target, (Object) new float[21]);
        invoke(setMiShadow, target, 0, 0f, 0f, 0f, 1f);
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("vendor material invocation failed", cause);
        } catch (Throwable error) {
            throw new IllegalStateException("vendor material invocation failed", error);
        }
    }
}
