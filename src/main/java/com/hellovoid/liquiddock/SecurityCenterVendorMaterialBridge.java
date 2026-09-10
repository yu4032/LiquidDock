package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Exact, reversible HyperOS 4 vendor-material mutation boundary for Security Center. */
final class SecurityCenterVendorMaterialBridge {
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;

    private final SecurityCenterHookSpec spec;
    private final Class<?> materialHelper;
    private final Class<?> gameMaterialViewClass;
    private final Class<?> videoAdapterClass;
    private final int videoMainContentResId;
    private final Map<View, Drawable> savedTargetBackgrounds = new WeakHashMap<>();
    private final Set<View> capturedTargets = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Object, Integer> claimedAssistantTypes = new WeakHashMap<>();
    private final Map<Object, WeakReference<View>> claimedBoxTargets = new WeakHashMap<>();

    SecurityCenterVendorMaterialBridge(
            ClassLoader loader, SecurityCenterHookSpec spec, int videoMainContentResId) {
        if (loader == null) throw new IllegalArgumentException("loader == null");
        if (spec == null) throw new IllegalArgumentException("spec == null");
        if (videoMainContentResId == 0) {
            throw new IllegalArgumentException("videoMainContentResId == 0");
        }
        this.spec = spec;
        this.videoMainContentResId = videoMainContentResId;
        try {
            materialHelper = Class.forName(spec.os4MaterialHelperClass(), false, loader);
            Method reset = HookUtil.findMethodExact(
                    materialHelper, spec.os4MaterialResetMethod(), new Class<?>[]{View.class});
            if (!Modifier.isStatic(reset.getModifiers())) {
                throw new IllegalStateException("vendor material reset is not static");
            }

            Class<?> gameBoxClass = Class.forName(spec.gameToolboxViewClass(), false, loader);
            Method gameMaterialGetter = HookUtil.findMethodExact(
                    gameBoxClass, spec.gameToolboxMaterialGetter(), new Class<?>[0]);
            gameMaterialViewClass = gameMaterialGetter.getReturnType();
            if (!View.class.isAssignableFrom(gameMaterialViewClass)) {
                throw new IllegalStateException("game material getter is not View-returning");
            }
            Method gameRestore = HookUtil.findMethodExact(
                    gameMaterialViewClass,
                    spec.gameToolboxMaterialRestoreMethod(), new Class<?>[0]);
            if (gameRestore.getReturnType() != void.class) {
                throw new IllegalStateException("game material restore shape changed");
            }

            Class<?> turboClass = Class.forName(spec.turboLayoutClass(), false, loader);
            videoAdapterClass = Class.forName(spec.videoToolboxAdapterClass(), false, loader);
            Method videoAdapterGetter = HookUtil.findMethodExact(
                    turboClass, spec.videoToolboxAdapterGetter(), new Class<?>[0]);
            if (!videoAdapterClass.isAssignableFrom(videoAdapterGetter.getReturnType())) {
                throw new IllegalStateException("video adapter getter shape changed");
            }
            Method videoRestore = HookUtil.findMethodExact(
                    videoAdapterClass,
                    spec.videoToolboxMaterialRestoreMethod(), new Class<?>[0]);
            if (videoRestore.getReturnType() != void.class) {
                throw new IllegalStateException("video material restore shape changed");
            }
        } catch (Throwable error) {
            throw new IllegalStateException("Security Center vendor material bridge unavailable", error);
        }
    }

    void claimCustom(
            Object turboLayout, View dockLayout, View boxMaterialView, View allAppsLayout) {
        if (turboLayout == null || dockLayout == null) {
            throw new IllegalArgumentException("missing Security Center material owner");
        }
        int assistantType = classifyBoxMaterial(boxMaterialView);
        claimedAssistantTypes.put(turboLayout, assistantType);
        claimedBoxTargets.put(turboLayout, new WeakReference<>(boxMaterialView));

        // Dock uses the OS4 gq material stack. Reset exactly that stack, then make the ordinary
        // fallback Drawable transparent while Prismal owns presentation.
        HookUtil.requireInvokeStatic(
                materialHelper, spec.os4MaterialResetMethod(), dockLayout);
        MiBlurBridge.clearPassWindowBlur(dockLayout);
        dockLayout.setBackground(null);

        // Game y1 and Video main_content are created by com.miui.common.utils.m.l(), not by a
        // recursive container material. Only disable the exact carrier returned by decompiled
        // authority; restoration replays the vendor's own y1.o()/za.p.t() methods below.
        if (boxMaterialView != null) {
            MiBlurBridge.clearPassWindowBlur(boxMaterialView);
            boxMaterialView.setBackground(null);
        }

        if (allAppsLayout != null && allAppsLayout != dockLayout
                && allAppsLayout != boxMaterialView) {
            if (!capturedTargets.contains(allAppsLayout)) {
                capturedTargets.add(allAppsLayout);
                savedTargetBackgrounds.put(allAppsLayout, allAppsLayout.getBackground());
            }
            HookUtil.requireInvokeStatic(
                    materialHelper, spec.os4MaterialResetMethod(), allAppsLayout);
            MiBlurBridge.clearPassWindowBlur(allAppsLayout);
            allAppsLayout.setBackground(null);
        }
    }

    void restoreVendor(Object turboLayout) {
        if (turboLayout == null) throw new IllegalArgumentException("turboLayout == null");

        // All Apps has no standalone public/vendor reapply entry point in this lifecycle, so its
        // exact captured Drawable object is restored by identity.
        for (View target : new ArrayList<>(capturedTargets)) {
            if (target == null) continue;
            Drawable original = savedTargetBackgrounds.remove(target);
            target.setBackground(original);
            capturedTargets.remove(target);
        }

        Integer assistantType = claimedAssistantTypes.remove(turboLayout);
        WeakReference<View> boxRef = claimedBoxTargets.remove(turboLayout);
        View boxMaterialView = boxRef != null ? boxRef.get() : null;

        // U() is the exact TurboLayout final-Dock material authority.
        HookUtil.requireInvoke(turboLayout, spec.finalBackgroundMethod());

        if (assistantType == null || assistantType == ASSISTANT_GLOBAL_DOCK) return;
        if (assistantType == ASSISTANT_GAME) {
            if (boxMaterialView == null || !gameMaterialViewClass.isInstance(boxMaterialView)) {
                throw new IllegalStateException("game material carrier unavailable during restore");
            }
            HookUtil.requireInvoke(boxMaterialView, spec.gameToolboxMaterialRestoreMethod());
            return;
        }
        if (assistantType == ASSISTANT_VIDEO) {
            Object adapter = HookUtil.requireInvoke(turboLayout, spec.videoToolboxAdapterGetter());
            if (adapter == null || !videoAdapterClass.isInstance(adapter)) {
                throw new IllegalStateException("video material adapter unavailable during restore");
            }
            HookUtil.requireInvoke(adapter, spec.videoToolboxMaterialRestoreMethod());
            return;
        }
        throw new IllegalStateException("unknown claimed assistant type=" + assistantType);
    }

    private int classifyBoxMaterial(View boxMaterialView) {
        if (boxMaterialView == null) return ASSISTANT_GLOBAL_DOCK;
        if (gameMaterialViewClass.isInstance(boxMaterialView)) return ASSISTANT_GAME;
        if (boxMaterialView.getId() == videoMainContentResId) return ASSISTANT_VIDEO;
        throw new IllegalArgumentException(
                "unvalidated Security Center box material carrier: "
                        + boxMaterialView.getClass().getName()
                        + " id=" + boxMaterialView.getId());
    }
}
