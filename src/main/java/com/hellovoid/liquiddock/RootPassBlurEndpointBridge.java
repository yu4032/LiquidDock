package com.hellovoid.liquiddock;

import android.graphics.Point;
import android.graphics.Rect;
import android.view.Display;
import android.view.SurfaceControl;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * System-private ViewRoot/SurfaceControl inspection boundary for the generic root PassBlur backend.
 * Project-owned runtime code consumes only the typed {@link Endpoint} snapshot below.
 */
final class RootPassBlurEndpointBridge {
    static final class Endpoint {
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final int rotation;
        final SurfaceControl rootSurface;
        final int viewRootIdentity;
        final int surfaceSequenceId;
        final int rootLayerId;
        final int insetLeft;
        final int insetTop;
        final int insetRight;
        final int insetBottom;

        Endpoint(
                int surfaceWidth,
                int surfaceHeight,
                int bufferWidth,
                int bufferHeight,
                int rotation,
                SurfaceControl rootSurface,
                int viewRootIdentity,
                int surfaceSequenceId,
                int rootLayerId,
                int insetLeft,
                int insetTop,
                int insetRight,
                int insetBottom) {
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.rotation = rotation;
            this.rootSurface = rootSurface;
            this.viewRootIdentity = viewRootIdentity;
            this.surfaceSequenceId = surfaceSequenceId;
            this.rootLayerId = rootLayerId;
            this.insetLeft = insetLeft;
            this.insetTop = insetTop;
            this.insetRight = insetRight;
            this.insetBottom = insetBottom;
        }

        boolean isValid() {
            return rootSurface != null && rootSurface.isValid()
                    && surfaceWidth > 0 && surfaceHeight > 0
                    && bufferWidth > 0 && bufferHeight > 0;
        }
    }

    private RootPassBlurEndpointBridge() {}

    static Endpoint inspect(View root) {
        if (root == null) return null;
        try {
            Object viewRoot = getViewRootImpl(root);
            if (viewRoot == null) return null;
            Field sizeField = findField(viewRoot.getClass(), "mSurfaceSize");
            sizeField.setAccessible(true);
            Object sizeValue = sizeField.get(viewRoot);
            if (!(sizeValue instanceof Point)) return null;
            Point surfaceSize = (Point) sizeValue;
            int surfaceWidth = surfaceSize.x;
            int surfaceHeight = surfaceSize.y;
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return null;

            Rect surfaceInsets = readSurfaceInsets(viewRoot);
            int rotation = readConfigRotation(root);
            int bufferWidth = surfaceWidth;
            int bufferHeight = surfaceHeight;
            if (rotation == 1 || rotation == 3) {
                bufferWidth = surfaceHeight;
                bufferHeight = surfaceWidth;
            }

            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object value = getSurfaceControl.invoke(viewRoot);
            SurfaceControl rootSurface = value instanceof SurfaceControl
                    ? (SurfaceControl) value : null;
            return new Endpoint(
                    surfaceWidth,
                    surfaceHeight,
                    bufferWidth,
                    bufferHeight,
                    rotation,
                    rootSurface,
                    System.identityHashCode(viewRoot),
                    Miuix307PassBlurBridge.readSurfaceSequenceId(viewRoot),
                    Miuix307PassBlurBridge.surfaceLayerId(rootSurface),
                    surfaceInsets.left,
                    surfaceInsets.top,
                    surfaceInsets.right,
                    surfaceInsets.bottom);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean isBindingValid(Miuix307PassBlurBridge.Binding binding) {
        return binding != null && binding.bound
                && binding.rootSurface != null && binding.rootSurface.isValid();
    }

    static boolean sameGeneration(
            Miuix307PassBlurBridge.Binding binding,
            Endpoint endpoint) {
        if (binding == null || endpoint == null || !endpoint.isValid()) return false;
        if (binding.viewRootIdentity != 0 && endpoint.viewRootIdentity != 0
                && binding.viewRootIdentity != endpoint.viewRootIdentity) return false;

        boolean comparedImmutableGeneration = false;
        if (binding.rootLayerId >= 0 && endpoint.rootLayerId >= 0) {
            comparedImmutableGeneration = true;
            if (binding.rootLayerId != endpoint.rootLayerId) return false;
        }
        if (binding.surfaceSequenceId >= 0 && endpoint.surfaceSequenceId >= 0) {
            comparedImmutableGeneration = true;
            if (binding.surfaceSequenceId != endpoint.surfaceSequenceId) return false;
        }
        if (comparedImmutableGeneration) return true;
        return isSameSurface(binding.rootSurface, endpoint.rootSurface);
    }

    private static Rect readSurfaceInsets(Object viewRoot) {
        Rect result = new Rect();
        if (viewRoot == null) return result;
        try {
            Field attrsField = findField(viewRoot.getClass(), "mWindowAttributes");
            attrsField.setAccessible(true);
            Object attrs = attrsField.get(viewRoot);
            if (attrs == null) return result;
            Field insetsField = findField(attrs.getClass(), "surfaceInsets");
            insetsField.setAccessible(true);
            Object value = insetsField.get(attrs);
            if (value instanceof Rect) result.set((Rect) value);
        } catch (Throwable ignored) {}
        return result;
    }

    private static int readConfigRotation(View view) {
        Display display = view != null ? view.getDisplay() : null;
        if (display == null) return 0;
        int installOrientation = 0;
        try {
            Method method = Display.class.getMethod("getInstallOrientation");
            Object value = method.invoke(display);
            if (value instanceof Number) installOrientation = ((Number) value).intValue();
        } catch (Throwable ignored) {}
        int result = (installOrientation + display.getRotation()) % 4;
        return result < 0 ? result + 4 : result;
    }

    private static Object getViewRootImpl(View view) throws Exception {
        Method method = View.class.getDeclaredMethod("getViewRootImpl");
        method.setAccessible(true);
        return method.invoke(view);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean isSameSurface(SurfaceControl first, SurfaceControl second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        try {
            Method method = SurfaceControl.class.getMethod("isSameSurface", SurfaceControl.class);
            Object value = method.invoke(first, second);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return first.equals(second);
        }
    }
}
