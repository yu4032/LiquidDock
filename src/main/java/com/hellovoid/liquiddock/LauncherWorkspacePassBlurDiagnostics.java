package com.hellovoid.liquiddock;

import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Diagnostic-only observer for Workspace PassBlur SurfaceControl writes.
 *
 * <p>This class deliberately does not rewrite vendor arguments. It records the exact write,
 * Launcher presentation state and a filtered caller stack so the next device trace can identify
 * which HyperOS path changes the Launcher-root PassBlur contract. The active LiquidDock producer
 * is tracked only to correlate surface/update writes with one Workspace binding.</p>
 */
final class LauncherWorkspacePassBlurDiagnostics {
    private static final String TAG = "[DC][WorkspacePBTrace]";
    private static final Object LOCK = new Object();

    private static final class Claim {
        final Surface surface;
        final float liquidDockScale;
        final WeakReference<View> hostRef;
        boolean desiredUpdates = true;
        long updateWriteSerial;
        long surfaceWriteSerial;

        Claim(Surface surface, float liquidDockScale, View host) {
            this.surface = surface;
            this.liquidDockScale = liquidDockScale;
            this.hostRef = new WeakReference<>(host);
        }
    }

    private static final Map<SurfaceControl, Claim> ACTIVE_ROOTS = new WeakHashMap<>();
    private static boolean installed;

    private LauncherWorkspacePassBlurDiagnostics() {}

    static boolean install() {
        synchronized (LOCK) {
            if (installed) return true;
            try {
                Class<?> transactionClass = SurfaceControl.Transaction.class;
                Method setUpdateTextureFlag = transactionClass.getMethod(
                        "setUpdateTextureFlag", SurfaceControl.class, Boolean.TYPE, Float.TYPE);
                Method setPassBlurSurface = transactionClass.getMethod(
                        "SetPassBlurSurface", SurfaceControl.class, Surface.class);

                HookUtil.hook(setUpdateTextureFlag, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = surfaceControlArg(args, 0);
                    Claim claim = root != null ? claimFor(root) : null;
                    if (claim != null) {
                        boolean requestedEnabled = args.length > 1
                                && args[1] instanceof Boolean
                                && (Boolean) args[1];
                        float requestedScale = args.length > 2 && args[2] instanceof Float
                                ? (Float) args[2] : Float.NaN;
                        long serial;
                        synchronized (LOCK) {
                            serial = ++claim.updateWriteSerial;
                        }
                        View host = claim.hostRef.get();
                        log("update-write serial=" + serial
                                + " layerId=" + Miuix307PassBlurBridge.surfaceLayerId(root)
                                + " requestedEnabled=" + requestedEnabled
                                + " requestedScale=" + requestedScale
                                + " liquidDockDesiredEnabled=" + claim.desiredUpdates
                                + " liquidDockScale=" + claim.liquidDockScale
                                + " rootValid=" + root.isValid()
                                + " thread=" + Thread.currentThread().getName()
                                + " " + viewState(host)
                                + " " + LauncherGlassSceneController.diagnosticState(host)
                                + " caller=" + callerTrace());
                    }
                    return chain.proceed(args);
                });

                HookUtil.hook(setPassBlurSurface, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    SurfaceControl root = surfaceControlArg(args, 0);
                    Claim claim = root != null ? claimFor(root) : null;
                    if (claim != null) {
                        Surface requested = args.length > 1 && args[1] instanceof Surface
                                ? (Surface) args[1] : null;
                        long serial;
                        synchronized (LOCK) {
                            serial = ++claim.surfaceWriteSerial;
                        }
                        View host = claim.hostRef.get();
                        log("surface-write serial=" + serial
                                + " layerId=" + Miuix307PassBlurBridge.surfaceLayerId(root)
                                + " requestedNull=" + (requested == null)
                                + " matchesLiquidDockProducer=" + (requested == claim.surface)
                                + " requestedIdentity="
                                + (requested != null ? System.identityHashCode(requested) : 0)
                                + " liquidDockIdentity=" + System.identityHashCode(claim.surface)
                                + " thread=" + Thread.currentThread().getName()
                                + " " + viewState(host)
                                + " " + LauncherGlassSceneController.diagnosticState(host)
                                + " caller=" + callerTrace());
                    }
                    return chain.proceed(args);
                });

                installed = true;
                log("Workspace PassBlur diagnostics installed");
                return true;
            } catch (Throwable error) {
                log("Workspace PassBlur diagnostics unavailable: " + error);
                return false;
            }
        }
    }

    static void claim(SurfaceControl root, Surface surface, float scale, View host) {
        if (root == null || surface == null || host == null
                || !Float.isFinite(scale) || scale <= 0f) return;
        synchronized (LOCK) {
            ACTIVE_ROOTS.put(root, new Claim(surface, scale, host));
        }
        log("claim layerId=" + Miuix307PassBlurBridge.surfaceLayerId(root)
                + " producerIdentity=" + System.identityHashCode(surface)
                + " scale=" + scale
                + " " + viewState(host)
                + " " + LauncherGlassSceneController.diagnosticState(host));
    }

    static void setDesiredUpdates(SurfaceControl root, boolean enabled, String reason) {
        if (root == null) return;
        Claim current;
        synchronized (LOCK) {
            current = ACTIVE_ROOTS.get(root);
            if (current != null) current.desiredUpdates = enabled;
        }
        if (current != null) {
            View host = current.hostRef.get();
            log("desired-updates=" + enabled
                    + " reason=" + reason
                    + " layerId=" + Miuix307PassBlurBridge.surfaceLayerId(root)
                    + " " + viewState(host)
                    + " " + LauncherGlassSceneController.diagnosticState(host));
        }
    }

    static void release(SurfaceControl root, Surface surface) {
        if (root == null) return;
        Claim removed = null;
        synchronized (LOCK) {
            Claim current = ACTIVE_ROOTS.get(root);
            if (current != null && (surface == null || current.surface == surface)) {
                removed = ACTIVE_ROOTS.remove(root);
            }
        }
        if (removed != null) {
            View host = removed.hostRef.get();
            log("release layerId=" + Miuix307PassBlurBridge.surfaceLayerId(root)
                    + " rootValid=" + root.isValid()
                    + " " + viewState(host)
                    + " " + LauncherGlassSceneController.diagnosticState(host));
        }
    }

    private static Claim claimFor(SurfaceControl root) {
        synchronized (LOCK) {
            return ACTIVE_ROOTS.get(root);
        }
    }

    private static SurfaceControl surfaceControlArg(Object[] args, int index) {
        return args != null && args.length > index && args[index] instanceof SurfaceControl
                ? (SurfaceControl) args[index] : null;
    }

    private static String viewState(View host) {
        if (host == null) return "view=null";
        try {
            return "view={attached=" + host.isAttachedToWindow()
                    + ",shown=" + host.isShown()
                    + ",visibility=" + host.getVisibility()
                    + ",windowVisibility=" + host.getWindowVisibility()
                    + ",windowFocus=" + host.hasWindowFocus()
                    + ",alpha=" + host.getAlpha()
                    + ",size=" + host.getWidth() + "x" + host.getHeight()
                    + "}";
        } catch (Throwable error) {
            return "view={error=" + error.getClass().getSimpleName() + "}";
        }
    }

    private static String callerTrace() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder();
        int written = 0;
        for (StackTraceElement frame : stack) {
            if (frame == null) continue;
            String name = frame.getClassName();
            if (name == null
                    || name.equals(LauncherWorkspacePassBlurDiagnostics.class.getName())
                    || name.equals(Thread.class.getName())
                    || name.startsWith("io.github.libxposed.")
                    || name.startsWith("java.lang.reflect.")
                    || name.startsWith("jdk.internal.reflect.")) {
                continue;
            }
            if (written++ > 0) out.append(" <- ");
            out.append(name).append("#").append(frame.getMethodName())
                    .append(":").append(frame.getLineNumber());
            if (written >= 10) break;
        }
        return out.length() > 0 ? out.toString() : "unknown";
    }

    private static void log(String message) {
        try { Api101Bridge.log(TAG + " " + message); }
        catch (Throwable ignored) {}
    }
}
