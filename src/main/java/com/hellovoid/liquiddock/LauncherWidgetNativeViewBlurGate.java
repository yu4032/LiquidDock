package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Owns the native MiViewBlurMode output of glass-backed Launcher widget hosts.
 *
 * <p>Launcher 4.50's BlurUtilities.setWidgetOrMaMlBlur() sets View.setMiViewBlurMode(1);
 * it does NOT use setMiSelfBlur. Leave the native blend list/radius untouched and hand those
 * values back through Launcher BlurUtilities when glass releases ownership.
 */
final class LauncherWidgetNativeViewBlurGate {
    private static final String TAG = "[DC][WidgetViewBlur]";
    private static final Map<View, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    // Keep only hosts whose native material LiquidDock actually muted. A released
    // widget can lose its restored mode/radius/blend colors on a later App -> HOME
    // transition without passing through another glass release.
    private static final Map<View, Boolean> PREVIOUSLY_OWNED_HOSTS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> OUR_SETTER = new ThreadLocal<>();

    private static Method setViewBlurMode;
    private static Method isWidgetBlurSupported;
    private static Method supportsWidgetBackgroundBlur;
    private static Method setWidgetBlurIfSupported;
    private static Method setMaMlBlurIfSupported;
    private static Class<?> mamlViewClass;
    private static boolean installed;

    private static final class State {
        boolean claimed;
    }

    private LauncherWidgetNativeViewBlurGate() {}

    static synchronized void install(ClassLoader loader) {
        if (installed || loader == null) return;
        try {
            Method nativeSetter = View.class.getMethod("setMiViewBlurMode", int.class);
            Class<?> blurUtilities = Class.forName(
                    "com.miui.home.launcher.common.BlurUtilities", false, loader);
            Class<?> mamlView = Class.forName("com.miui.maml.component.MamlView", false, loader);

            Method isSupported = blurUtilities.getMethod("isWidgetBlurSupported");
            Method supportsBackground = blurUtilities.getMethod(
                    "supportWidgetBackgroundBlur", View.class);
            Method restoreWidget = blurUtilities.getMethod("setWidgetBlurIfSupported", View.class);
            Method restoreMaml = blurUtilities.getMethod(
                    "setMaMlBlurIfSupported", mamlView, boolean.class);

            // Observe only the actual Launcher 4.50 widget material output setter.
            // Non-owned View instances (Dock/folders/other applications) pass through untouched.
            HookUtil.hook(nativeSetter, chain -> {
                Object receiver = chain.getThisObject();
                Object[] args = chain.getArgs().toArray(new Object[0]);
                if (Boolean.TRUE.equals(OUR_SETTER.get())
                        || !(receiver instanceof View)
                        || args.length != 1 || !(args[0] instanceof Number)) {
                    return chain.proceed(args);
                }
                View view = (View) receiver;
                State state;
                synchronized (STATES) {
                    state = STATES.get(view);
                }
                if (state == null) return chain.proceed(args);

                synchronized (state) {
                    if (!state.claimed) return chain.proceed(args);
                }
                // Material remains opted in on the vendor side; only suppress RenderNode output.
                return chain.proceed(new Object[]{0});
            });

            setViewBlurMode = nativeSetter;
            isWidgetBlurSupported = isSupported;
            supportsWidgetBackgroundBlur = supportsBackground;
            setWidgetBlurIfSupported = restoreWidget;
            setMaMlBlurIfSupported = restoreMaml;
            mamlViewClass = mamlView;
            installed = true;
            MainHook.log(TAG + " Launcher 4.50 native MiViewBlurMode ownership installed");
        } catch (Throwable error) {
            // Do not clear mode/radius/blend lists if the verified native boundary is unavailable.
            MainHook.log(TAG + " native view-blur ownership unavailable: " + error);
        }
    }

    static void claim(View host) {
        if (!installed || host == null) return;
        if (!isSupportedWidget(host)) return;
        State state;
        synchronized (STATES) {
            state = STATES.get(host);
            if (state == null) {
                state = new State();
                STATES.put(host, state);
            }
        }
        synchronized (state) {
            if (state.claimed) {
                // Launcher may have refreshed the material without calling the mode setter.
                // Reassert our gate on the real host, but keep vendor params intact.
                setNativeMode(host, 0);
                return;
            }
            state.claimed = true;
        }
        boolean muted = setNativeMode(host, 0);
        if (!muted) {
            synchronized (STATES) { STATES.remove(host); }
        } else {
            PREVIOUSLY_OWNED_HOSTS.put(host, Boolean.TRUE);
        }
        MainHook.log(TAG + " claim host=" + host.getClass().getSimpleName()
                + " muted=" + muted);
    }

    /**
     * Release the gate and, for an attached eligible host, have Launcher recreate the
     * complete native material (mode, blur radius and background blend colors).
     *
     * A recorded 0 from clearWidgetBlurIfSupported() can be an App / HOME detach
     * transition, not the desired steady-state mode. Replaying only that integer
     * drops the native plate, even after Launcher has cleared its other blur data.
     *
     * @return true only when Launcher has rebuilt its native material for this host.
     */
    static boolean release(View host) {
        if (!installed || host == null) return false;
        State state;
        synchronized (STATES) {
            state = STATES.remove(host);
        }
        if (state == null) return false;

        synchronized (state) {
            state.claimed = false;
        }
        // A detached View must be initialized by Launcher's own onAttachedToWindow path.
        // Do not fabricate a material or re-enable an ineligible widget here.
        if (!host.isAttachedToWindow() || !isSupportedWidget(host)) {
            MainHook.log(TAG + " release vendor-owned lifecycle host="
                    + host.getClass().getSimpleName()
                    + " attached=" + host.isAttachedToWindow());
            return false;
        }
        // Always rebind: even a positive vendor mode is insufficient after detach
        // clears the native blur radius and blend colors.
        boolean restored = restoreViaVendorUtilities(host);
        MainHook.log(TAG + " release vendor-rebind=" + restored
                + " host=" + host.getClass().getSimpleName());
        return restored;
    }

    /**
     * Launcher 4.50 may clear its native widget blur during an App transition without
     * detaching the widget and without triggering a LiquidDock glass-state change.
     * Restore only previously muted widgets after Launcher has resumed HOME.
     *
     * Glass-enabled widgets remain owned and must not regain a duplicate native plate.
     * Ineligible, detached, non-Workspace and foreign-window Views are not touched.
     */
    static void restoreReleasedHostsAfterHomeResume(View workspace) {
        if (!installed || workspace == null || !workspace.isAttachedToWindow()
                || GlassRuntimeState.isWidgetEnabled()) return;
        View workspaceRoot = workspace.getRootView();
        java.util.ArrayList<View> hosts;
        synchronized (PREVIOUSLY_OWNED_HOSTS) {
            hosts = new java.util.ArrayList<>(PREVIOUSLY_OWNED_HOSTS.keySet());
        }
        for (View host : hosts) {
            if (host == null || !host.isAttachedToWindow()
                    || host.getRootView() != workspaceRoot
                    || !LauncherGlassHierarchy.isWorkspace(host)) continue;
            // Do not overwrite an active glass claim even if the runtime switch changed
            // while a deferred HOME callback was queued.
            synchronized (STATES) {
                if (STATES.containsKey(host)) continue;
            }
            if (!isSupportedWidget(host) || GlassRuntimeState.isWidgetEnabled()) continue;
            boolean rebound = restoreViaVendorUtilities(host);
            MainHook.log(TAG + " HOME resume vendor-rebind=" + rebound
                    + " host=" + host.getClass().getSimpleName());
        }
    }

    private static boolean isSupportedWidget(View host) {
        try {
            return Boolean.TRUE.equals(isWidgetBlurSupported.invoke(null))
                    && Boolean.TRUE.equals(supportsWidgetBackgroundBlur.invoke(null, host));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean restoreViaVendorUtilities(View host) {
        try {
            if (mamlViewClass.isInstance(host)) {
                // Launcher setMaMlBlurIfSupported() also updates enable_background_blur.
                // The vendor's current enable_background_blur value is authoritative after
                // successful rebind; only a failed rebind permits owned-value restoration.
                setMaMlBlurIfSupported.invoke(null, host, true);
            } else {
                setWidgetBlurIfSupported.invoke(null, host);
            }
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " vendor material rebind failed: " + error);
            return false;
        }
    }

    private static boolean setNativeMode(View host, int mode) {
        Method setter = setViewBlurMode;
        if (setter == null) return false;
        OUR_SETTER.set(Boolean.TRUE);
        try {
            Object result = setter.invoke(host, mode);
            return !(result instanceof Boolean) || Boolean.TRUE.equals(result);
        } catch (Throwable error) {
            MainHook.log(TAG + " native mode write failed: " + error);
            return false;
        } finally {
            OUR_SETTER.remove();
        }
    }
}
