package com.hellovoid.liquiddock;

import android.os.SystemClock;
import android.view.Surface;
import android.view.SurfaceControl;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

/**
 * Observe-only trace of framework PassBlur transaction writes against the bound Dock root
 * SurfaceControl.
 *
 * <p>The Floating Dock window owns a ViewRootImpl whose passblur state machine can rewrite
 * setUpdateTextureFlag / SetPassBlurSurface on the same SurfaceControl LiquidDock binds as its
 * producer root. This trace records those writes (requested values, thread, rate-limited) without
 * altering any transaction, so the device run can confirm or reject the co-management hypothesis.
 * Output goes through MainHook.log (debug switch); arguments are always passed through unchanged.
 */
final class DockScContractTrace {
    private static final String TAG = "[DC][DockFrameSync][SC]";
    private static final long MIN_LOG_INTERVAL_MS = 250L;
    private static final Object LOCK = new Object();

    private static boolean installed;
    private static WeakReference<SurfaceControl> dockRoot = new WeakReference<>(null);
    private static WeakReference<Surface> dockSurface = new WeakReference<>(null);
    private static float expectedScale = Float.NaN;
    private static long lastFlagLogMs;
    private static long lastBindLogMs;
    private static long flagWrites;
    private static long foreignFlags;
    private static long foreignBinds;

    private DockScContractTrace() {}

    /** Called from the Dock bind path; installs the observe-only hooks on first use. */
    static void claim(SurfaceControl root, Surface surface, float scale) {
        if (root == null) return;
        synchronized (LOCK) {
            dockRoot = new WeakReference<>(root);
            dockSurface = new WeakReference<>(surface);
            expectedScale = scale;
        }
        install();
        MainHook.log(TAG + " observing dock root layerId="
                + Miuix307PassBlurBridge.surfaceLayerId(root) + " scale=" + scale);
    }

    static void release(SurfaceControl root) {
        synchronized (LOCK) {
            if (root != null && dockRoot.get() == root) {
                dockRoot = new WeakReference<>(null);
                dockSurface = new WeakReference<>(null);
                expectedScale = Float.NaN;
            }
        }
    }

    private static void install() {
        synchronized (LOCK) {
            if (installed) return;
            try {
                Class<?> transactionClass = SurfaceControl.Transaction.class;
                Method setPassBlurSurface = transactionClass.getMethod(
                        "SetPassBlurSurface", SurfaceControl.class, Surface.class);
                Method setUpdateTextureFlag = transactionClass.getMethod(
                        "setUpdateTextureFlag", SurfaceControl.class, Boolean.TYPE, Float.TYPE);

                HookUtil.hook(setPassBlurSurface, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    notePassBlurSurface(args);
                    return chain.proceed(args);
                });
                HookUtil.hook(setUpdateTextureFlag, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    noteUpdateTextureFlag(args);
                    return chain.proceed(args);
                });
                installed = true;
                MainHook.log(TAG + " dock SC write observer installed");
            } catch (Throwable error) {
                MainHook.log(TAG + " dock SC write observer unavailable: " + error);
            }
        }
    }

    private static void noteUpdateTextureFlag(Object[] args) {
        SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                ? (SurfaceControl) args[0] : null;
        if (!isDockRoot(root)) return;
        boolean enabled = args.length > 1 && args[1] instanceof Boolean && (Boolean) args[1];
        float scale = args.length > 2 && args[2] instanceof Float ? (Float) args[2] : Float.NaN;
        boolean foreign = !enabled
                || !Float.isFinite(scale) || !Float.isFinite(expectedScale)
                || Float.compare(scale, expectedScale) != 0;
        long now = SystemClock.uptimeMillis();
        synchronized (LOCK) {
            flagWrites++;
            if (!foreign) return;
            foreignFlags++;
            if (now - lastFlagLogMs < MIN_LOG_INTERVAL_MS) return;
            lastFlagLogMs = now;
        }
        MainHook.log(TAG + " flag write enabled=" + enabled
                + " scale=" + scale + " expected=" + expectedScale
                + " th=" + Thread.currentThread().getName()
                + " writes=" + flagWrites + " foreign=" + foreignFlags);
    }

    private static void notePassBlurSurface(Object[] args) {
        SurfaceControl root = args.length > 0 && args[0] instanceof SurfaceControl
                ? (SurfaceControl) args[0] : null;
        if (!isDockRoot(root)) return;
        Surface requested = args.length > 1 && args[1] instanceof Surface ? (Surface) args[1] : null;
        Surface ours = dockSurface.get();
        boolean foreign = requested != ours;
        long now = SystemClock.uptimeMillis();
        synchronized (LOCK) {
            if (!foreign) return;
            foreignBinds++;
            if (now - lastBindLogMs < MIN_LOG_INTERVAL_MS) return;
            lastBindLogMs = now;
        }
        MainHook.log(TAG + " surface rebind requested=" + describe(requested)
                + " ours=" + describe(ours)
                + " th=" + Thread.currentThread().getName()
                + " foreignBinds=" + foreignBinds);
    }

    /** Identity fast path; layer-id fallback only while debug logging is active. */
    private static boolean isDockRoot(SurfaceControl root) {
        SurfaceControl observed = dockRoot.get();
        if (root == null || observed == null) return false;
        if (root == observed) return true;
        return MainHook.debugLogging
                && Miuix307PassBlurBridge.surfaceLayerId(root)
                        == Miuix307PassBlurBridge.surfaceLayerId(observed);
    }

    private static String describe(Surface surface) {
        return surface != null ? surface.toString() : "null";
    }
}
