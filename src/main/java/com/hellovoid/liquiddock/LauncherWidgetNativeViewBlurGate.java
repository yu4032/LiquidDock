package com.hellovoid.liquiddock;

import android.view.View;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Owns the native MiViewBlurMode output for glass-backed widgets and folder icons.
 *
 * <p>Launcher 4.50 configures material mode, blend colors, outline and radius through
 * its own BlurUtilities. LiquidDock suppresses only the final View blur output for an
 * explicitly claimed host; it never destroys the vendor configuration. Widget and folder
 * releases both use their respective Launcher material initializer.
 */
final class LauncherWidgetNativeViewBlurGate {
    private static final String TAG = "[DC][WidgetViewBlur]";
    private static final Map<View, State> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<View, FolderState> FOLDER_STATES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ThreadLocal<Boolean> OUR_SETTER = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> FOLDER_REBIND = new ThreadLocal<>();

    private static Method setViewBlurMode;
    private static Method isWidgetBlurSupported;
    private static Method supportsWidgetBackgroundBlur;
    private static Method setWidgetBlurIfSupported;
    private static Method setMaMlBlurIfSupported;
    private static Method setFolderIconBlur;
    private static Class<?> mamlViewClass;
    private static boolean installed;

    private static final class State {
        boolean claimed;
    }

    private static final class FolderState {
        boolean claimed;
        // Latest actual Launcher material request, not a snapshot of rendered properties.
        Object[] latestVendorCall;
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
                if (state != null) {
                    synchronized (state) {
                        if (state.claimed) return chain.proceed(new Object[]{0});
                    }
                }
                FolderState folder;
                synchronized (FOLDER_STATES) {
                    folder = FOLDER_STATES.get(view);
                }
                if (folder != null) {
                    synchronized (folder) {
                        if (folder.claimed) return chain.proceed(new Object[]{0});
                    }
                }
                // The OEM can replace FolderAnimController's closing plate
                // between pre-draw and composition. If this exact native
                // animation image belongs to the presented glass, claim it at
                // the setter boundary so its first frame cannot flash blur.
                if (OpenFolderGlassCoordinator.ownsNativeBlurMode(view)) {
                    claimFolder(view);
                    return chain.proceed(new Object[]{0});
                }
                // Unowned Views and Launcher lifecycle clears always pass through unchanged.
                return chain.proceed(args);
            });

            setViewBlurMode = nativeSetter;
            isWidgetBlurSupported = isSupported;
            supportsWidgetBackgroundBlur = supportsBackground;
            setWidgetBlurIfSupported = restoreWidget;
            setMaMlBlurIfSupported = restoreMaml;
            mamlViewClass = mamlView;
            installed = true;
            MainHook.log(TAG + " Launcher 4.50 widget/folder native MiViewBlurMode gate installed");
        } catch (Throwable error) {
            // Do not clear mode/radius/blend lists if the verified native boundary is unavailable.
            MainHook.log(TAG + " native view-blur ownership unavailable: " + error);
        }
    }

    /**
     * Folder materials use the exact same View setter gate as widgets, but not the
     * widget supportBackgroundBlur predicate (folder icons have no MIUIWidgetBasicInfo).
     */
    static void installFolderInitializer(Method vendorInitializer) {
        if (installed && vendorInitializer != null) setFolderIconBlur = vendorInitializer;
    }

    static boolean isRestoringFolder() {
        return Boolean.TRUE.equals(FOLDER_REBIND.get());
    }

    static boolean claimFolder(View material) {
        if (!installed || material == null) return false;
        FolderState state;
        synchronized (FOLDER_STATES) {
            state = FOLDER_STATES.get(material);
            if (state == null) {
                state = new FolderState();
                FOLDER_STATES.put(material, state);
            }
        }
        synchronized (state) {
            if (state.claimed) return true;
            state.claimed = true;
        }
        // Only mute the actual RenderNode blur output; native mode/outline/blend
        // inputs stay under Launcher control and are updated by its initializer.
        if (!setNativeMode(material, 0)) {
            synchronized (FOLDER_STATES) {
                if (FOLDER_STATES.get(material) == state) FOLDER_STATES.remove(material);
            }
            MainHook.log(TAG + " folder output claim failed material="
                    + material.getClass().getSimpleName());
            return false;
        }
        return true;
    }

    static void rememberFolderInitializer(View material, Object[] vendorArgs) {
        if (material == null || vendorArgs == null || vendorArgs.length != 11
                || vendorArgs[0] != material
                || material.getMeasuredWidth() <= 0 || material.getMeasuredHeight() <= 0) {
            return;
        }
        FolderState state;
        synchronized (FOLDER_STATES) {
            state = FOLDER_STATES.get(material);
        }
        if (state == null) return;
        synchronized (state) {
            if (state.claimed) state.latestVendorCall = vendorArgs.clone();
        }
    }

    static void releaseFolder(View material) {
        if (material == null) return;
        FolderState state;
        synchronized (FOLDER_STATES) {
            state = FOLDER_STATES.remove(material);
        }
        if (state == null) return;
        Object[] latestRequest;
        synchronized (state) {
            state.claimed = false;
            latestRequest = state.latestVendorCall;
            state.latestVendorCall = null;
        }
        // No invented blur colors or modes: restore by invoking the latest actual
        // Launcher folder material request only if this View is still live.
        if (!material.isAttachedToWindow() || latestRequest == null
                || setFolderIconBlur == null) {
            MainHook.log(TAG + " folder release handed to vendor lifecycle material="
                    + material.getClass().getSimpleName()
                    + " attached=" + material.isAttachedToWindow()
                    + " requestAvailable=" + (latestRequest != null));
            return;
        }
        FOLDER_REBIND.set(Boolean.TRUE);
        try {
            setFolderIconBlur.invoke(null, latestRequest);
            MainHook.log(TAG + " folder material restored via Launcher initializer");
        } catch (Throwable error) {
            MainHook.log(TAG + " folder material initializer failed: " + error);
        } finally {
            FOLDER_REBIND.remove();
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
