package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.view.SurfaceControl;

import java.util.Collections;
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
    private static final Set<SurfaceControl> TRACKED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static boolean installed;

    private SystemUiHandleMenuSurfaceProbe() {}

    static synchronized void install() {
        if (installed) return;
        int hooks = 0;
        hooks += hook("setMatrix",
                new Class<?>[]{SurfaceControl.class, float.class, float.class,
                        float.class, float.class},
                "matrix");
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
                            if (relevant) logCall(op, args);
                        }
                        return chain.proceed(args);
                    });
            return 1;
        } catch (Throwable error) {
            log("hook unavailable op=" + op + " error=" + error);
            return 0;
        }
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
        } else if ("scale".equals(op) && args.length >= 3) {
            out.append(" sx=").append(args[1]).append(" sy=").append(args[2]);
        } else if ("position".equals(op) && args.length >= 3) {
            out.append(" x=").append(args[1]).append(" y=").append(args[2]);
        } else if ("alpha".equals(op) && args.length >= 2) {
            out.append(" value=").append(args[1]);
        } else if (("crop".equals(op) || "windowCrop".equals(op)) && args.length >= 2) {
            out.append(" value=").append(args[1]);
            if (args.length >= 3) out.append('x').append(args[2]);
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
