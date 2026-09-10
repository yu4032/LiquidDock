package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import java.lang.reflect.Field;
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
    private static final WeakHashMap<View, SettleObserver> SETTLE_OBSERVERS = new WeakHashMap<>();
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

    /** Pure vendor-authority policy: transformation state, never elapsed time, owns completion. */
    static final class ToggleAuthority {
        private ToggleAuthority() {}
        static boolean shouldStartTransition(boolean transforming) { return !transforming; }
        static boolean shouldKeepWaiting(boolean transforming) { return transforming; }
    }

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

        // Validate hidden View material mutation APIs as part of the atomic preflight.
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

            HookUtil.hook(contract.toggleAllApps(), chain -> {
                Object turboObject = chain.getThisObject();
                if (!ACTIVATION.allowsMutation() || !(turboObject instanceof View)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View turbo = (View) turboObject;
                final boolean transformingNow;
                try {
                    transformingNow = contract.transforming().getBoolean(turbo);
                } catch (Throwable error) {
                    log("toggle authority read failed", error);
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                if (!ToggleAuthority.shouldStartTransition(transformingNow)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                SecurityCenterGlassCoordinator live = currentCoordinator(contract);
                final long transitionGeneration = live != null
                        ? live.onAllAppsToggleStarted(turbo) : -1L;
                final Object result;
                try {
                    result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                } catch (Throwable error) {
                    if (live != null) live.releaseAll();
                    throw error;
                }
                if (live != null) {
                    try {
                        Object apps = invoke(contract.appsGetter(), turbo);
                        if (apps instanceof View) live.updateAllAppsLayout(turbo, (View) apps);
                    } catch (Throwable error) {
                        live.releaseAll();
                        log("all-apps late-bind observation failed", error);
                        return result;
                    }
                }
                if (transitionGeneration >= 0L) {
                    installSettleObserver(turbo, contract, transitionGeneration);
                }
                return result;
            });

            HookUtil.hook(contract.removeAnimated(), chain -> {
                if (ACTIVATION.allowsMutation()) notifyVendorPanelClosing(chain.getArgs(), contract);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(contract.removeWithoutAnimation(), chain -> {
                if (ACTIVATION.allowsMutation()) notifyVendorPanelClosing(chain.getArgs(), contract);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

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

    private static void notifyVendorPanelClosing(
            java.util.List<?> args,
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        Object wrapper = args != null && !args.isEmpty() ? args.get(0) : null;
        if (wrapper == null) return;
        try {
            Object turbo = invoke(contract.wrapperTurboGetter(), wrapper);
            SecurityCenterGlassCoordinator live = currentCoordinator(contract);
            if (live != null && turbo instanceof View) {
                live.onVendorPanelClosing((View) turbo);
            }
        } catch (Throwable error) {
            log("vendor panel close observation failed", error);
        }
    }

    private static SecurityCenterGlassCoordinator currentCoordinator(
            SecurityCenterSemanticContractResolver.ResolvedContract contract) {
        synchronized (LOCK) {
            return validatedHooksInstalled && installedContract == contract ? coordinator : null;
        }
    }

    private static void installSettleObserver(
            View turbo,
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            long transitionGeneration) {
        if (turbo == null || transitionGeneration < 0L) return;
        ViewTreeObserver observer = turbo.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) {
            log("toggle settle observer unavailable generation=" + transitionGeneration, null);
            return;
        }
        synchronized (LOCK) {
            SettleObserver old = SETTLE_OBSERVERS.remove(turbo);
            if (old != null) old.remove();
            SettleObserver created = new SettleObserver(
                    turbo, contract, observer, transitionGeneration);
            SETTLE_OBSERVERS.put(turbo, created);
            observer.addOnPreDrawListener(created);
        }
    }

    private static final class SettleObserver implements ViewTreeObserver.OnPreDrawListener {
        private final View turbo;
        private final SecurityCenterSemanticContractResolver.ResolvedContract contract;
        private final ViewTreeObserver observer;
        private final long transitionGeneration;
        private boolean removed;

        SettleObserver(
                View turbo,
                SecurityCenterSemanticContractResolver.ResolvedContract contract,
                ViewTreeObserver observer,
                long transitionGeneration) {
            this.turbo = turbo;
            this.contract = contract;
            this.observer = observer;
            this.transitionGeneration = transitionGeneration;
        }

        @Override
        public boolean onPreDraw() {
            SecurityCenterGlassCoordinator live = currentCoordinator(contract);
            if (!ACTIVATION.allowsMutation()
                    || live == null || !SecurityCenterGlassRuntimeState.isEnabled()) {
                remove();
                return true;
            }
            final boolean transforming;
            try {
                transforming = contract.transforming().getBoolean(turbo);
            } catch (Throwable error) {
                remove();
                live.releaseAll();
                log("toggle settle authority read failed", error);
                return true;
            }
            if (ToggleAuthority.shouldKeepWaiting(transforming)) {
                live.refreshTransitionFrame(turbo);
                return true;
            }

            remove();
            try {
                boolean allAppsPresent = contract.allAppsPresent().getBoolean(turbo);
                live.onAllAppsToggleSettled(turbo, allAppsPresent, transitionGeneration);
            } catch (Throwable error) {
                live.releaseAll();
                log("toggle settle state read failed", error);
            }
            return true;
        }

        void remove() {
            if (removed) return;
            removed = true;
            synchronized (LOCK) {
                if (SETTLE_OBSERVERS.get(turbo) == this) SETTLE_OBSERVERS.remove(turbo);
            }
            try {
                if (observer.isAlive()) observer.removeOnPreDrawListener(this);
            } catch (Throwable ignored) {}
        }
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
