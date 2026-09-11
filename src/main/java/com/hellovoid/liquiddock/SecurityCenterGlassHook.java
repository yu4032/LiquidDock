package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.WeakHashMap;

/** Semantic, fail-closed Security Center hooks for Game / Video / Global Dock liquid glass. */
final class SecurityCenterGlassHook {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Object LOCK = new Object();
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;
    private static final WeakHashMap<View, Integer> CONFIGURED_ASSISTANTS = new WeakHashMap<>();
    private static final SecurityCenterOneShotTipPolicy UNSUPPORTED_TIP =
            new SecurityCenterOneShotTipPolicy();
    private static final SecurityCenterHookActivationState ACTIVATION =
            new SecurityCenterHookActivationState();

    private static boolean bootstrapInstalled;
    private static boolean validatedHooksInstalled;
    private static ClassLoader installedLoader;
    private static LiquidDockConfig installedConfig;
    private static SecurityCenterSemanticContractResolver.ResolvedContract installedContract;
    private static SecurityCenterGlassCoordinator coordinator;

    private SecurityCenterGlassHook() {}

    static void install(ClassLoader classLoader, LiquidDockConfig initialConfig) {
        if (classLoader == null || initialConfig == null) return;
        synchronized (LOCK) {
            installedLoader = classLoader;
            installedConfig = initialConfig;
            if (bootstrapInstalled) return;
            try {
                Class<?> serviceClass = Class.forName(
                        SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS, false, classLoader);
                Method onCreate = HookUtil.findMethodExact(serviceClass, "onCreate", new Class<?>[0]);
                HookUtil.hook(onCreate, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (!(owner instanceof Context)) {
                        log("bootstrap owner is not Context", null);
                        return result;
                    }
                    Context context = (Context) owner;
                    try {
                        installValidatedHooksOnce(context);
                    } catch (Throwable error) {
                        ACTIVATION.onValidationFailed();
                        showUnsupportedTipOnce(context);
                        log("semantic hook installation failed closed", error);
                    }
                    return result;
                });
                bootstrapInstalled = true;
                log("bootstrap hook installed", null);
            } catch (Throwable error) {
                log("bootstrap hook unavailable", error);
            }
        }
    }

    private static void installValidatedHooksOnce(Context service) throws Exception {
        final ClassLoader loader;
        final LiquidDockConfig config;
        synchronized (LOCK) {
            if (validatedHooksInstalled) return;
            loader = installedLoader;
            config = installedConfig;
        }
        if (loader == null || config == null || service == null) return;

        final Resources resources = service.getResources();
        final String resourcePackage = service.getPackageName();
        final int allAppsCornerRadiusResId = resolveResource(
                resources, resourcePackage, "dimen", SecurityCenterHookSpec.ALL_APPS_RADIUS_RESOURCE);
        final int gameToolboxCornerRadiusResId = resolveResource(
                resources, resourcePackage, "dimen", SecurityCenterHookSpec.GAME_RADIUS_RESOURCE);
        final int videoMainContentResId = resolveResource(
                resources, resourcePackage, "id", SecurityCenterHookSpec.VIDEO_CONTENT_RESOURCE);

        Set<String> capabilities = new HashSet<>();
        capabilities.add("dimen:" + SecurityCenterHookSpec.ALL_APPS_RADIUS_RESOURCE);
        capabilities.add("dimen:" + SecurityCenterHookSpec.GAME_RADIUS_RESOURCE);
        capabilities.add("id:" + SecurityCenterHookSpec.VIDEO_CONTENT_RESOURCE);

        Class<?> turboClass = Class.forName(
                SecurityCenterHookSpec.TURBO_LAYOUT_CLASS, false, loader);
        SecurityCenterSemanticContractResolver.ResolvedContract contract =
                SecurityCenterSemanticContractResolver.resolve(turboClass, View.class, capabilities);
        SecurityCenterSemanticContractResolver.AllAppsMotionContract allAppsMotion =
                SecurityCenterSemanticContractResolver.resolveAllAppsMotion(turboClass, View.class);
        SecurityCenterSemanticContractResolver.TerminalCleanupContract terminalCleanup =
                SecurityCenterSemanticContractResolver.resolveTerminalCleanup(
                        contract.managerClass(), contract.turboClass(),
                        contract.wrapperClass(), View.class);

        SecurityCenterVendorMaterialBridge vendorBridge =
                new SecurityCenterVendorMaterialBridge(contract, videoMainContentResId);
        SecurityCenterGlassCoordinator nextCoordinator =
                new SecurityCenterGlassCoordinator(
                        config.glass, vendorBridge,
                        allAppsCornerRadiusResId, gameToolboxCornerRadiusResId);

        synchronized (LOCK) {
            if (validatedHooksInstalled) {
                nextCoordinator.releaseAll();
                return;
            }
            installedContract = contract;
            coordinator = nextCoordinator;

            HookUtil.hook(contract.configure(), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (!ACTIVATION.allowsMutation()) return result;
                Object turboObject = chain.getThisObject();
                Object typeArg = chain.getArgs().size() > 4 ? chain.getArgs().get(4) : null;
                if (!(turboObject instanceof View) || typeArg == null) return result;
                View turbo = (View) turboObject;
                try {
                    int assistantType = resolveAssistantType(typeArg, contract);
                    synchronized (LOCK) {
                        if (assistantType != 0) CONFIGURED_ASSISTANTS.put(turbo, assistantType);
                        else CONFIGURED_ASSISTANTS.remove(turbo);
                    }
                } catch (Throwable error) {
                    synchronized (LOCK) { CONFIGURED_ASSISTANTS.remove(turbo); }
                    log("assistant configure observation failed", error);
                }
                return result;
            });

            HookUtil.hook(contract.dockReady(), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (!ACTIVATION.allowsMutation()) return result;
                Object turboObject = chain.getThisObject();
                if (!(turboObject instanceof View)) return result;
                View turbo = (View) turboObject;
                final Integer assistantType;
                synchronized (LOCK) { assistantType = CONFIGURED_ASSISTANTS.get(turbo); }
                if (assistantType == null) return result;

                SecurityCenterGlassCoordinator live = currentCoordinator(contract);
                try {
                    Object dockObject = invoke(contract.dockGetter(), turbo);
                    if (!(dockObject instanceof View)) {
                        throw new IllegalStateException("dock getter returned non-View");
                    }
                    View boxMaterialView = null;
                    if (assistantType == ASSISTANT_GAME || assistantType == ASSISTANT_VIDEO) {
                        Object boxObject = invoke(contract.boxGetter(), turbo);
                        if (!(boxObject instanceof View)) {
                            throw new IllegalStateException("box getter returned non-View");
                        }
                        View box = (View) boxObject;
                        if (assistantType == ASSISTANT_GAME) {
                            if (!contract.gameBoxClass().isInstance(box)) {
                                throw new IllegalStateException("game box relation changed");
                            }
                            Object material = invoke(contract.gameMaterialGetter(), box);
                            if (!(material instanceof View)
                                    || !contract.gameMaterialClass().isInstance(material)) {
                                throw new IllegalStateException("game material carrier unavailable");
                            }
                            boxMaterialView = (View) material;
                        } else {
                            View material = box.findViewById(videoMainContentResId);
                            if (material == null || material.getId() != videoMainContentResId) {
                                throw new IllegalStateException("video material carrier unavailable");
                            }
                            boxMaterialView = material;
                        }
                    }
                    if (live != null) {
                        live.bindAssistant(turbo, (View) dockObject, boxMaterialView, assistantType);
                    }
                } catch (Throwable error) {
                    if (live != null) live.releaseAll();
                    log("assistant dock-ready observation failed", error);
                }
                return result;
            });

            HookUtil.hook(allAppsMotion.attach(), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (ACTIVATION.allowsMutation()) {
                    observeAllAppsTransitionStart(
                            chain.getArgs().size() > 1 ? chain.getArgs().get(1) : null, contract);
                }
                return result;
            });
            HookUtil.hook(allAppsMotion.dismiss(), chain -> {
                if (ACTIVATION.allowsMutation()) {
                    observeAllAppsTransitionStart(
                            chain.getArgs().isEmpty() ? null : chain.getArgs().get(0), contract);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(allAppsMotion.dismissToPoint(), chain -> {
                if (ACTIVATION.allowsMutation()) {
                    observeAllAppsTransitionStart(
                            chain.getArgs().isEmpty() ? null : chain.getArgs().get(0), contract);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hook(contract.removeAnimated(), chain -> {
                if (ACTIVATION.allowsMutation()) notifyVendorPanelClosing(chain.getArgs(), contract);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(contract.removeWithoutAnimation(), chain -> {
                if (ACTIVATION.allowsMutation()) notifyVendorPanelClosing(chain.getArgs(), contract);
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (ACTIVATION.allowsMutation()) {
                    notifyVendorPanelTerminalFromWrapper(chain.getArgs(), contract);
                }
                return result;
            });
            for (Method terminalMethod : terminalCleanup.methods()) {
                HookUtil.hook(terminalMethod, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    if (ACTIVATION.allowsMutation()) {
                        notifyVendorPanelTerminal(chain.getArgs(), contract);
                    }
                    return result;
                });
            }

            HookUtil.hook(contract.finalBackground(), chain -> {
                if (!ACTIVATION.allowsMutation()) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                Object turbo = chain.getThisObject();
                SecurityCenterGlassCoordinator live = currentCoordinator(contract);
                if (live != null && live.shouldSuppressVendorFinalBackground(turbo)) return null;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            ACTIVATION.onCallbacksRegistered();
            ACTIVATION.onValidationCommitted();
            if (!ACTIVATION.allowsMutation()) {
                throw new IllegalStateException("semantic hook activation did not commit");
            }
            validatedHooksInstalled = true;
        }
        log("semantic hooks installed; versionCode observed="
                + service.getPackageManager().getPackageInfo(service.getPackageName(), 0)
                        .getLongVersionCode(), null);
    }

    private static int resolveAssistantType(
            Object typeArg, SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        Object value = invoke(contract.assistantTypeDiscriminator(), typeArg);
        if (!(value instanceof Number)) {
            throw new IllegalStateException("assistant type discriminator returned non-number");
        }
        int type = ((Number) value).intValue();
        if (type == ASSISTANT_GAME || type == ASSISTANT_VIDEO || type == ASSISTANT_GLOBAL_DOCK) {
            return type;
        }
        return 0;
    }

    private static int resolveResource(
            Resources resources, String packageName, String type, String entry) {
        int id = resources.getIdentifier(entry, type, packageName);
        if (id == 0
                || !packageName.equals(resources.getResourcePackageName(id))
                || !type.equals(resources.getResourceTypeName(id))
                || !entry.equals(resources.getResourceEntryName(id))) {
            throw new IllegalStateException(
                    "Security Center resource unavailable: "
                            + packageName + ":" + type + "/" + entry);
        }
        return id;
    }

    private static void observeAllAppsTransitionStart(
            Object appsObject,
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        if (appsObject == null || contract == null
                || !contract.allAppsClass().isInstance(appsObject)
                || !(appsObject instanceof View)) return;
        View apps = (View) appsObject;
        View turbo = resolveTurboParent(apps, contract);
        SecurityCenterGlassCoordinator live = currentCoordinator(contract);
        if (live == null || turbo == null) return;
        live.updateAllAppsLayout(turbo, apps);
        live.onAllAppsToggleStarted(turbo);
        live.refreshTransitionFrame(turbo);
    }

    private static void notifyVendorPanelClosing(
            java.util.List<?> args,
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        try {
            View turbo = resolveTurboFromWrapperArgs(args, contract);
            SecurityCenterGlassCoordinator live = currentCoordinator(contract);
            if (live != null && turbo != null) live.onVendorPanelClosing(turbo);
        } catch (Throwable error) {
            log("vendor panel close observation failed", error);
        }
    }

    private static void notifyVendorPanelTerminalFromWrapper(
            java.util.List<?> args,
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        try {
            View turbo = resolveTurboFromWrapperArgs(args, contract);
            SecurityCenterGlassCoordinator live = currentCoordinator(contract);
            if (live != null && turbo != null) live.onVendorPanelTerminal(turbo);
        } catch (Throwable error) {
            log("synchronous vendor panel terminal observation failed", error);
        }
    }

    private static void notifyVendorPanelTerminal(
            java.util.List<?> args,
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        try {
            View turbo = null;
            if (args != null) {
                for (Object arg : args) {
                    if (!contract.turboClass().isInstance(arg) || !(arg instanceof View)) continue;
                    if (turbo != null && turbo != arg) {
                        throw new IllegalStateException("terminal cleanup exposed multiple TurboLayout args");
                    }
                    turbo = (View) arg;
                }
            }
            if (turbo == null) {
                throw new IllegalStateException("terminal cleanup missing TurboLayout arg");
            }
            SecurityCenterGlassCoordinator live = currentCoordinator(contract);
            if (live != null) live.onVendorPanelTerminal(turbo);
        } catch (Throwable error) {
            log("animated vendor panel terminal observation failed", error);
        }
    }

    private static View resolveTurboFromWrapperArgs(
            java.util.List<?> args,
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        Object wrapper = args != null && !args.isEmpty() ? args.get(0) : null;
        if (wrapper == null || !contract.wrapperClass().isInstance(wrapper)) return null;
        Object turbo = invoke(contract.wrapperTurboGetter(), wrapper);
        return turbo instanceof View && contract.turboClass().isInstance(turbo) ? (View) turbo : null;
    }

    private static SecurityCenterGlassCoordinator currentCoordinator(
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        synchronized (LOCK) {
            return validatedHooksInstalled && installedContract == contract ? coordinator : null;
        }
    }

    private static View resolveTurboParent(
            View apps, SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        if (apps == null || contract == null) return null;
        Object parent = apps.getParent();
        return contract.turboClass().isInstance(parent) ? (View) parent : null;
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            if (cause instanceof Error) throw (Error) cause;
            throw new IllegalStateException("Security Center invocation failed", cause);
        } catch (Throwable error) {
            throw new IllegalStateException("Security Center invocation failed", error);
        }
    }

    private static void showUnsupportedTipOnce(Context context) {
        if (context == null || !UNSUPPORTED_TIP.shouldEmit()) return;
        Runnable show = () -> {
            try {
                Toast.makeText(
                        context.getApplicationContext(),
                        "LiquidDock: Security Center glass is unavailable on this build",
                        Toast.LENGTH_LONG).show();
            } catch (Throwable error) {
                log("compatibility tip unavailable", error);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) show.run();
        else new Handler(Looper.getMainLooper()).post(show);
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
