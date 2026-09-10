package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Exact, reversible HyperOS 4 vendor-material mutation boundary for Security Center. */
final class SecurityCenterVendorMaterialBridge {
    private final SecurityCenterHookSpec spec;
    private final Class<?> materialHelper;

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

    void claimCustom(Object turboLayout, View dockLayout) {
        if (turboLayout == null || dockLayout == null) {
            throw new IllegalArgumentException("missing Security Center material owner");
        }
        // gq.g.l(View) is the validated HyperOS 4 material reset. Do not touch TurboLayout/root
        // pass-window permission and do not clear unrelated descendants.
        HookUtil.requireInvokeStatic(
                materialHelper, spec.os4MaterialResetMethod(), dockLayout);
        MiBlurBridge.clearPassWindowBlur(dockLayout);
        // TurboLayout.S()/U() may leave shape_gb_turbo_bg as the View's own Drawable even after
        // material/pass-blur reset. Custom ownership means that exact vendor backing surface must
        // become transparent; U() remains the authoritative restoration path on release.
        dockLayout.setBackground(null);
    }

    void restoreVendor(Object turboLayout) {
        if (turboLayout == null) {
            throw new IllegalArgumentException("turboLayout == null");
        }
        // U() is the public vendor semantic that chooses MiGlass/material-token/ordinary blur and
        // restores the complete final background. Never replay private token/shadow parameters.
        HookUtil.requireInvoke(turboLayout, spec.finalBackgroundMethod());
    }
}
