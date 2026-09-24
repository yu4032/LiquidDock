package com.hellovoid.liquiddock;

import android.view.SurfaceControl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Production authority for HyperOS caption-menu Surface alpha.
 *
 * <p>HyperOS animates the outer caption-menu SurfaceControl while the Windowless View hierarchy
 * remains identity-transformed. The material fade therefore follows the compositor alpha directly.
 * No transaction values are modified.</p>
 */
final class SystemUiHandleMenuSurfaceAnimationAuthority {
    interface AlphaListener {
        void onAlpha(float alpha);
    }

    private static final Map<Integer, AlphaListener> ALPHA_LISTENERS =
            Collections.synchronizedMap(new HashMap<>());

    private static boolean installed;

    private SystemUiHandleMenuSurfaceAnimationAuthority() {}

    static synchronized boolean install() {
        if (installed) return true;
        try {
            HookUtil.hookMethod(
                    SurfaceControl.Transaction.class,
                    "setAlpha",
                    new Class<?>[]{SurfaceControl.class, float.class},
                    chain -> {
                        Object[] args = chain.getArgs().toArray(new Object[0]);
                        if (args.length >= 2
                                && args[0] instanceof SurfaceControl
                                && args[1] instanceof Number) {
                            dispatchAlpha(
                                    (SurfaceControl) args[0],
                                    ((Number) args[1]).floatValue());
                        }
                        return chain.proceed(args);
                    });
            installed = true;
            return true;
        } catch (Throwable error) {
            Api101Bridge.log(
                    "[DC][SystemUiHandleMenuGlass] Surface alpha authority unavailable", error);
            return false;
        }
    }

    static SurfaceControl windowSurface(Object controller) {
        if (controller == null) return null;
        HookUtil.InvocationResult<Object> result =
                HookUtil.tryInvoke(controller, "getWindowSurface");
        Object value = result.succeeded() ? result.value() : null;
        if (!(value instanceof SurfaceControl)) return null;
        SurfaceControl surface = (SurfaceControl) value;
        return surface.isValid() ? surface : null;
    }

    static void registerAlphaListener(SurfaceControl surface, AlphaListener listener) {
        if (surface == null || listener == null) return;
        int layerId = stableLayerId(surface);
        if (layerId >= 0) ALPHA_LISTENERS.put(layerId, listener);
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

    private static void dispatchAlpha(SurfaceControl surface, float alpha) {
        int layerId = stableLayerId(surface);
        AlphaListener listener = layerId >= 0 ? ALPHA_LISTENERS.get(layerId) : null;
        if (listener != null) {
            listener.onAlpha(Math.max(0f, Math.min(1f, alpha)));
        }
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
}
