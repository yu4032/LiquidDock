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
    private static final Map<View, String> LAST_BLUR_TRACE = new WeakHashMap<>();
    private static final Map<View, Boolean> FIRST_SCROLL_SEEN = new WeakHashMap<>();
    private static boolean firstLauncherScrollCallbackSeen;
    private static boolean installed;

    private LauncherWorkspacePassBlurDiagnostics() {}

    static boolean install(ClassLoader classLoader) {
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

                installWorkspaceScrollTrace(classLoader);
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

    private static void installWorkspaceScrollTrace(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Workspace",
                    "scrollTo",
                    chain -> {
                        Object owner = chain.getThisObject();
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (!(owner instanceof View)
                                || args.length < 2
                                || !(args[0] instanceof Integer)
                                || !(args[1] instanceof Integer)) {
                            return chain.proceed(args);
                        }
                        View workspace = (View) owner;
                        int beforeX = workspace.getScrollX();
                        int beforeY = workspace.getScrollY();
                        int targetX = (Integer) args[0];
                        int targetY = (Integer) args[1];
                        boolean firstRealMotion = false;
                        if (targetX != beforeX || targetY != beforeY) {
                            synchronized (LOCK) {
                                if (!Boolean.TRUE.equals(FIRST_SCROLL_SEEN.get(workspace))) {
                                    FIRST_SCROLL_SEEN.put(workspace, Boolean.TRUE);
                                    firstRealMotion = true;
                                }
                            }
                        }
                        if (firstRealMotion) {
                            log("workspace-first-scroll BEFORE"
                                    + " from=" + beforeX + "," + beforeY
                                    + " target=" + targetX + "," + targetY
                                    + " " + viewState(workspace)
                                    + " " + LauncherGlassSceneController.diagnosticState(workspace));
                        }
                        Object result = chain.proceed(args);
                        if (firstRealMotion) {
                            log("workspace-first-scroll AFTER"
                                    + " actual=" + workspace.getScrollX() + "," + workspace.getScrollY()
                                    + " " + viewState(workspace)
                                    + " " + LauncherGlassSceneController.diagnosticState(workspace));
                        }
                        return result;
                    },
                    int.class, int.class);
        } catch (Throwable error) {
            log("Workspace.scrollTo trace unavailable: " + error);
        }

        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.Launcher",
                    "onWorkspaceScroll",
                    chain -> {
                        Object launcher = chain.getThisObject();
                        boolean first;
                        synchronized (LOCK) {
                            first = !firstLauncherScrollCallbackSeen;
                            if (first) firstLauncherScrollCallbackSeen = true;
                        }
                        if (!first) return chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Boolean beforeMoved = tryHasMoved(launcher);
                        log("launcher-first-onWorkspaceScroll BEFORE"
                                + " hasMoved=" + beforeMoved
                                + " caller=" + callerTrace());
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        Boolean afterMoved = tryHasMoved(launcher);
                        log("launcher-first-onWorkspaceScroll AFTER"
                                + " hasMoved=" + afterMoved);
                        return result;
                    });
        } catch (Throwable error) {
            log("Launcher.onWorkspaceScroll trace unavailable: " + error);
        }
    }

    private static Boolean tryHasMoved(Object launcher) {
        if (launcher == null) return null;
        try {
            Method method = launcher.getClass().getMethod("hasMoved");
            Object value = method.invoke(launcher);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void traceBlurInterception(
            String source, View receiver, Object requested, Object effective, boolean suppressed) {
        if (receiver == null || source == null) return;
        String signature = source + "|" + String.valueOf(requested)
                + "|" + String.valueOf(effective) + "|" + suppressed
                + "|" + receiver.getVisibility() + "|" + receiver.getWindowVisibility();
        synchronized (LOCK) {
            String previous = LAST_BLUR_TRACE.get(receiver);
            if (signature.equals(previous)) return;
            LAST_BLUR_TRACE.put(receiver, signature);
        }
        log("blur-write source=" + source
                + " receiver=" + receiver.getClass().getName()
                + " requested=" + requested
                + " effective=" + effective
                + " suppressed=" + suppressed
                + " " + viewState(receiver)
                + " " + LauncherGlassSceneController.diagnosticState(receiver)
                + " caller=" + callerTrace());
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
