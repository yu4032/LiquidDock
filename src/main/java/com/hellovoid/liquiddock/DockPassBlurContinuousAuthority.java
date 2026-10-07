package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps the LiquidDock-owned Dock PassBlur producer authoritative while it is bound to the
 * Floating Dock root.
 *
 * <p>Launcher owns the same root SurfaceControl and may switch its native static-Dock snapshot /
 * live-blur pipeline during APP -> HOME. Those later vendor transactions must not replace our
 * already-parceled producer Surface or silently change its update/scale contract. LiquidDock's
 * own power policy remains authoritative through {@link #setUpdatesEnabled}: static HOME may
 * pause the producer, while HOME animation override keeps it live.</p>
 */
final class DockPassBlurContinuousAuthority {
    private static final String TAG = "[DC][DockPassBlurAuthority]";
    private static final Object LOCK = new Object();

    private static final class Claim {
        final Surface surface;
        final float scale;
        boolean updatesEnabled = true;

        Claim(Surface surface, float scale) {
            this.surface = surface;
            this.scale = scale;
        }
    }

    private static final Map<SurfaceControl, Claim> ACTIVE_ROOTS = new WeakHashMap<>();
    private static boolean installed;

    private DockPassBlurContinuousAuthority() {}

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
                    if (claim != null) {
                        Surface requested = args.length > 1 && args[1] instanceof Surface
                                ? (Surface) args[1] : null;
                        if (requested != claim.surface) {
                            log("suppressed vendor PassBlur surface rebind layerId="
                                    + Miuix307PassBlurBridge.surfaceLayerId(root)
                                    + " requested=" + (requested != null ? "surface" : "null"));
                            return successfulSuppressionResult(
                                    setPassBlurSurface, chain.getThisObject());
                        }
                    }
                    return chain.proceed(args);
                });

                HookUtil.hook(setUpdateTextureFlag, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                            ? (SurfaceControl) args[0] : null;
                    Claim claim = root != null ? claimFor(root) : null;
                    if (claim != null) {
                        boolean requestedEnabled = args.length > 1 && args[1] instanceof Boolean
                                && (Boolean) args[1];
                        float requestedScale = args.length > 2 && args[2] instanceof Float
                                ? (Float) args[2] : Float.NaN;
                        boolean replacementEnabled = claim.updatesEnabled;
                        boolean changed = requestedEnabled != replacementEnabled
                                || !Float.isFinite(requestedScale)
                                || Float.compare(requestedScale, claim.scale) != 0;
                        args[1] = Boolean.valueOf(replacementEnabled);
                        args[2] = Float.valueOf(claim.scale);
                        if (changed) {
                            log("preserved LiquidDock update contract layerId="
                                    + Miuix307PassBlurBridge.surfaceLayerId(root)
                                    + " requestedEnabled=" + requestedEnabled
                                    + " replacementEnabled=" + replacementEnabled
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

    static void setUpdatesEnabled(SurfaceControl root, boolean enabled) {
        if (root == null) return;
        synchronized (LOCK) {
            Claim current = ACTIVE_ROOTS.get(root);
            if (current != null) current.updatesEnabled = enabled;
        }
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

    private static Object successfulSuppressionResult(Method method, Object receiver) {
        Class<?> result = method.getReturnType();
        if (result == void.class) return null;
        if (receiver != null && result.isInstance(receiver)) return receiver;
        return null;
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
