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
        boolean sawVendorMode;
        int lastVendorMode;
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
                    state.sawVendorMode = true;
                    state.lastVendorMode = ((Number) args[0]).intValue();
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
            state.sawVendorMode = false;
        }
        boolean muted = setNativeMode(host, 0);
        if (!muted) {
            synchronized (STATES) { STATES.remove(host); }
        }
        MainHook.log(TAG + " claim host=" + host.getClass().getSimpleName()
                + " muted=" + muted);
    }

    static void release(View host) {
        if (!installed || host == null) return;
        State state;
        synchronized (STATES) {
            state = STATES.remove(host);
        }
        if (state == null) return;

        boolean sawVendor;
        int vendorMode;
        synchronized (state) {
            state.claimed = false;
            sawVendor = state.sawVendorMode;
            vendorMode = state.lastVendorMode;
        }
        if (sawVendor) {
            // The most recent vendor request supersedes the initial known Launcher mode.
            boolean restored = setNativeMode(host, vendorMode);
            MainHook.log(TAG + " release latest-vendor-mode=" + vendorMode
                    + " restored=" + restored
                    + " host=" + host.getClass().getSimpleName());
            return;
        }
        // No post-claim mode write was observed. Ask the *real* Launcher setup code to recreate
        // its native material from support flags, not from an invented radius or color list.
        boolean restored = restoreViaVendorUtilities(host);
        MainHook.log(TAG + " release vendor-rebind=" + restored
                + " host=" + host.getClass().getSimpleName());
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
                // Controller restores the original MAML variable *after* this call.
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
