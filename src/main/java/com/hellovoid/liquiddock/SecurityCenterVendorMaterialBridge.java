package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Exact, reversible HyperOS 4 vendor-material mutation boundary for Security Center. */
final class SecurityCenterVendorMaterialBridge {
    private final SecurityCenterHookSpec spec;
    private final Class<?> materialHelper;
    private final Map<View, Drawable> savedTargetBackgrounds = new WeakHashMap<>();
    private final Set<View> capturedTargets =
            Collections.newSetFromMap(new WeakHashMap<>());

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

    void claimCustom(Object turboLayout, View dockLayout, View activeTarget) {
        if (turboLayout == null || dockLayout == null || activeTarget == null) {
            throw new IllegalArgumentException("missing Security Center material owner");
        }
        // gq.g.l(View) is the validated HyperOS 4 material reset. Do not touch TurboLayout/root
        // pass-window permission and do not clear unrelated descendants.
        HookUtil.requireInvokeStatic(
                materialHelper, spec.os4MaterialResetMethod(), dockLayout);
        MiBlurBridge.clearPassWindowBlur(dockLayout);

        // TurboLayout.S()/U() may leave shape_gb_turbo_bg as the dock View's own Drawable even
        // after material/pass-blur reset. Custom ownership requires the vendor backing to be
        // transparent; U() remains the authoritative dock restoration path.
        dockLayout.setBackground(null);

        // All Apps is a separate target layered inside TurboLayout. Preserve only that exact
        // target's original Drawable by identity; never recurse through its descendants or invent
        // replacement colors/materials. The saved object is restored verbatim on ownership release.
        if (activeTarget != dockLayout) {
            if (!capturedTargets.contains(activeTarget)) {
                capturedTargets.add(activeTarget);
                savedTargetBackgrounds.put(activeTarget, activeTarget.getBackground());
            }
            activeTarget.setBackground(null);
        }
    }

    void restoreVendor(Object turboLayout, View ownedTarget) {
        if (turboLayout == null) {
            throw new IllegalArgumentException("turboLayout == null");
        }
        if (ownedTarget != null && capturedTargets.remove(ownedTarget)) {
            Drawable original = savedTargetBackgrounds.remove(ownedTarget);
            ownedTarget.setBackground(original);
        }
        // U() is the public vendor semantic that chooses MiGlass/material-token/ordinary blur and
        // restores the complete final dock background. Never replay private token/shadow parameters.
        HookUtil.requireInvoke(turboLayout, spec.finalBackgroundMethod());
    }
}
