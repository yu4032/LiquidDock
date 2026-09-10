package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Exact, reversible HyperOS 4 vendor-material mutation boundary for Security Center. */
final class SecurityCenterVendorMaterialBridge {
    private final SecurityCenterHookSpec spec;
    private final Class<?> materialHelper;
    private final Map<View, Drawable> savedTargetBackgrounds = new WeakHashMap<>();
    private final Set<View> capturedTargets = Collections.newSetFromMap(new WeakHashMap<>());

    SecurityCenterVendorMaterialBridge(ClassLoader loader, SecurityCenterHookSpec spec) {
        if (loader == null) throw new IllegalArgumentException("loader == null");
        if (spec == null) throw new IllegalArgumentException("spec == null");
        this.spec = spec;
        try {
            materialHelper = Class.forName(spec.os4MaterialHelperClass(), false, loader);
            Method reset = HookUtil.findMethodExact(
                    materialHelper, spec.os4MaterialResetMethod(), new Class<?>[]{View.class});
            if (!Modifier.isStatic(reset.getModifiers())) {
                throw new IllegalStateException("vendor material reset is not static");
            }
        } catch (Throwable error) {
            throw new IllegalStateException("Security Center vendor material bridge unavailable", error);
        }
    }

    void claimCustom(Object turboLayout, View dockLayout, View allAppsLayout) {
        if (turboLayout == null || dockLayout == null) {
            throw new IllegalArgumentException("missing Security Center material owner");
        }
        HookUtil.requireInvokeStatic(
                materialHelper, spec.os4MaterialResetMethod(), dockLayout);
        MiBlurBridge.clearPassWindowBlur(dockLayout);
        dockLayout.setBackground(null);

        if (allAppsLayout != null && allAppsLayout != dockLayout) {
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
        for (View target : new ArrayList<>(capturedTargets)) {
            if (target == null) continue;
            Drawable original = savedTargetBackgrounds.remove(target);
            target.setBackground(original);
            capturedTargets.remove(target);
        }
        HookUtil.requireInvoke(turboLayout, spec.finalBackgroundMethod());
    }
}
