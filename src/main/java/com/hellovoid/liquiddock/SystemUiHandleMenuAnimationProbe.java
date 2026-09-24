package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import java.util.Locale;

/**
 * Read-only visual transform probe for the SystemUI caption Handle Menu.
 *
 * <p>The native menu owns its open/close animation. This probe never writes alpha, scale,
 * translation, layout, blur state, or SurfaceControl state. It records only property changes on
 * the real menu hierarchy so a backdrop-scale defect can be attributed to a View transform versus
 * an outer Windowless/SurfaceControlViewHost leash before any production compensation is added.</p>
 */
final class SystemUiHandleMenuAnimationProbe {
    private static final String TAG = "[DC][SystemUiHandleMenuAnim]";

    private final View root;
    private final View sourceRoot;
    private final View target;

    private ViewTreeObserver observer;
    private ViewTreeObserver.OnPreDrawListener listener;
    private String lastSignature;
    private boolean started;

    SystemUiHandleMenuAnimationProbe(View root, View sourceRoot, View target) {
        this.root = root;
        this.sourceRoot = sourceRoot;
        this.target = target;
    }

    void start() {
        if (started) return;
        ViewTreeObserver next = root.getViewTreeObserver();
        if (!next.isAlive()) return;
        started = true;
        observer = next;
        listener = () -> {
            recordIfChanged();
            return true;
        };
        next.addOnPreDrawListener(listener);
        recordIfChanged();
    }

    void stop() {
        if (!started) return;
        started = false;
        ViewTreeObserver registered = observer;
        ViewTreeObserver.OnPreDrawListener registeredListener = listener;
        observer = null;
        listener = null;
        if (registered != null && registered.isAlive() && registeredListener != null) {
            registered.removeOnPreDrawListener(registeredListener);
        } else if (registeredListener != null) {
            ViewTreeObserver current = root.getViewTreeObserver();
            if (current.isAlive()) current.removeOnPreDrawListener(registeredListener);
        }
        lastSignature = null;
    }

    private void recordIfChanged() {
        if (!started) return;
        String signature = buildSignature();
        if (signature.equals(lastSignature)) return;
        lastSignature = signature;
        log(signature);
    }

    private String buildSignature() {
        StringBuilder out = new StringBuilder(768);
        appendView(out, "root", root);
        if (sourceRoot != root) appendView(out, "sourceRoot", sourceRoot);
        appendView(out, "target", target);

        ViewParent parent = target.getParent();
        int depth = 0;
        while (parent instanceof View && parent != root && depth < 5) {
            appendView(out, "parent" + depth, (View) parent);
            parent = parent.getParent();
            depth++;
        }

        RootPassBlurEndpointBridge.Endpoint endpoint =
                RootPassBlurEndpointBridge.inspect(sourceRoot);
        if (endpoint != null) {
            out.append(" endpoint={")
                    .append(endpoint.surfaceWidth).append('x').append(endpoint.surfaceHeight)
                    .append(" buffer=").append(endpoint.bufferWidth).append('x')
                    .append(endpoint.bufferHeight)
                    .append(" rot=").append(endpoint.rotation)
                    .append(" layer=").append(endpoint.rootLayerId)
                    .append(" seq=").append(endpoint.surfaceSequenceId)
                    .append(" vri=").append(endpoint.viewRootIdentity)
                    .append('}');
        } else {
            out.append(" endpoint=<unavailable>");
        }
        return out.toString();
    }

    private static void appendView(StringBuilder out, String label, View view) {
        if (out.length() > 0) out.append(" | ");
        Rect visible = new Rect();
        boolean hasVisible = false;
        try {
            hasVisible = view.getGlobalVisibleRect(visible);
        } catch (Throwable ignored) {}

        int[] location = new int[2];
        try {
            view.getLocationOnScreen(location);
        } catch (Throwable ignored) {}

        float[] matrix = new float[9];
        try {
            Matrix local = view.getMatrix();
            local.getValues(matrix);
        } catch (Throwable ignored) {}

        out.append(label).append('=')
                .append(view.getClass().getSimpleName())
                .append('#').append(Integer.toHexString(System.identityHashCode(view)))
                .append('(').append(resourceName(view)).append(')')
                .append(" local=").append(view.getWidth()).append('x').append(view.getHeight())
                .append(" screen=").append(location[0]).append(',').append(location[1])
                .append(" global=").append(hasVisible ? visible.toShortString() : "<hidden>")
                .append(" scale=").append(f(view.getScaleX())).append(',').append(f(view.getScaleY()))
                .append(" trans=").append(f(view.getTranslationX())).append(',')
                .append(f(view.getTranslationY())).append(',').append(f(view.getTranslationZ()))
                .append(" pivot=").append(f(view.getPivotX())).append(',').append(f(view.getPivotY()))
                .append(" alpha=").append(f(view.getAlpha()))
                .append(" matrix=[")
                .append(f(matrix[Matrix.MSCALE_X])).append(',')
                .append(f(matrix[Matrix.MSKEW_X])).append(',')
                .append(f(matrix[Matrix.MTRANS_X])).append(';')
                .append(f(matrix[Matrix.MSKEW_Y])).append(',')
                .append(f(matrix[Matrix.MSCALE_Y])).append(',')
                .append(f(matrix[Matrix.MTRANS_Y])).append(']');
    }

    private static String resourceName(View view) {
        try {
            int id = view.getId();
            if (id != View.NO_ID) return view.getResources().getResourceEntryName(id);
        } catch (Throwable ignored) {}
        return "no-id";
    }

    private static String f(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return String.valueOf(value);
        return String.format(Locale.US, "%.3f", value);
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
