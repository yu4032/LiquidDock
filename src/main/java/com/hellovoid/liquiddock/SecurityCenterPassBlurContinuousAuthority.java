package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps LiquidDock-owned Security Center PassBlur producers continuous for the lifetime of their
 * native root binding.
 *
 * <p>Security Center's own advanced-material pipeline temporarily switches the root PassBlur
 * producer into snapshot mode while moving between Toolbox / All Apps pages. Once that vendor
 * transaction writes updateTextureFlag=false, no new source frame arrives, so a consumer-side
 * request cannot recover the stream. This authority intercepts that write only while the same
 * root has an active caller-owned PassBlur Surface. The native host still owns creation,
 * visibility, attachment and teardown; only its snapshot output policy is replaced.</p>
 */
final class SecurityCenterPassBlurContinuousAuthority {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Object LOCK = new Object();
    private static final Map<SurfaceControl, Boolean> ACTIVE_ROOTS = new WeakHashMap<>();
    private static boolean installed;

    private SecurityCenterPassBlurContinuousAuthority() {}

    static boolean install() {
        synchronized (LOCK) {
            if (installed) return true;
            try {
                Class<?> transactionClass = SurfaceControl.Transaction.class;
                Method setPassBlurSurface = transactionClass.getMethod(
                        "SetPassBlurSurface", SurfaceControl.class, Surface.class);
                Method setUpdateTextureFlag = transactionClass.getMethod(
                        "setUpdateTextureFlag", SurfaceControl.class, Boolean.TYPE, Float.TYPE);

                HookUtil.hook(setPassBlurSurface, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                            ? (SurfaceControl) args[0] : null;
                    Surface producer = args.length > 1 && args[1] instanceof Surface
                            ? (Surface) args[1] : null;

                    // Untrack before the real unbind transaction so its following FALSE write is
                    // allowed through. Track only after a successful non-null binding call.
                    if (root != null && producer == null) untrack(root);
                    Object result = chain.proceed(args);
                    if (root != null && producer != null
                            && SecurityCenterGlassRuntimeState.isMaterialEnabled()) {
                        track(root);
                    }
                    return result;
                });

                HookUtil.hook(setUpdateTextureFlag, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                            ? (SurfaceControl) args[0] : null;
                    boolean requested = args.length > 1 && args[1] instanceof Boolean
                            && (Boolean) args[1];
                    if (!requested && root != null
                            && SecurityCenterGlassRuntimeState.isMaterialEnabled()
                            && isTracked(root)) {
                        args[1] = Boolean.TRUE;
                        log("blocked vendor PassBlur snapshot for active replacement root layerId="
                                + Miuix307PassBlurBridge.surfaceLayerId(root));
                    }
                    return chain.proceed(args);
                });

                installed = true;
                log("continuous PassBlur authority installed");
                return true;
            } catch (Throwable error) {
                log("continuous PassBlur authority unavailable: " + error);
                return false;
            }
        }
    }

    private static void track(SurfaceControl root) {
        synchronized (LOCK) {
            ACTIVE_ROOTS.put(root, Boolean.TRUE);
        }
    }

    private static void untrack(SurfaceControl root) {
        synchronized (LOCK) {
            ACTIVE_ROOTS.remove(root);
        }
    }

    private static boolean isTracked(SurfaceControl root) {
        synchronized (LOCK) {
            return ACTIVE_ROOTS.containsKey(root);
        }
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
