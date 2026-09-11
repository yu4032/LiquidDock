package com.hellovoid.liquiddock;

import android.view.Surface;

import java.lang.reflect.Method;

/** Applies the existing HyperOS advanced-material backend to Security Center output sinks. */
final class SecurityCenterAdvancedMaterialHook {
    private static boolean installed;

    private SecurityCenterAdvancedMaterialHook() {}

    static synchronized void install() {
        if (installed) return;
        try {
            Method attach = HookUtil.findMethodExact(
                    SecurityCenterGlassSession.class,
                    "attachOutput",
                    new Class<?>[]{SecurityCenterGlassSinkView.class, Surface.class,
                            int.class, int.class});
            Method detach = HookUtil.findMethodExact(
                    SecurityCenterGlassSession.class,
                    "detachOutput",
                    new Class<?>[]{SecurityCenterGlassSinkView.class, Surface.class});
            HookUtil.hook(attach, chain -> {
                Object sink = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (sink instanceof SecurityCenterGlassSinkView) {
                    boolean active = SecurityCenterMaterialModePolicy.configureSink((SecurityCenterGlassSinkView) sink);
                    try {
                        Api101Bridge.log("[DC][SecurityCenterGlass] advanced material sink=" + active);
                    } catch (Throwable ignored) {}
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(detach, chain -> {
                Object sink = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (sink instanceof SecurityCenterGlassSinkView) {
                    SecurityCenterMaterialModePolicy.releaseSink((SecurityCenterGlassSinkView) sink);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            installed = true;
        } catch (Throwable error) {
            try {
                Api101Bridge.log("[DC][SecurityCenterGlass] advanced material hook unavailable: " + error);
            } catch (Throwable ignored) {}
        }
    }
}
