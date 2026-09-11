package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.View;

import java.lang.reflect.Method;

/** Connects Security Center to LiquidDock's existing HyperOS advanced-material capability. */
final class SecurityCenterAdvancedMaterialHook {
    private static boolean installed;

    private SecurityCenterAdvancedMaterialHook() {}

    static synchronized void install() {
        if (installed) return;
        try {
            Method bindAssistant = HookUtil.findMethodExact(
                    SecurityCenterGlassCoordinator.class,
                    "bindAssistant",
                    new Class<?>[]{View.class, View.class, View.class, int.class});
            Method toPortable = HookUtil.findMethodExact(
                    Miuix307PrismalAdapter.class,
                    "toPortable",
                    new Class<?>[]{Miuix307PrismalMaterial.Params.class});
            Method attach = HookUtil.findMethodExact(
                    SecurityCenterGlassSession.class,
                    "attachOutput",
                    new Class<?>[]{SecurityCenterGlassSinkView.class, Surface.class,
                            int.class, int.class});
            Method authorize = HookUtil.findMethodExact(
                    SecurityCenterGlassSinkView.class,
                    "setAuthorizedVisible", new Class<?>[]{boolean.class});
            Method claimCustom = HookUtil.findMethodExact(
                    SecurityCenterVendorMaterialBridge.class,
                    "claimCustom", new Class<?>[]{Object.class, View.class, View.class, View.class});
            Method suppressFinal = HookUtil.findMethodExact(
                    SecurityCenterGlassCoordinator.class,
                    "shouldSuppressVendorFinalBackground", new Class<?>[]{Object.class});
            Method dispose = HookUtil.findMethodExact(
                    SecurityCenterGlassSinkView.class, "dispose", new Class<?>[0]);
            Method terminal = HookUtil.findMethodExact(
                    SecurityCenterGlassCoordinator.class,
                    "onVendorPanelTerminal", new Class<?>[]{View.class});
            Method releaseAll = HookUtil.findMethodExact(
                    SecurityCenterGlassCoordinator.class, "releaseAll", new Class<?>[0]);

            HookUtil.hook(bindAssistant, chain -> {
                Object owner = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (!SecurityCenterMaterialModePolicy.prepareBind(owner)) {
                    log("advanced material unavailable; retaining vendor presentation");
                    return null;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(toPortable, chain -> {
                Object arg = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (!SecurityCenterMaterialModePolicy.useShaderBlur()
                        && arg instanceof Miuix307PrismalMaterial.Params) {
                    return Miuix307PrismalAdapter.toPortable(
                            (Miuix307PrismalMaterial.Params) arg, false);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(attach, chain -> {
                Object sink = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                if (sink instanceof SecurityCenterGlassSinkView
                        && !SecurityCenterMaterialModePolicy.configureSink(
                                (SecurityCenterGlassSinkView) sink)) {
                    log("advanced material sink unavailable; vendor remains authoritative");
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(authorize, chain -> {
                boolean visible = !chain.getArgs().isEmpty()
                        && Boolean.TRUE.equals(chain.getArgs().get(0));
                if (visible && SecurityCenterMaterialModePolicy.blockCustomPresentation()) {
                    return chain.proceed(new Object[]{false});
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(claimCustom, chain -> {
                if (SecurityCenterMaterialModePolicy.blockCustomPresentation()) return null;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(suppressFinal, chain -> {
                if (SecurityCenterMaterialModePolicy.blockCustomPresentation()) return false;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(dispose, chain -> {
                Object sink = chain.getThisObject();
                if (sink instanceof SecurityCenterGlassSinkView) {
                    SecurityCenterMaterialModePolicy.releaseSink((SecurityCenterGlassSinkView) sink);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(terminal, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                SecurityCenterMaterialModePolicy.resetLifecycle();
                return result;
            });
            HookUtil.hook(releaseAll, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                SecurityCenterMaterialModePolicy.resetLifecycle();
                return result;
            });
            installed = true;
        } catch (Throwable error) {
            log("advanced material hook unavailable: " + error);
        }
    }

    private static void log(String message) {
        try { Api101Bridge.log("[DC][SecurityCenterGlass] " + message); }
        catch (Throwable ignored) {}
    }
}
