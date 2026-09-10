package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.view.View;
import android.view.ViewTreeObserver;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.WeakHashMap;

/** Exact version-gated Security Center hooks for Global Dock / All Apps liquid glass. */
final class SecurityCenterGlassHook {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Object LOCK = new Object();
    private static final WeakHashMap<View, SettleObserver> SETTLE_OBSERVERS = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> TYPE4_CONFIGURED = new WeakHashMap<>();

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

        static boolean shouldStartTransition(boolean transforming) {
            return !transforming;
        }

        static boolean shouldKeepWaiting(boolean transforming) {
            return transforming;
        }
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
                Method onCreate = HookUtil.findMethodExact(
                        serviceClass, "onCreate", new Class<?>[0]);
                HookUtil.hook(onCreate, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object owner = chain.getThisObject();
                    if (!(owner instanceof Context)) {
                        log("bootstrap owner is not Context: "
                                + (owner != null ? owner.getClass().getName() : "null"), null);
                        return result;
                    }
                    try {
                        installValidatedHooksOnce((Context) owner);
                    } catch (Throwable error) {
                        log("validated hook installation failed", error);
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

        // Pre-resolve the complete compatibility contract before installing any mutation hook.
        Class<?> turboClass = Class.forName(spec.turboLayoutClass(), false, loader);
        Class<?> wrapperClass = Class.forName(spec.sidebarWrapperClass(), false, loader);
        Class<?> typeClass = Class.forName(spec.dockWindowTypeClass(), false, loader);
        Class<?> helperClass = Class.forName(spec.os4MaterialHelperClass(), false, loader);
        Method configure = HookUtil.findMethodExact(
                turboClass,
                spec.configureDockMethod(),
                new Class<?>[]{
                        wrapperClass,
                        boolean.class,
                        String.class,
                        int.class,
                        typeClass,
                        boolean.class,
                        boolean.class,
                        boolean.class
                });
        Method dockReady = HookUtil.findMethodExact(
                turboClass, spec.dockReadyMethod(), new Class<?>[0]);
        Method toggle = HookUtil.findMethodExact(
                turboClass, spec.toggleAllAppsMethod(), new Class<?>[0]);
        Method finalBackground = HookUtil.findMethodExact(
                turboClass, spec.finalBackgroundMethod(), new Class<?>[0]);
        Method type4 = HookUtil.findMethodExact(
                typeClass, spec.type4PredicateMethod(), new Class<?>[0]);
        HookUtil.findMethodExact(turboClass, spec.dockLayoutGetter(), new Class<?>[0]);
        HookUtil.findMethodExact(turboClass, spec.appsLayoutGetter(), new Class<?>[0]);
        Method reset = HookUtil.findMethodExact(
                helperClass, spec.os4MaterialResetMethod(), new Class<?>[]{View.class});
        Field transforming = HookUtil.findField(turboClass, spec.transformingField());
        Field allAppsPresent = HookUtil.findField(turboClass, spec.allAppsPresentField());
        if (type4.getReturnType() != boolean.class
                || transforming.getType() != boolean.class
                || allAppsPresent.getType() != boolean.class
                || configure.getReturnType() != void.class
                || dockReady.getReturnType() != void.class
                || finalBackground.getReturnType() != void.class
                || !Modifier.isStatic(reset.getModifiers())) {
            throw new IllegalStateException("validated Security Center member shape changed");
        }

        SecurityCenterVendorMaterialBridge vendorBridge =
                new SecurityCenterVendorMaterialBridge(loader, spec);
        SecurityCenterGlassCoordinator nextCoordinator =
                new SecurityCenterGlassCoordinator(config.glass, vendorBridge);

        synchronized (LOCK) {
            if (validatedHooksInstalled) {
                nextCoordinator.releaseAll();
                return;
            }
            installedSpec = spec;
            coordinator = nextCoordinator;

            // 40011320 visible Global Dock path is V(..., type, ..., false) -> c0().
            // Record exact type authority at V(), then bind only after c0() creates the dock view.
            HookUtil.hook(configure, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object turboObject = chain.getThisObject();
                Object typeArg = chain.getArgs().size() > 4 ? chain.getArgs().get(4) : null;
                if (!(turboObject instanceof View) || typeArg == null) return result;
                View turbo = (View) turboObject;
                try {
                    boolean isType4 = Boolean.TRUE.equals(
                            HookUtil.requireInvoke(typeArg, spec.type4PredicateMethod()));
                    synchronized (LOCK) {
                        if (isType4) TYPE4_CONFIGURED.put(turbo, Boolean.TRUE);
                        else TYPE4_CONFIGURED.remove(turbo);
                    }
                    if (isType4) log("type-4 dock configured", null);
                } catch (Throwable error) {
                    synchronized (LOCK) {
                        TYPE4_CONFIGURED.remove(turbo);
                    }
                    log("type-4 configure observation failed", error);
                }
                return result;
            });

            HookUtil.hook(dockReady, chain -> {
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                Object turboObject = chain.getThisObject();
                if (!(turboObject instanceof View)) return result;
                View turbo = (View) turboObject;
                final boolean type4Configured;
                synchronized (LOCK) {
                    type4Configured = Boolean.TRUE.equals(TYPE4_CONFIGURED.get(turbo));
                }
                if (!type4Configured) return result;
                try {
                    Object dock = HookUtil.requireInvoke(turbo, spec.dockLayoutGetter());
                    if (!(dock instanceof View)) {
                        log("type-4 dock-ready getter returned non-View value", null);
                        return result;
                    }
                    SecurityCenterGlassCoordinator live = currentCoordinator(spec);
                    if (live != null) {
                        live.bindGlobalDock(turbo, (View) dock);
                        log("type-4 dock bound", null);
                    }
                } catch (Throwable error) {
                    log("type-4 dock-ready observation failed", error);
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
                try {
                    transformingNow = HookUtil.getBooleanField(turbo, spec.transformingField());
                } catch (Throwable error) {
                    log("toggle authority read failed", error);
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }
                if (!ToggleAuthority.shouldStartTransition(transformingNow)) {
                    return chain.proceed(chain.getArgs().toArray(new Object[0]));
                }

                SecurityCenterGlassCoordinator live = currentCoordinator(spec);
                if (live != null) live.onAllAppsToggleStarted(turbo);
                final Object result;
                try {
                    result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                } catch (Throwable error) {
                    if (live != null) live.releaseAll();
                    throw error;
                }
                if (live != null) {
                    try {
                        Object apps = HookUtil.requireInvoke(turbo, spec.appsLayoutGetter());
                        if (apps instanceof View) {
                            live.updateAllAppsLayout(turbo, (View) apps);
                        }
                    } catch (Throwable error) {
                        live.releaseAll();
                        log("all-apps late-bind observation failed", error);
                        return result;
                    }
                }
                installSettleObserver(turbo, spec);
                return result;
            });

            HookUtil.hook(finalBackground, chain -> {
                Object turbo = chain.getThisObject();
                SecurityCenterGlassCoordinator live = currentCoordinator(spec);
                if (live != null && live.shouldSuppressVendorFinalBackground(turbo)) {
                    return null;
                }
                return chain.proceed(chain.getArgs().toArray(new Object[0]));
            });

            validatedHooksInstalled = true;
        }
        log("validated hooks installed versionCode=" + versionCode, null);
    }

    private static SecurityCenterGlassCoordinator currentCoordinator(SecurityCenterHookSpec spec) {
        synchronized (LOCK) {
            return validatedHooksInstalled && installedSpec == spec ? coordinator : null;
        }
    }

    private static void installSettleObserver(View turbo, SecurityCenterHookSpec spec) {
        if (turbo == null) return;
        ViewTreeObserver observer = turbo.getViewTreeObserver();
        if (observer == null || !observer.isAlive()) {
            log("toggle settle observer unavailable", null);
            return;
        }
        synchronized (LOCK) {
            SettleObserver old = SETTLE_OBSERVERS.remove(turbo);
            if (old != null) old.remove();
            SettleObserver created = new SettleObserver(turbo, spec, observer);
            SETTLE_OBSERVERS.put(turbo, created);
            observer.addOnPreDrawListener(created);
        }
    }

    private static final class SettleObserver implements ViewTreeObserver.OnPreDrawListener {
        private final View turbo;
        private final SecurityCenterHookSpec spec;
        private final ViewTreeObserver observer;
        private boolean removed;

        SettleObserver(View turbo, SecurityCenterHookSpec spec, ViewTreeObserver observer) {
            this.turbo = turbo;
            this.spec = spec;
            this.observer = observer;
        }

        @Override
        public boolean onPreDraw() {
            SecurityCenterGlassCoordinator live = currentCoordinator(spec);
            if (live == null || !SecurityCenterGlassRuntimeState.isEnabled()) {
                remove();
                return true;
            }
            final boolean transforming;
            try {
                transforming = HookUtil.getBooleanField(turbo, spec.transformingField());
            } catch (Throwable error) {
                remove();
                live.releaseAll();
                log("toggle settle authority read failed", error);
                return true;
            }
            if (ToggleAuthority.shouldKeepWaiting(transforming)) return true;

            remove();
            try {
                boolean allAppsPresent = HookUtil.getBooleanField(turbo, spec.allAppsPresentField());
                live.onAllAppsToggleSettled(turbo, allAppsPresent);
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
