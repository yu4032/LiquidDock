package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Temporary, non-mutating composition diagnostic.
 *
 * <p>HyperOS Launcher drives the standard SurfaceControl background-blur fields from
 * WindowBlurUtils for Folder/Drawer/Recents transitions. AOSP CompositionEngine uses these fields
 * when deciding whether layers below a blur layer must stay in client/GPU composition. This tracer
 * records those writes and the transaction apply boundary without changing any argument or state.
 */
final class LauncherCompositionDecisionTrace {
    private static final String TAG = "[DC][COMPTRACE]";
    private static boolean attempted;
    private static boolean installed;

    private static final Map<String, Integer> LAST_RADIUS = new HashMap<>();
    private static final Map<String, Boolean> LAST_REGIONS = new HashMap<>();
    private static final WeakHashMap<Object, Integer> PENDING_RELEVANT_WRITES =
            new WeakHashMap<>();

    private LauncherCompositionDecisionTrace() {}

    static synchronized boolean install() {
        if (attempted) return installed;
        attempted = true;

        int hooked = 0;
        if (hookBackgroundBlurRadius()) hooked++;
        if (hookBlurRegions()) hooked++;
        if (hookBlurScaleRatio()) hooked++;
        if (hookApply()) hooked++;

        installed = hooked >= 2;
        MainHook.log(TAG + " installed=" + installed + " hooks=" + hooked);
        return installed;
    }

    private static boolean hookBackgroundBlurRadius() {
        try {
            Method method = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setBackgroundBlurRadius", SurfaceControl.class, int.class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (args.length >= 2
                        && args[0] instanceof SurfaceControl
                        && args[1] instanceof Number) {
                    SurfaceControl sc = (SurfaceControl) args[0];
                    if (isRelevant(sc)) {
                        int radius = ((Number) args[1]).intValue();
                        String key = surfaceKey(sc);
                        Integer previous;
                        synchronized (LauncherCompositionDecisionTrace.class) {
                            previous = LAST_RADIUS.put(key, Integer.valueOf(radius));
                            markPending(chain.getThisObject());
                        }
                        MainHook.log(TAG + " bgRadius"
                                + " txn=" + transactionId(chain.getThisObject())
                                + " layer=" + key
                                + " old=" + (previous == null ? "?" : previous)
                                + " new=" + radius
                                + " forceClient=" + (radius > 0)
                                + " caller=" + launcherCallerStack());
                    }
                }
                return result;
            });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable setBackgroundBlurRadius: " + error);
            return false;
        }
    }

    private static boolean hookBlurRegions() {
        try {
            Method method = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setBlurRegions", SurfaceControl.class, float[][].class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (args.length >= 2 && args[0] instanceof SurfaceControl) {
                    SurfaceControl sc = (SurfaceControl) args[0];
                    if (isRelevant(sc)) {
                        float[][] regions = args[1] instanceof float[][] ? (float[][]) args[1] : null;
                        boolean nonEmpty = regions != null && regions.length > 0;
                        String key = surfaceKey(sc);
                        Boolean previous;
                        synchronized (LauncherCompositionDecisionTrace.class) {
                            previous = LAST_REGIONS.put(key, Boolean.valueOf(nonEmpty));
                            markPending(chain.getThisObject());
                        }
                        MainHook.log(TAG + " blurRegions"
                                + " txn=" + transactionId(chain.getThisObject())
                                + " layer=" + key
                                + " old=" + (previous == null ? "?" : previous)
                                + " nonEmpty=" + nonEmpty
                                + " count=" + (regions == null ? -1 : regions.length)
                                + " caller=" + launcherCallerStack());
                    }
                }
                return result;
            });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable setBlurRegions: " + error);
            return false;
        }
    }

    private static boolean hookBlurScaleRatio() {
        try {
            Method method = SurfaceControl.Transaction.class.getDeclaredMethod(
                    "setBlurScaleRatio", SurfaceControl.class, float.class);
            HookUtil.hook(method, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                if (args.length >= 2
                        && args[0] instanceof SurfaceControl
                        && args[1] instanceof Number) {
                    SurfaceControl sc = (SurfaceControl) args[0];
                    if (isRelevant(sc)) {
                        synchronized (LauncherCompositionDecisionTrace.class) {
                            markPending(chain.getThisObject());
                        }
                        MainHook.log(TAG + " blurScale"
                                + " txn=" + transactionId(chain.getThisObject())
                                + " layer=" + surfaceKey(sc)
                                + " value=" + ((Number) args[1]).floatValue()
                                + " caller=" + launcherCallerStack());
                    }
                }
                return result;
            });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable setBlurScaleRatio: " + error);
            return false;
        }
    }

    private static boolean hookApply() {
        try {
            Method method = SurfaceControl.Transaction.class.getDeclaredMethod("apply");
            HookUtil.hook(method, chain -> {
                Object transaction = chain.getThisObject();
                Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                int writes;
                synchronized (LauncherCompositionDecisionTrace.class) {
                    Integer count = PENDING_RELEVANT_WRITES.remove(transaction);
                    writes = count == null ? 0 : count.intValue();
                }
                if (writes > 0) {
                    MainHook.log(TAG + " apply"
                            + " txn=" + transactionId(transaction)
                            + " relevantWrites=" + writes);
                }
                return result;
            });
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable apply: " + error);
            return false;
        }
    }

    private static void markPending(Object transaction) {
        if (transaction == null) return;
        Integer current = PENDING_RELEVANT_WRITES.get(transaction);
        PENDING_RELEVANT_WRITES.put(
                transaction, Integer.valueOf(current == null ? 1 : current.intValue() + 1));
    }

    private static String transactionId(Object transaction) {
        return transaction == null
                ? "null"
                : Integer.toHexString(System.identityHashCode(transaction));
    }

    private static boolean isRelevant(SurfaceControl sc) {
        if (sc == null) return false;
        String name = surfaceName(sc);
        return name.contains("com.miui.home")
                || name.contains("Launcher")
                || name.contains("Floating Dock")
                || name.contains("Workspace");
    }

    private static String surfaceKey(SurfaceControl sc) {
        int layerId = Miuix307PassBlurBridge.surfaceLayerId(sc);
        return surfaceName(sc) + "#" + layerId;
    }

    private static String surfaceName(SurfaceControl sc) {
        if (sc == null) return "null";
        try {
            Method method = SurfaceControl.class.getDeclaredMethod("getName");
            method.setAccessible(true);
            Object value = method.invoke(sc);
            if (value != null) return String.valueOf(value);
        } catch (Throwable ignored) {}
        return String.valueOf(sc);
    }

    private static String launcherCallerStack() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder out = new StringBuilder();
        int added = 0;
        for (StackTraceElement frame : stack) {
            if (frame == null) continue;
            String cls = frame.getClassName();
            if (cls == null) continue;
            if (cls.equals(LauncherCompositionDecisionTrace.class.getName())
                    || cls.startsWith("java.lang.Thread")
                    || cls.startsWith("java.lang.reflect.")
                    || cls.startsWith("jdk.internal.reflect.")
                    || cls.startsWith("org.lsposed.")
                    || cls.startsWith("io.github.libxposed.")) {
                continue;
            }
            if (!cls.startsWith("com.miui.home.")
                    && !cls.startsWith("com.miui.launcher.")
                    && !cls.startsWith("com.hellovoid.liquiddock.")) {
                continue;
            }
            if (added++ > 0) out.append(" <- ");
            out.append(cls)
                    .append('.')
                    .append(frame.getMethodName())
                    .append(':')
                    .append(frame.getLineNumber());
            if (added >= 8) break;
        }
        return out.length() == 0 ? "unknown" : out.toString();
    }
}
