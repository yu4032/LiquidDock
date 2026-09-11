package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Exact, reversible vendor-material mutation boundary for Security Center. */
final class SecurityCenterVendorMaterialBridge {
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;

    private final SecurityCenterSemanticContractResolver.ResolvedContract contract;
    private final int videoMainContentResId;
    private final Method setMiViewBlurMode;
    private final Method clearMiBackgroundBlendColor;
    private final Method setMiBloomStroke;
    private final Method setMiShadow;
    private final Map<View, Drawable> savedTargetBackgrounds = new WeakHashMap<>();
    private final Set<View> capturedTargets = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Object, Integer> claimedAssistantTypes = new WeakHashMap<>();
    private final Map<Object, WeakReference<View>> claimedBoxTargets = new WeakHashMap<>();

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
        int assistantType = classifyBoxMaterial(boxMaterialView);
        claimedAssistantTypes.put(turboLayout, assistantType);
        claimedBoxTargets.put(turboLayout, new WeakReference<>(boxMaterialView));

        resetVendorMaterial(turboView);
        MiBlurBridge.clearPassWindowBlur(turboView);
        turboView.setBackground(null);

        resetVendorMaterial(dockLayout);
        MiBlurBridge.clearPassWindowBlur(dockLayout);
        dockLayout.setBackground(null);

        if (boxMaterialView != null) {
            resetVendorMaterial(boxMaterialView);
            MiBlurBridge.clearPassWindowBlur(boxMaterialView);
            boxMaterialView.setBackground(null);
        }

        if (allAppsLayout != null && allAppsLayout != dockLayout
                && allAppsLayout != boxMaterialView) {
            if (!capturedTargets.contains(allAppsLayout)) {
                capturedTargets.add(allAppsLayout);
                savedTargetBackgrounds.put(allAppsLayout, allAppsLayout.getBackground());
            }
            resetVendorMaterial(allAppsLayout);
            MiBlurBridge.clearPassWindowBlur(allAppsLayout);
            allAppsLayout.setBackground(null);
        }
    }

    void restoreVendor(Object turboLayout) {
        if (turboLayout == null) throw new IllegalArgumentException("turboLayout == null");

        for (View target : new ArrayList<>(capturedTargets)) {
            if (target == null) continue;
            Drawable original = savedTargetBackgrounds.remove(target);
            target.setBackground(original);
            capturedTargets.remove(target);
        }

        Integer assistantType = claimedAssistantTypes.remove(turboLayout);
        WeakReference<View> boxRef = claimedBoxTargets.remove(turboLayout);
        View boxMaterialView = boxRef != null ? boxRef.get() : null;

        invoke(contract.finalBackground(), turboLayout);

        if (assistantType == null || assistantType == ASSISTANT_GLOBAL_DOCK) return;
        if (assistantType == ASSISTANT_GAME) {
            if (boxMaterialView == null || !contract.gameMaterialClass().isInstance(boxMaterialView)) {
                throw new IllegalStateException("game material carrier unavailable during restore");
            }
            invoke(contract.gameMaterialRestore(), boxMaterialView);
            return;
        }
        if (assistantType == ASSISTANT_VIDEO) {
            Object adapter = invoke(contract.videoAdapterGetter(), turboLayout);
            if (adapter == null || !contract.videoAdapterClass().isInstance(adapter)) {
                throw new IllegalStateException("video material adapter unavailable during restore");
            }
            invoke(contract.videoMaterialRestore(), adapter);
            return;
        }
        throw new IllegalStateException("unknown claimed assistant type=" + assistantType);
    }

    private void resetVendorMaterial(View target) {
        invoke(setMiViewBlurMode, target, 0);
        invoke(clearMiBackgroundBlendColor, target);
        invoke(setMiBloomStroke, target, (Object) new float[21]);
        invoke(setMiShadow, target, 0, 0f, 0f, 0f, 1f);
    }

    private int classifyBoxMaterial(View boxMaterialView) {
        if (boxMaterialView == null) return ASSISTANT_GLOBAL_DOCK;
        if (contract.gameMaterialClass().isInstance(boxMaterialView)) return ASSISTANT_GAME;
        if (boxMaterialView.getId() == videoMainContentResId) return ASSISTANT_VIDEO;
        throw new IllegalArgumentException(
                "unvalidated Security Center box material carrier: "
                        + boxMaterialView.getClass().getName()
                        + " id=" + boxMaterialView.getId());
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
