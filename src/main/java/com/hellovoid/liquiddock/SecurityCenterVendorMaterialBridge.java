package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Exact, reversible vendor-material mutation boundary for Security Center. */
final class SecurityCenterVendorMaterialBridge {
    private final SecurityCenterSemanticContractResolver.ResolvedContract contract;
    private final int videoMainContentResId;
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method setMiBloomStroke;
    private final Method setMiShadow;

    SecurityCenterVendorMaterialBridge(
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            int videoMainContentResId) {
        if (contract == null) throw new IllegalArgumentException("contract == null");
        if (videoMainContentResId == 0) {
            throw new IllegalArgumentException("videoMainContentResId == 0");
        }
        this.contract = contract;
        this.videoMainContentResId = videoMainContentResId;
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
    }

    void claimCustom(
            Object turboLayout, View dockLayout, View boxMaterialView, View allAppsLayout) {
        if (!(turboLayout instanceof View) || dockLayout == null) {
            throw new IllegalArgumentException("missing Security Center material owner");
        }
        View turboView = (View) turboLayout;
        validateBoxMaterial(boxMaterialView);

        SecurityCenterVendorMaterialState.claimOwner(
                turboLayout, turboView, dockLayout, boxMaterialView, allAppsLayout);
        SecurityCenterVendorMaterialState.runModuleMutation(() -> {
            clearVendorTarget(turboView);
            clearVendorTarget(dockLayout);
            if (boxMaterialView != null) clearVendorTarget(boxMaterialView);
            if (allAppsLayout != null
                    && allAppsLayout != dockLayout
                    && allAppsLayout != boxMaterialView
                    && allAppsLayout != turboView) {
                clearVendorTarget(allAppsLayout);
            }
        });
    }

    void restoreVendor(Object turboLayout) {
        if (turboLayout == null) throw new IllegalArgumentException("turboLayout == null");
        SecurityCenterVendorMaterialState.restoreOwner(turboLayout);
    }

    private void validateBoxMaterial(View boxMaterialView) {
        if (boxMaterialView == null) return;
        if (contract.gameMaterialClass().isInstance(boxMaterialView)) return;
        if (boxMaterialView.getId() == videoMainContentResId) return;
        throw new IllegalArgumentException(
                "unvalidated Security Center box material carrier: "
                        + boxMaterialView.getClass().getName()
                        + " id=" + boxMaterialView.getId());
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
