package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.SurfaceControl;
import android.view.View;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Read-only transaction trace for the outer HyperOS caption-menu surface.
 *
 * <p>The menu ViewRoot is Windowless and device evidence shows its View hierarchy remains at an
 * identity transform while the visible backdrop still scales. This probe observes only framework
 * SurfaceControl.Transaction calls whose target label contains the source-verified
 * "Caption Menu of Task" surface name. It never changes arguments, creates transactions, or
 * applies transactions.</p>
 */
final class SystemUiHandleMenuSurfaceProbe {
    private static final String TAG = "[DC][SystemUiHandleMenuSurface]";
    private static final String CAPTION_MENU_SURFACE = "Caption Menu";
    interface ScaleListener {
        void onScale(float scaleX, float scaleY);
    }

    interface AlphaListener {
        void onAlpha(float alpha);
    }

    private static final Set<SurfaceControl> TRACKED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Map<Integer, ScaleListener> SCALE_LISTENERS =
            Collections.synchronizedMap(new HashMap<>());
    private static final Map<Integer, AlphaListener> ALPHA_LISTENERS =
            Collections.synchronizedMap(new HashMap<>());

    private static boolean installed;

    private SystemUiHandleMenuSurfaceProbe() {}

    static SurfaceControl trackController(Object controller) {
        if (controller == null) return null;
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(controller, "getWindowSurface");
        Object value = result.succeeded() ? result.value() : null;
        if (!(value instanceof SurfaceControl)) {
            log("trackController unavailable controller=" + controller.getClass().getName()
                    + " failure=" + result.failure());
            return null;
        }
        SurfaceControl surface = (SurfaceControl) value;
        if (!surface.isValid()) {
            log("trackController invalid surface=" + surface);
            return null;
        }
        TRACKED.add(surface);
        log("trackController surface=" + surface);
        return surface;
    }

    static void registerScaleListener(SurfaceControl surface, ScaleListener listener) {
        if (surface == null || listener == null) return;
        TRACKED.add(surface);
        int layerId = stableLayerId(surface);
        if (layerId < 0) {
            log("registerScaleListener unavailable layer surface=" + surface);
            return;
        }
        SCALE_LISTENERS.put(layerId, listener);
        log("registerScaleListener layer=" + layerId + " surface=" + surface);
    }

    static void unregisterScaleListener(SurfaceControl surface, ScaleListener listener) {
        if (surface == null || listener == null) return;
        int layerId = stableLayerId(surface);
        if (layerId < 0) return;
        synchronized (SCALE_LISTENERS) {
            if (SCALE_LISTENERS.get(layerId) == listener) {
                SCALE_LISTENERS.remove(layerId);
            }
        }
    }

    static void registerAlphaListener(SurfaceControl surface, AlphaListener listener) {
        if (surface == null || listener == null) return;
        TRACKED.add(surface);
        int layerId = stableLayerId(surface);
        if (layerId < 0) {
            log("registerAlphaListener unavailable layer surface=" + surface);
            return;
        }
        ALPHA_LISTENERS.put(layerId, listener);
        log("registerAlphaListener layer=" + layerId + " surface=" + surface);
    }

    static void unregisterAlphaListener(SurfaceControl surface, AlphaListener listener) {
        if (surface == null || listener == null) return;
        int layerId = stableLayerId(surface);
        if (layerId < 0) return;
        synchronized (ALPHA_LISTENERS) {
            if (ALPHA_LISTENERS.get(layerId) == listener) {
                ALPHA_LISTENERS.remove(layerId);
            }
        }
    }

    static void trackRoot(View root) {
        if (root == null) return;
        RootPassBlurEndpointBridge.Endpoint endpoint = RootPassBlurEndpointBridge.inspect(root);
        if (endpoint == null || endpoint.rootSurface == null || !endpoint.rootSurface.isValid()) {
            log("trackRoot unavailable view=" + root.getClass().getName());
            return;
        }
        TRACKED.add(endpoint.rootSurface);
        log("trackRoot layer=" + endpoint.rootLayerId
                + " seq=" + endpoint.surfaceSequenceId
                + " vri=" + endpoint.viewRootIdentity
                + " surface=" + endpoint.rootSurface);
    }

    static synchronized void install() {
        if (installed) return;
        int hooks = 0;
        hooks += hook("setMatrix",
                new Class<?>[]{SurfaceControl.class, float.class, float.class,
                        float.class, float.class},
                "matrix");
        hooks += hook("setMatrix",
                new Class<?>[]{SurfaceControl.class, Matrix.class, float[].class},
                "matrixObject");
        hooks += hook("setScale",
                new Class<?>[]{SurfaceControl.class, float.class, float.class},
                "scale");
        hooks += hook("setPosition",
                new Class<?>[]{SurfaceControl.class, float.class, float.class},
                "position");
        hooks += hook("setAlpha",
                new Class<?>[]{SurfaceControl.class, float.class},
                "alpha");
        hooks += hook("setCrop",
                new Class<?>[]{SurfaceControl.class, Rect.class},
                "crop");
        hooks += hook("setWindowCrop",
                new Class<?>[]{SurfaceControl.class, int.class, int.class},
                "windowCrop");
        hooks += hook("setGeometry",
                new Class<?>[]{SurfaceControl.class, Rect.class, Rect.class, int.class},
                "geometry");
        hooks += hook("show", new Class<?>[]{SurfaceControl.class}, "show");
        hooks += hook("hide", new Class<?>[]{SurfaceControl.class}, "hide");
        hooks += hook("reparent",
                new Class<?>[]{SurfaceControl.class, SurfaceControl.class},
                "reparent");
        installed = hooks > 0;
        log("installed hooks=" + hooks);
    }

    private static int hook(String name, Class<?>[] params, String op) {
        try {
            HookUtil.hookMethod(
                    SurfaceControl.Transaction.class,
                    name,
                    params,
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length > 0 && args[0] instanceof SurfaceControl) {
                            SurfaceControl target = (SurfaceControl) args[0];
                            boolean relevant = isTracked(target);
                            if (relevant && "reparent".equals(op)
                                    && args.length >= 2 && args[1] instanceof SurfaceControl) {
                                TRACKED.add((SurfaceControl) args[1]);
                            }
                            if (relevant) {
                                logCall(op, args);
                                if ("matrixObject".equals(op) && args.length >= 2
                                        && args[1] instanceof Matrix) {
                                    dispatchScale(target, (Matrix) args[1]);
                                } else if ("alpha".equals(op) && args.length >= 2
                                        && args[1] instanceof Number) {
                                    dispatchAlpha(target, ((Number) args[1]).floatValue());
                                }
                            }
                        }
                        return chain.proceed(args);
                    });
            return 1;
        } catch (Throwable error) {
            log("hook unavailable op=" + op + " error=" + error);
            return 0;
        }
    }

    private static void dispatchScale(SurfaceControl surface, Matrix matrix) {
        if (surface == null || matrix == null) return;
        int layerId = stableLayerId(surface);
        float[] values = new float[9];
        matrix.getValues(values);
        float scaleX = (float) Math.hypot(
                values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y]);
        float scaleY = (float) Math.hypot(
                values[Matrix.MSCALE_Y], values[Matrix.MSKEW_X]);
        ScaleListener listener = layerId >= 0 ? SCALE_LISTENERS.get(layerId) : null;
        if (listener == null) {
            if (scaleX >= 0.9999f && scaleY >= 0.9999f) {
                log("settled matrix has no listener layer=" + layerId + " surface=" + surface);
            }
            return;
        }
        if (scaleX >= 0.9999f && scaleY >= 0.9999f) {
            log("dispatch settled scale layer=" + layerId
                    + " scale=" + scaleX + "," + scaleY);
        }
        listener.onScale(scaleX, scaleY);
    }

    private static void dispatchAlpha(SurfaceControl surface, float alpha) {
        if (surface == null) return;
        int layerId = stableLayerId(surface);
        AlphaListener listener = layerId >= 0 ? ALPHA_LISTENERS.get(layerId) : null;
        if (listener != null) listener.onAlpha(Math.max(0f, Math.min(1f, alpha)));
    }

    private static int stableLayerId(SurfaceControl surface) {
        if (surface == null) return -1;
        try {
            String label = String.valueOf(surface);
            int hash = label.lastIndexOf('#');
            int end = hash >= 0 ? label.indexOf(')', hash) : -1;
            if (hash >= 0 && end > hash + 1) {
                return Integer.parseInt(label.substring(hash + 1, end));
            }
        } catch (Throwable ignored) {}
        return Miuix307PassBlurBridge.surfaceLayerId(surface);
    }

    private static boolean isTracked(SurfaceControl surface) {
        if (surface == null) return false;
        if (TRACKED.contains(surface)) return true;
        try {
            String label = String.valueOf(surface);
            boolean caption = label.contains(CAPTION_MENU_SURFACE) || label.contains("captionMenu");
            if (caption) TRACKED.add(surface);
            return caption;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void logCall(String op, Object[] args) {
        StringBuilder out = new StringBuilder(op);
        if (args.length > 0) out.append(" target=").append(args[0]);
        if ("matrix".equals(op) && args.length >= 5) {
            out.append(" dsdx=").append(args[1])
                    .append(" dtdx=").append(args[2])
                    .append(" dsdy=").append(args[3])
                    .append(" dtdy=").append(args[4]);
        } else if ("matrixObject".equals(op) && args.length >= 2) {
            out.append(" value=").append(args[1]);
            if (args.length >= 3 && args[2] instanceof float[]) {
                float[] values = (float[]) args[2];
                out.append(" floats=");
                for (int i = 0; i < values.length; i++) {
                    if (i > 0) out.append(',');
                    out.append(values[i]);
                }
            }
        } else if ("scale".equals(op) && args.length >= 3) {
            out.append(" sx=").append(args[1]).append(" sy=").append(args[2]);
        } else if ("position".equals(op) && args.length >= 3) {
            out.append(" x=").append(args[1]).append(" y=").append(args[2]);
        } else if ("alpha".equals(op) && args.length >= 2) {
            out.append(" value=").append(args[1]);
        } else if (("crop".equals(op) || "windowCrop".equals(op)) && args.length >= 2) {
            out.append(" value=").append(args[1]);
            if (args.length >= 3) out.append('x').append(args[2]);
        } else if ("geometry".equals(op) && args.length >= 4) {
            out.append(" source=").append(args[1])
                    .append(" dest=").append(args[2])
                    .append(" transform=").append(args[3]);
        } else if ("reparent".equals(op) && args.length >= 2) {
            out.append(" parent=").append(args[1]);
        }
        log(out.toString());
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
