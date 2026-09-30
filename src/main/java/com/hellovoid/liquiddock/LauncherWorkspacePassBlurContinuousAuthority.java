package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Keeps LiquidDock's Workspace PassBlur update state authoritative for the lifetime of the
 * Launcher-root binding.
 *
 * <p>HyperOS/MIUIX code may write setUpdateTextureFlag() again after LiquidDock has bound its
 * Workspace producer. Miuix307PassBlurBridge caches its own desired update state, so an external
 * vendor write can otherwise leave SurfaceFlinger paused while the bridge still believes HOME is
 * live. This authority rewrites only the update flag/scale for a claimed Launcher root. It does
 * not intercept SetPassBlurSurface(), so vendor surface ownership outside LiquidDock's binding
 * lifecycle remains untouched.</p>
 */
final class LauncherWorkspacePassBlurContinuousAuthority {
    private static final String TAG = "[DC][LauncherGlass]";
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

    private LauncherWorkspacePassBlurContinuousAuthority() {}

    static boolean install() {
        synchronized (LOCK) {
            if (installed) return true;
            try {
                Method setUpdateTextureFlag = SurfaceControl.Transaction.class.getMethod(
                        "setUpdateTextureFlag", SurfaceControl.class, Boolean.TYPE, Float.TYPE);

                HookUtil.hook(setUpdateTextureFlag, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                            ? (SurfaceControl) args[0] : null;
                    Claim claim = root != null ? claimFor(root) : null;
                    if (claim != null) {
                        boolean requestedEnabled = args.length > 1
                                && args[1] instanceof Boolean
                                && (Boolean) args[1];
                        float requestedScale = args.length > 2 && args[2] instanceof Float
                                ? (Float) args[2] : Float.NaN;
                        boolean changed = requestedEnabled != claim.updatesEnabled
                                || !Float.isFinite(requestedScale)
                                || Float.compare(requestedScale, claim.scale) != 0;
                        args[1] = Boolean.valueOf(claim.updatesEnabled);
                        args[2] = Float.valueOf(claim.scale);
                        if (changed) {
                            log("preserved Workspace PassBlur update contract layerId="
                                    + Miuix307PassBlurBridge.surfaceLayerId(root)
                                    + " requestedEnabled=" + requestedEnabled
                                    + " requestedScale=" + requestedScale
                                    + " replacementEnabled=" + claim.updatesEnabled
                                    + " replacementScale=" + claim.scale);
                        }
                    }
                    return chain.proceed(args);
                });

                installed = true;
                log("Workspace continuous PassBlur update authority installed");
                return true;
            } catch (Throwable error) {
                log("Workspace continuous PassBlur update authority unavailable: " + error);
                return false;
            }
        }
    }

    static void claim(SurfaceControl root, Surface surface, float scale) {
        if (root == null || surface == null || !Float.isFinite(scale) || scale <= 0f) return;
        synchronized (LOCK) {
            ACTIVE_ROOTS.put(root, new Claim(surface, scale));
        }
        log("claimed Workspace PassBlur update authority layerId="
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
        log("released Workspace PassBlur update authority layerId="
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
