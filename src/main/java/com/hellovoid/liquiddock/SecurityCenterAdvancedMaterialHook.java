package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;

/** Connects Security Center to LiquidDock's existing HyperOS advanced-material capability. */
final class SecurityCenterAdvancedMaterialHook {
    private static boolean installed;

    private SecurityCenterAdvancedMaterialHook() {}

    static synchronized boolean install() {
        if (installed) return true;
        try {
            if (!SecurityCenterVendorMaterialState.install()) {
                log("stable vendor material state interception unavailable");
                return false;
            }
            Method bindAssistant = HookUtil.findMethodExact(
                    SecurityCenterGlassCoordinator.class,
                    "bindAssistant",
                    new Class<?>[]{View.class, View.class, View.class, int.class});
            Method toPortable = HookUtil.findMethodExact(
                    Miuix307PrismalAdapter.class,
                    "toPortable",
                    new Class<?>[]{Miuix307PrismalMaterial.Params.class});
            Method authorize = HookUtil.findMethodExact(
                    SecurityCenterGlassSinkView.class,
                    "setAuthorizedVisible", new Class<?>[]{boolean.class});
            Method claimCustom = HookUtil.findMethodExact(
                    SecurityCenterVendorMaterialBridge.class,
                    "claimCustom", new Class<?>[]{Object.class, View.class, View.class, View.class});
            Method restoreVendor = HookUtil.findMethodExact(
                    SecurityCenterVendorMaterialBridge.class,
                    "restoreVendor", new Class<?>[]{Object.class});
            Method suppressFinal = HookUtil.findMethodExact(
                    SecurityCenterGlassCoordinator.class,
                    "shouldSuppressVendorFinalBackground", new Class<?>[]{Object.class});
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
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object turboArg = chain.getArgs().size() > 0 ? chain.getArgs().get(0) : null;
                Object dockArg = chain.getArgs().size() > 1 ? chain.getArgs().get(1) : null;
                Object appsArg = chain.getArgs().size() > 3 ? chain.getArgs().get(3) : null;
                if (SecurityCenterMaterialModePolicy.currentMode()
                        == LiquidBlurMode.ADVANCED_MATERIAL) {
                    if (!(turboArg instanceof View) || !(dockArg instanceof View)
                            || !SecurityCenterMaterialModePolicy.configureAdvancedMaterial(
                            (View) turboArg,
                            (View) dockArg,
                            appsArg instanceof View ? (View) appsArg : null)) {
                        throw new IllegalStateException(
                                "Security Center advanced material carrier unavailable");
                    }
                }
                return result;
            });
            HookUtil.hook(restoreVendor, chain -> {
                SecurityCenterMaterialModePolicy.releaseAdvancedMaterial();
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(suppressFinal, chain -> {
                if (SecurityCenterMaterialModePolicy.blockCustomPresentation()) return false;
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
            return true;
        } catch (Throwable error) {
            log("advanced material hook unavailable: " + error);
            return false;
        }
    }

    private static void log(String message) {
        try { Api101Bridge.log("[DC][SecurityCenterGlass] " + message); }
        catch (Throwable ignored) {}
    }
}
