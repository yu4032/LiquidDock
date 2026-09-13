package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps the LiquidDock-owned Security Center PassBlur producer authoritative for the lifetime of
 * its native root binding.
 *
 * <p>Security Center may reuse the same root SurfaceControl while its own advanced-material
 * pipeline rebinds SetPassBlurSurface and changes updateTextureFlag/scale during page transitions.
 * Once LiquidDock has bound a caller-owned Surface, those later vendor transactions must not steal
 * the output target or apply the vendor Surface's geometry contract to our full-size producer.
 * Native View/window attach and teardown remain authoritative; Miuix307PassBlurBridge explicitly
 * releases this claim before its real unbind transaction.</p>
 */
final class SecurityCenterPassBlurContinuousAuthority {
    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Object LOCK = new Object();

    private static final class Claim {
        final Surface surface;
        final float scale;

        Claim(Surface surface, float scale) {
            this.surface = surface;
            this.scale = scale;
        }
    }

    private static final Map<SurfaceControl, Claim> ACTIVE_ROOTS = new WeakHashMap<>();
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
                    Claim claim = root != null ? claimFor(root) : null;
                    if (claim != null && SecurityCenterGlassRuntimeState.isMaterialEnabled()) {
                        Surface requested = args.length > 1 && args[1] instanceof Surface
                                ? (Surface) args[1] : null;
                        if (requested != claim.surface) {
                            args[1] = claim.surface;
                            log("preserved LiquidDock PassBlur surface against vendor rebind layerId="
                                    + Miuix307PassBlurBridge.surfaceLayerId(root));
                        }
                    }
                    return chain.proceed(args);
                });

                HookUtil.hook(setUpdateTextureFlag, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                            ? (SurfaceControl) args[0] : null;
                    Claim claim = root != null ? claimFor(root) : null;
                    if (claim != null && SecurityCenterGlassRuntimeState.isMaterialEnabled()) {
                        boolean requestedEnabled = args.length > 1 && args[1] instanceof Boolean
                                && (Boolean) args[1];
                        float requestedScale = args.length > 2 && args[2] instanceof Float
                                ? (Float) args[2] : Float.NaN;
                        boolean changed = !requestedEnabled
                                || !Float.isFinite(requestedScale)
                                || Float.compare(requestedScale, claim.scale) != 0;
                        args[1] = Boolean.TRUE;
                        args[2] = Float.valueOf(claim.scale);
                        if (changed) {
                            log("preserved LiquidDock PassBlur update contract layerId="
                                    + Miuix307PassBlurBridge.surfaceLayerId(root)
                                    + " requestedEnabled=" + requestedEnabled
                                    + " requestedScale=" + requestedScale
                                    + " replacementScale=" + claim.scale);
                        }
                    }
                    return chain.proceed(args);
                });

                installed = true;
                log("continuous PassBlur output authority installed");
                return true;
            } catch (Throwable error) {
                log("continuous PassBlur output authority unavailable: " + error);
                return false;
            }
        }
    }

    static void claim(SurfaceControl root, Surface surface, float scale) {
        if (root == null || surface == null || !Float.isFinite(scale) || scale <= 0f) return;
        synchronized (LOCK) {
            ACTIVE_ROOTS.put(root, new Claim(surface, scale));
        }
        log("claimed LiquidDock PassBlur output layerId="
                + Miuix307PassBlurBridge.surfaceLayerId(root) + " scale=" + scale);
    }

    static void release(SurfaceControl root, Surface surface) {
        if (root == null) return;
        synchronized (LOCK) {
            Claim current = ACTIVE_ROOTS.get(root);
            if (current != null && (surface == null || current.surface == surface)) {
                ACTIVE_ROOTS.remove(root);
            }
        }
        log("released LiquidDock PassBlur output layerId="
                + Miuix307PassBlurBridge.surfaceLayerId(root));
    }

    private static Claim claimFor(SurfaceControl root) {
        synchronized (LOCK) {
            return ACTIVE_ROOTS.get(root);
        }
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
