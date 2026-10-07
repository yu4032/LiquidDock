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
    private static final Object ACCESS_LOCK = new Object();
    private static final Method VIEW_GET_VIEW_ROOT_IMPL =
            optionalDeclaredMethod(View.class, "getViewRootImpl");
    private static final Method DISPLAY_GET_INSTALL_ORIENTATION =
            optionalPublicMethod(Display.class, "getInstallOrientation");
    private static final Method SURFACE_IS_SAME =
            optionalPublicMethod(SurfaceControl.class, "isSameSurface", SurfaceControl.class);
    private static volatile ViewRootAccess cachedViewRootAccess;

    private static final class ViewRootAccess {
        final Class<?> type;
        final Field surfaceSize;
        final Field windowAttributes;
        final Method getSurfaceControl;
        private Class<?> attrsType;
        private Field surfaceInsets;

        ViewRootAccess(Class<?> type) throws ReflectiveOperationException {
            this.type = type;
            surfaceSize = accessible(findField(type, "mSurfaceSize"));
            windowAttributes = accessible(findField(type, "mWindowAttributes"));
            getSurfaceControl = accessible(findMethod(type, "getSurfaceControl"));
        }

        Rect surfaceInsets(Object attrs) {
            if (attrs == null) return null;
            Class<?> nextType = attrs.getClass();
            Field field = surfaceInsets;
            if (field == null || attrsType != nextType) {
                synchronized (this) {
                    field = surfaceInsets;
                    if (field == null || attrsType != nextType) {
                        try {
                            field = accessible(findField(nextType, "surfaceInsets"));
                            attrsType = nextType;
                            surfaceInsets = field;
                        } catch (Throwable ignored) {
                            return null;
                        }
                    }
                }
            }
            try {
                Object value = field.get(attrs);
                return value instanceof Rect ? (Rect) value : null;
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

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
        return inspect(root, -1, true);
    }

    static Endpoint inspect(View root, int knownRotation) {
        return inspect(root, knownRotation, true);
    }

    static Endpoint inspectGeometry(View root) {
        return inspect(root, -1, false);
    }

    private static Endpoint inspect(View root, int knownRotation, boolean includeGeneration) {
        if (root == null) return null;
        try {
            Object viewRoot = getViewRootImpl(root);
            if (viewRoot == null) return null;
            ViewRootAccess access = viewRootAccess(viewRoot.getClass());
            Object sizeValue = access.surfaceSize.get(viewRoot);
            if (!(sizeValue instanceof Point)) return null;
            Point surfaceSize = (Point) sizeValue;
            int surfaceWidth = surfaceSize.x;
            int surfaceHeight = surfaceSize.y;
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return null;

            Object attrs = access.windowAttributes.get(viewRoot);
            Rect surfaceInsets = access.surfaceInsets(attrs);
            int insetLeft = surfaceInsets != null ? surfaceInsets.left : 0;
            int insetTop = surfaceInsets != null ? surfaceInsets.top : 0;
            int insetRight = surfaceInsets != null ? surfaceInsets.right : 0;
            int insetBottom = surfaceInsets != null ? surfaceInsets.bottom : 0;
            int rotation = knownRotation >= 0 ? knownRotation : readConfigRotation(root);
            int bufferWidth = surfaceWidth;
            int bufferHeight = surfaceHeight;
            if (rotation == 1 || rotation == 3) {
                bufferWidth = surfaceHeight;
                bufferHeight = surfaceWidth;
            }

            Object value = access.getSurfaceControl.invoke(viewRoot);
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
                    includeGeneration
                            ? Miuix307PassBlurBridge.readSurfaceSequenceId(viewRoot) : -1,
                    includeGeneration
                            ? Miuix307PassBlurBridge.surfaceLayerId(rootSurface) : -1,
                    insetLeft,
                    insetTop,
                    insetRight,
                    insetBottom);
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

    static int readConfigRotation(View view) {
        Display display = view != null ? view.getDisplay() : null;
        if (display == null) return 0;
        int installOrientation = 0;
        Method method = DISPLAY_GET_INSTALL_ORIENTATION;
        if (method != null) {
            try {
                Object value = method.invoke(display);
                if (value instanceof Number) installOrientation = ((Number) value).intValue();
            } catch (Throwable ignored) {}
        }
        int result = (installOrientation + display.getRotation()) % 4;
        return result < 0 ? result + 4 : result;
    }

    private static Object getViewRootImpl(View view) throws Exception {
        Method method = VIEW_GET_VIEW_ROOT_IMPL;
        return method != null ? method.invoke(view) : null;
    }

    private static ViewRootAccess viewRootAccess(Class<?> type) throws ReflectiveOperationException {
        ViewRootAccess current = cachedViewRootAccess;
        if (current != null && current.type == type) return current;
        synchronized (ACCESS_LOCK) {
            current = cachedViewRootAccess;
            if (current == null || current.type != type) {
                current = new ViewRootAccess(type);
                cachedViewRootAccess = current;
            }
            return current;
        }
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

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }

    private static Method optionalDeclaredMethod(
            Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return accessible(type.getDeclaredMethod(name, parameterTypes));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method optionalPublicMethod(
            Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return accessible(type.getMethod(name, parameterTypes));
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isSameSurface(SurfaceControl first, SurfaceControl second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        Method method = SURFACE_IS_SAME;
        if (method != null) {
            try {
                Object value = method.invoke(first, second);
                return value instanceof Boolean && (Boolean) value;
            } catch (Throwable ignored) {}
        }
        return first.equals(second);
    }
}
