package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Semantic, fail-closed Security Center hooks for Game / Video / Global Dock liquid glass. */
final class SecurityCenterGlassHook {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Object LOCK = new Object();
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
        Class<?> serviceClass = Class.forName(
                SecurityCenterHookSpec.BOOTSTRAP_SERVICE_CLASS, false, loader);
        Class<?> sidebarStubClass = Class.forName(
                SecurityCenterHookSpec.SIDEBAR_OVERLAY_STUB_CLASS, false, loader);
        SecurityCenterSemanticContractResolver.ResolvedContract contract =
                SecurityCenterSemanticContractResolver.resolve(turboClass, View.class, capabilities);
        SecurityCenterSemanticContractResolver.SidebarLifecycleContract sidebarLifecycle =
                SecurityCenterSemanticContractResolver.resolveSidebarLifecycle(
                        serviceClass, sidebarStubClass);
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

            HookUtil.hook(allAppsMotion.attach(), chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                if (ACTIVATION.allowsMutation()) {
                    observeAllAppsTransitionStart(
                            chain.getArgs().size() > 1 ? chain.getArgs().get(1) : null,
                            contract,
                            true);
                }
                return result;
            });
            HookUtil.hook(allAppsMotion.dismiss(), chain -> {
                if (ACTIVATION.allowsMutation()) {
                    observeAllAppsTransitionStart(
                            chain.getArgs().isEmpty() ? null : chain.getArgs().get(0),
                            contract,
                            false);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(allAppsMotion.dismissToPoint(), chain -> {
                if (ACTIVATION.allowsMutation()) {
                    observeAllAppsTransitionStart(
                            chain.getArgs().isEmpty() ? null : chain.getArgs().get(0),
                            contract,
                            false);
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            // Keep the native host authoritative for lifetime. A Security Center sidebar can reuse
            // the same TurboLayout across hide/show cycles without re-running configure(). The old
            // synthetic show/hide/terminal callbacks destroyed our session in that gap, so the
            // second pull had no binding event with which to recreate the replacement. Observe the
            // methods for compatibility only; attach/detach/root replacement now perform teardown.
            HookUtil.hook(sidebarLifecycle.show(), chain ->
                    chain.proceed(chain.getArgs().toArray(new Object[0])));
            HookUtil.hook(sidebarLifecycle.hideImmediate(), chain ->
                    chain.proceed(chain.getArgs().toArray(new Object[0])));
            HookUtil.hook(sidebarLifecycle.hideAnimated(), chain ->
                    chain.proceed(chain.getArgs().toArray(new Object[0])));

            for (Method terminalMethod : terminalCleanup.methods()) {
                HookUtil.hook(terminalMethod, chain ->
                        chain.proceed(chain.getArgs().toArray(new Object[0])));
            }

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
            SecurityCenterSemanticContractResolver.ResolvedContract contract,
            boolean targetPresent) {
        if (appsObject == null || contract == null
                || !contract.allAppsClass().isInstance(appsObject)
                || !(appsObject instanceof View)) return;
        View apps = (View) appsObject;
        View turbo = resolveTurboParent(apps, contract);
        SecurityCenterGlassCoordinator live = currentCoordinator(contract);
        if (live == null || turbo == null) return;
        live.updateAllAppsLayout(turbo, apps);
        long generation = live.onAllAppsToggleStarted(turbo);
        if (generation >= 0L) {
            live.onAllAppsToggleTargetResolved(turbo, targetPresent, generation);
        }
        live.refreshTransitionFrame(turbo);
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
