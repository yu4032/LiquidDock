package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** Keeps the LiquidDock-owned PassBlur producer authoritative while Searchbox glass is active. */
final class MiuiSearchboxPassBlurContinuousAuthority {
    private static final String TAG = "[DC][MiuiSearchboxGlass]";
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

    private MiuiSearchboxPassBlurContinuousAuthority() {}

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
                        if (requested != claim.surface) args[1] = claim.surface;
                    }
                    return chain.proceed(args);
                });

                HookUtil.hook(setUpdateTextureFlag, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                            ? (SurfaceControl) args[0] : null;
                    Claim claim = root != null ? claimFor(root) : null;
                    if (claim != null) {
                        args[1] = Boolean.TRUE;
                        args[2] = Float.valueOf(claim.scale);
                    }
                    return chain.proceed(args);
                });

                installed = true;
                return true;
            } catch (Throwable error) {
                log("continuous PassBlur output authority unavailable cause="
                        + failureSummary(error));
                return false;
            }
        }
    }

    static void claim(SurfaceControl root, Surface surface, float scale) {
        if (root == null || surface == null || !Float.isFinite(scale) || scale <= 0f) return;
        synchronized (LOCK) {
            ACTIVE_ROOTS.put(root, new Claim(surface, scale));
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
    }

    private static Claim claimFor(SurfaceControl root) {
        synchronized (LOCK) {
            return ACTIVE_ROOTS.get(root);
        }
    }

    private static String failureSummary(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return error.getClass().getName()
                + (message == null || message.isEmpty() ? "" : ": " + message);
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
