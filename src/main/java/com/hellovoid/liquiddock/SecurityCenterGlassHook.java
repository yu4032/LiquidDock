package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;

/** Exact version-gated Security Center hooks for Game / Video / Global Dock liquid glass. */
final class SecurityCenterGlassHook {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Object LOCK = new Object();
    private static final int ASSISTANT_GAME = 1;
    private static final int ASSISTANT_VIDEO = 3;
    private static final int ASSISTANT_GLOBAL_DOCK = 4;
    private static final WeakHashMap<View, SettleObserver> SETTLE_OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> CONFIGURED_ASSISTANTS = new WeakHashMap<>();

    private static boolean bootstrapInstalled;
    private static boolean validatedHooksInstalled;
    private static boolean unsupportedVersionLogged;
    private static ClassLoader installedLoader;
    private static LiquidDockConfig installedConfig;
    private static SecurityCenterHookSpec installedSpec;
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
                        log("bootstrap owner is not Context: "
                                + (owner != null ? owner.getClass().getName() : "null"), null);
                        return result;
                    }
                    try { installValidatedHooksOnce((Context) owner); }
                    catch (Throwable error) { log("validated hook installation failed", error); }
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

        PackageInfo info = service.getPackageManager().getPackageInfo(service.getPackageName(), 0);
        long versionCode = info.getLongVersionCode();
        SecurityCenterHookSpec spec = SecurityCenterHookSpec.forVersionCode(versionCode);
        if (spec == null) {
            synchronized (LOCK) {
                if (!unsupportedVersionLogged) {
                    unsupportedVersionLogged = true;
                    log("unsupported versionCode=" + versionCode + "; mutation hooks not installed", null);
                }
            }
            return;
        }

        // Resolve the complete decompiled compatibility contract before installing any mutation.
        Class<?> turboClass = Class.forName(spec.turboLayoutClass(), false, loader);
        Class<?> wrapperClass = Class.forName(spec.sidebarWrapperClass(), false, loader);
        Class<?> managerClass = Class.forName(spec.dockWindowManagerClass(), false, loader);
        Class<?> typeClass = Class.forName(spec.dockWindowTypeClass(), false, loader);
        Class<?> helperClass = Class.forName(spec.os4MaterialHelperClass(), false, loader);
        Class<?> gameBoxClass = Class.forName(spec.gameToolboxViewClass(), false, loader);
        Class<?> videoAdapterClass = Class.forName(spec.videoToolboxAdapterClass(), false, loader);

        Method configure = HookUtil.findMethodExact(
                turboClass, spec.configureDockMethod(),
                new Class<?>[]{wrapperClass, boolean.class, String.class, int.class, typeClass,
                        boolean.class, boolean.class, boolean.class});
        Method dockReady = HookUtil.findMethodExact(turboClass, spec.dockReadyMethod(), new Class<?>[0]);
        Method toggle = HookUtil.findMethodExact(turboClass, spec.toggleAllAppsMethod(), new Class<?>[0]);
        Method finalBackground = HookUtil.findMethodExact(
                turboClass, spec.finalBackgroundMethod(), new Class<?>[0]);
        Method typeGame = HookUtil.findMethodExact(
                typeClass, spec.gameToolboxPredicateMethod(), new Class<?>[0]);
        Method typeVideo = HookUtil.findMethodExact(
                typeClass, spec.videoToolboxPredicateMethod(), new Class<?>[0]);
        Method typeGlobalDock = HookUtil.findMethodExact(
                typeClass, spec.globalDockPredicateMethod(), new Class<?>[0]);
        Method wrapperTurboGetter = HookUtil.findMethodExact(
                wrapperClass, spec.sidebarTurboGetter(), new Class<?>[0]);
        Method removeAnimated = HookUtil.findMethodExact(
                managerClass, spec.removeTurboLayoutMethod(),
                new Class<?>[]{wrapperClass, boolean.class});
        Method removeWithoutAnimation = HookUtil.findMethodExact(
                managerClass, spec.removeTurboLayoutWithoutAnimationMethod(),
                new Class<?>[]{wrapperClass, boolean.class});
        Method dockGetter = HookUtil.findMethodExact(
                turboClass, spec.dockLayoutGetter(), new Class<?>[0]);
        Method appsGetter = HookUtil.findMethodExact(
                turboClass, spec.appsLayoutGetter(), new Class<?>[0]);
        Method boxGetter = HookUtil.findMethodExact(
                turboClass, spec.boxViewGetter(), new Class<?>[0]);
        Method gameMaterialGetter = HookUtil.findMethodExact(
                gameBoxClass, spec.gameToolboxMaterialGetter(), new Class<?>[0]);
        Class<?> gameMaterialViewClass = gameMaterialGetter.getReturnType();
        Method gameMaterialRestore = HookUtil.findMethodExact(
                gameMaterialViewClass,
                spec.gameToolboxMaterialRestoreMethod(), new Class<?>[0]);
        Method videoAdapterGetter = HookUtil.findMethodExact(
                turboClass, spec.videoToolboxAdapterGetter(), new Class<?>[0]);
        Method videoMaterialRestore = HookUtil.findMethodExact(
                videoAdapterClass,
                spec.videoToolboxMaterialRestoreMethod(), new Class<?>[0]);
        Method reset = HookUtil.findMethodExact(
                helperClass, spec.os4MaterialResetMethod(), new Class<?>[]{View.class});
        Field transforming = HookUtil.findField(turboClass, spec.transformingField());
        Field allAppsPresent = HookUtil.findField(turboClass, spec.allAppsPresentField());

        if (typeGame.getReturnType() != boolean.class
                || typeVideo.getReturnType() != boolean.class
                || typeGlobalDock.getReturnType() != boolean.class
                || transforming.getType() != boolean.class
                || allAppsPresent.getType() != boolean.class
                || configure.getReturnType() != void.class
                || dockReady.getReturnType() != void.class
                || removeAnimated.getReturnType() != void.class
                || removeWithoutAnimation.getReturnType() != void.class
                || finalBackground.getReturnType() != void.class
                || !View.class.isAssignableFrom(dockGetter.getReturnType())
                || !View.class.isAssignableFrom(appsGetter.getReturnType())
                || !View.class.isAssignableFrom(boxGetter.getReturnType())
                || !View.class.isAssignableFrom(gameMaterialViewClass)
                || gameMaterialRestore.getReturnType() != void.class
                || !videoAdapterClass.isAssignableFrom(videoAdapterGetter.getReturnType())
                || videoMaterialRestore.getReturnType() != void.class
                || !turboClass.isAssignableFrom(wrapperTurboGetter.getReturnType())
                || !Modifier.isStatic(reset.getModifiers())) {
            throw new IllegalStateException("validated Security Center member shape changed");
        }

        // Resolve exact vendor resources from the live Security Center table, never local guesses.
        final Resources resources = service.getResources();
        final String resourcePackage = service.getPackageName();
        final int allAppsCornerRadiusResId = resolveResource(
                resources, resourcePackage, "dimen", spec.allAppsCornerRadiusResource());
        final int gameToolboxCornerRadiusResId = resolveResource(
                resources, resourcePackage, "dimen", spec.gameToolboxCornerRadiusResource());
        final int videoMainContentResId = resolveResource(
                resources, resourcePackage, "id", spec.videoToolboxMaterialViewIdResource());

        SecurityCenterVendorMaterialBridge vendorBridge =
                new SecurityCenterVendorMaterialBridge(loader, spec, videoMainContentResId);
        SecurityCenterGlassCoordinator nextCoordinator =
                new SecurityCenterGlassCoordinator(
                        config.glass,
                        vendorBridge,
                        allAppsCornerRadiusResId,
                        gameToolboxCornerRadiusResId);

        synchronized (LOCK) {
            if (validatedHooksInstalled) {
                nextCoordinator.releaseAll();
                return;
            }
            installedSpec = spec;
            coordinator = nextCoordinator;

            HookUtil.hook(configure, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object turboObject = chain.getThisObject();
                Object typeArg = chain.getArgs().size() > 4 ? chain.getArgs().get(4) : null;
                if (!(turboObject instanceof View) || typeArg == null) return result;
                View turbo = (View) turboObject;
                try {
                    int assistantType = resolveAssistantType(typeArg, spec);
                    synchronized (LOCK) {
                        if (assistantType != 0) CONFIGURED_ASSISTANTS.put(turbo, assistantType);
                        else CONFIGURED_ASSISTANTS.remove(turbo);
                    }
                    if (assistantType != 0) {
                        log("assistant configured type=" + assistantType, null);
                    }
                } catch (Throwable error) {
                    synchronized (LOCK) { CONFIGURED_ASSISTANTS.remove(turbo); }
                    log("assistant configure observation failed", error);
                }
                return result;
            });

            HookUtil.hook(dockReady, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object turboObject = chain.getThisObject();
                if (!(turboObject instanceof View)) return result;
                View turbo = (View) turboObject;
                final Integer assistantType;
                synchronized (LOCK) { assistantType = CONFIGURED_ASSISTANTS.get(turbo); }
                if (assistantType == null) return result;

                SecurityCenterGlassCoordinator live = currentCoordinator(spec);
                try {
                    Object dockObject = HookUtil.requireInvoke(turbo, spec.dockLayoutGetter());
                    if (!(dockObject instanceof View)) {
                        throw new IllegalStateException("dock-ready getter returned non-View value");
                    }
                    View boxMaterialView = null;
                    if (assistantType == ASSISTANT_GAME || assistantType == ASSISTANT_VIDEO) {
                        Object boxObject = HookUtil.requireInvoke(turbo, spec.boxViewGetter());
                        if (!(boxObject instanceof View)) {
                            throw new IllegalStateException("box getter returned non-View value");
                        }
                        View box = (View) boxObject;
                        if (assistantType == ASSISTANT_GAME) {
                            if (!gameBoxClass.isInstance(box)) {
                                throw new IllegalStateException(
                                        "game box class changed: " + box.getClass().getName());
                            }
                            Object material = HookUtil.requireInvoke(
                                    box, spec.gameToolboxMaterialGetter());
                            if (!(material instanceof View)
                                    || !gameMaterialViewClass.isInstance(material)) {
                                throw new IllegalStateException("game material carrier unavailable");
                            }
                            boxMaterialView = (View) material;
                        } else {
                            View material = box.findViewById(videoMainContentResId);
                            if (material == null || material.getId() != videoMainContentResId) {
                                throw new IllegalStateException("video main_content carrier unavailable");
                            }
                            boxMaterialView = material;
                        }
                    }
                    if (live != null) {
                        live.bindAssistant(turbo, (View) dockObject, boxMaterialView, assistantType);
                        log("assistant bound type=" + assistantType
                                + " boxMaterial="
                                + (boxMaterialView != null
                                        ? boxMaterialView.getClass().getName() : "none"), null);
                    }
                } catch (Throwable error) {
                    if (live != null) live.releaseAll();
                    log("assistant dock-ready observation failed type=" + assistantType, error);
                }
                return result;
            });

            HookUtil.hook(toggle, chain -> {
                Object turboObject = chain.getThisObject();
                if (!(turboObject instanceof View)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                View turbo = (View) turboObject;
                final boolean transformingNow;
                try { transformingNow = HookUtil.getBooleanField(turbo, spec.transformingField()); }
                catch (Throwable error) {
                    log("toggle authority read failed", error);
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                if (!ToggleAuthority.shouldStartTransition(transformingNow)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                SecurityCenterGlassCoordinator live = currentCoordinator(spec);
                final long transitionGeneration = live != null
                        ? live.onAllAppsToggleStarted(turbo) : -1L;
                final Object result;
                try { result = chain.proceed(chain.getArgs().toArray(new Object[0])); }
                catch (Throwable error) {
                    if (live != null) live.releaseAll();
                    throw error;
                }
                if (live != null) {
                    try {
                        Object apps = HookUtil.requireInvoke(turbo, spec.appsLayoutGetter());
                        if (apps instanceof View) live.updateAllAppsLayout(turbo, (View) apps);
                    } catch (Throwable error) {
                        live.releaseAll();
                        log("all-apps late-bind observation failed", error);
                        return result;
                    }
                }
                if (transitionGeneration >= 0L) {
                    installSettleObserver(turbo, spec, transitionGeneration);
                }
                return result;
            });

            // d2/f2 are the decompiled semantic teardown authorities. Revoke before vendor
            // TurboLayout teardown or PassBlur destruction can expose stale custom composition.
            HookUtil.hook(removeAnimated, chain -> {
                notifyVendorPanelClosing(chain.getArgs(), spec);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });
            HookUtil.hook(removeWithoutAnimation, chain -> {
                notifyVendorPanelClosing(chain.getArgs(), spec);
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            HookUtil.hook(finalBackground, chain -> {
                Object turbo = chain.getThisObject();
                SecurityCenterGlassCoordinator live = currentCoordinator(spec);
                if (live != null && live.shouldSuppressVendorFinalBackground(turbo)) return null;
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            validatedHooksInstalled = true;
        }
        log("validated hooks installed versionCode=" + versionCode
                + " allAppsRadiusResId=" + allAppsCornerRadiusResId
                + " gameRadiusResId=" + gameToolboxCornerRadiusResId
                + " videoMainContentResId=" + videoMainContentResId, null);
    }

    private static int resolveAssistantType(Object typeArg, SecurityCenterHookSpec spec) {
        boolean game = Boolean.TRUE.equals(
                HookUtil.requireInvoke(typeArg, spec.gameToolboxPredicateMethod()));
        boolean video = Boolean.TRUE.equals(
                HookUtil.requireInvoke(typeArg, spec.videoToolboxPredicateMethod()));
        boolean globalDock = Boolean.TRUE.equals(
                HookUtil.requireInvoke(typeArg, spec.globalDockPredicateMethod()));
        int count = (game ? 1 : 0) + (video ? 1 : 0) + (globalDock ? 1 : 0);
        if (count > 1) throw new IllegalStateException("assistant type predicates overlap");
        if (game) return ASSISTANT_GAME;
        if (video) return ASSISTANT_VIDEO;
        if (globalDock) return ASSISTANT_GLOBAL_DOCK;
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
                    "validated Security Center resource unavailable: "
                            + packageName + ":" + type + "/" + entry);
        }
        return id;
    }

    private static void notifyVendorPanelClosing(
            java.util.List<?> args, SecurityCenterHookSpec spec) {
        Object wrapper = args != null && !args.isEmpty() ? args.get(0) : null;
        if (wrapper == null) return;
        try {
            Object turbo = HookUtil.requireInvoke(wrapper, spec.sidebarTurboGetter());
            SecurityCenterGlassCoordinator live = currentCoordinator(spec);
            if (live != null && turbo instanceof View) {
                live.onVendorPanelClosing((View) turbo);
                log("vendor panel close observed", null);
            }
        } catch (Throwable error) {
            log("vendor panel close observation failed", error);
        }
    }

    private static SecurityCenterGlassCoordinator currentCoordinator(SecurityCenterHookSpec spec) {
        synchronized (LOCK) {
            return validatedHooksInstalled && installedSpec == spec ? coordinator : null;
        }
    }

    private static void installSettleObserver(
            View turbo, SecurityCenterHookSpec spec, long transitionGeneration) {
        if (turbo == null || transitionGeneration < 0L) return;
        ViewTreeObserver observer = turbo.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) {
            log("toggle settle observer unavailable generation=" + transitionGeneration, null);
            return;
        }
        synchronized (LOCK) {
            SettleObserver old = SETTLE_OBSERVERS.remove(turbo);
            if (old != null) old.remove();
            SettleObserver created =
                    new SettleObserver(turbo, spec, observer, transitionGeneration);
            SETTLE_OBSERVERS.put(turbo, created);
            observer.addOnPreDrawListener(created);
        }
    }

    private static final class SettleObserver implements ViewTreeObserver.OnPreDrawListener {
        private final View turbo;
        private final SecurityCenterHookSpec spec;
        private final ViewTreeObserver observer;
        private final long transitionGeneration;
        private boolean removed;

        SettleObserver(
                View turbo,
                SecurityCenterHookSpec spec,
                ViewTreeObserver observer,
                long transitionGeneration) {
            this.turbo = turbo;
            this.spec = spec;
            this.observer = observer;
            this.transitionGeneration = transitionGeneration;
        }

        @Override
        public boolean onPreDraw() {
            SecurityCenterGlassCoordinator live = currentCoordinator(spec);
            if (live == null || !SecurityCenterGlassRuntimeState.isEnabled()) {
                remove();
                return true;
            }
            final boolean transforming;
            try { transforming = HookUtil.getBooleanField(turbo, spec.transformingField()); }
            catch (Throwable error) {
                remove();
                live.releaseAll();
                log("toggle settle authority read failed", error);
                return true;
            }
            if (ToggleAuthority.shouldKeepWaiting(transforming)) {
                // Raw DEX field s is the vendor animation authority. Sample the actual Folme
                // transformed Views on each pre-draw; no fixed-delay interpolation is invented.
                live.refreshTransitionFrame(turbo);
                return true;
            }

            remove();
            try {
                boolean allAppsPresent = HookUtil.getBooleanField(turbo, spec.allAppsPresentField());
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

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
