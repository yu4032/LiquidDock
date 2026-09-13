package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Live geometry authority for the Global Dock sidebar morph.
 *
 * <p>The vendor Dock material View reaches its final 206x1463 layout before the visible sidebar
 * has finished morphing from the thin line. The visible extent is instead updated by the drawable
 * hosted by {@code id/sidebar_background}. We resolve that drawable from the real resource/View
 * graph, then hook its unique {@code (RectF, float) -> void} mutation structurally. Corner radius
 * stays authoritative on DockLayout's live ViewOutlineProvider; this class owns only the live rect.
 */
final class SecurityCenterSidebarDrawableGeometry {
    static final class Snapshot {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Snapshot(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    static final class State {
        private boolean live;
        private float left;
        private float top;
        private float right;
        private float bottom;

        synchronized void update(float left, float top, float right, float bottom) {
            if (!finite(left) || !finite(top) || !finite(right) || !finite(bottom)
                    || right <= left || bottom <= top) return;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
            live = true;
        }

        synchronized boolean hasLiveGeometry() {
            return live;
        }

        synchronized Snapshot snapshot() {
            return live ? new Snapshot(left, top, right, bottom) : null;
        }

        synchronized void beginAnimationEpoch() {
            live = false;
        }

        synchronized void clear() {
            beginAnimationEpoch();
        }
    }

    private static final String TAG = "[DC][SecurityCenterGlass]";
    private static final Object LOCK = new Object();
    private static final State LIVE = new State();
    private static final WeakHashMap<Object, WeakReference<View>> DRAWABLE_OWNERS =
            new WeakHashMap<>();
    private static final Set<Class<?>> HOOKED_DRAWABLE_CLASSES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static WeakReference<View> dockRef = new WeakReference<>(null);
    private static WeakReference<View> rootRef = new WeakReference<>(null);
    private static WeakReference<View> drawableOwnerRef = new WeakReference<>(null);

    private SecurityCenterSidebarDrawableGeometry() {}

    /** Bind the exact runtime Global Dock material to its semantic sidebar-background drawable. */
    static boolean bindDock(View dock) {
        if (dock == null || !dock.isAttachedToWindow()) return false;
        View root = dock.getRootView();
        if (root == null || !root.isAttachedToWindow()) return false;

        final int backgroundId;
        try {
            backgroundId = resolveSidebarBackgroundId(root);
        } catch (Throwable error) {
            log("sidebar drawable resource unavailable", error);
            return false;
        }
        View owner = root.findViewById(backgroundId);
        if (owner == null || !owner.isAttachedToWindow()) return false;

        Drawable drawable = resolveMorphDrawable(owner);
        if (drawable == null) {
            log("sidebar drawable morph contract unavailable", null);
            return false;
        }
        Method update = resolveMorphMethod(drawable.getClass());
        if (update == null) {
            log("sidebar drawable morph method ambiguous or unavailable", null);
            return false;
        }

        boolean installHook = false;
        synchronized (LOCK) {
            View previousDock = dockRef.get();
            View previousRoot = rootRef.get();
            View previousOwner = drawableOwnerRef.get();
            if (previousDock != dock || previousRoot != root || previousOwner != owner) {
                LIVE.clear();
            }
            dockRef = new WeakReference<>(dock);
            rootRef = new WeakReference<>(root);
            drawableOwnerRef = new WeakReference<>(owner);
            DRAWABLE_OWNERS.put(drawable, new WeakReference<>(owner));
            installHook = HOOKED_DRAWABLE_CLASSES.add(drawable.getClass());
        }

        if (installHook) {
            try {
                HookUtil.hook(update, chain -> {
                    Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                    Object target = chain.getThisObject();
                    Object rectArg = chain.getArgs().isEmpty() ? null : chain.getArgs().get(0);
                    if (rectArg instanceof RectF) {
                        publish(target, (RectF) rectArg);
                    }
                    return result;
                });
                log("sidebar drawable geometry hook installed class="
                        + drawable.getClass().getName() + " method=" + update.getName(), null);
            } catch (Throwable error) {
                synchronized (LOCK) {
                    HOOKED_DRAWABLE_CLASSES.remove(drawable.getClass());
                }
                log("sidebar drawable geometry hook unavailable", error);
                return false;
            }
        }
        return true;
    }

    /** Clear live ownership when Security Center returns the material to the vendor. */
    static void clearRuntime() {
        synchronized (LOCK) {
            LIVE.clear();
            dockRef = new WeakReference<>(null);
            rootRef = new WeakReference<>(null);
            drawableOwnerRef = new WeakReference<>(null);
            DRAWABLE_OWNERS.clear();
        }
    }

    /**
     * Replace a candidate only when it is the currently bound Dock material's own static bounds.
     * Other nodes (toolbox / All Apps) therefore keep their existing geometry path.
     */
    static Snapshot overrideForCandidate(
            int rootWidth,
            int rootHeight,
            float candidateLeft,
            float candidateTop,
            float candidateRight,
            float candidateBottom) {
        View dock;
        View root;
        View owner;
        Snapshot local;
        synchronized (LOCK) {
            dock = dockRef.get();
            root = rootRef.get();
            owner = drawableOwnerRef.get();
            local = LIVE.snapshot();
        }
        if (local == null || dock == null || root == null || owner == null
                || !dock.isAttachedToWindow() || !root.isAttachedToWindow()
                || !owner.isAttachedToWindow() || dock.getRootView() != root
                || owner.getRootView() != root
                || root.getWidth() != rootWidth || root.getHeight() != rootHeight) {
            return null;
        }

        Snapshot staticDock = mapRectToRoot(
                dock, root, 0f, 0f, dock.getWidth(), dock.getHeight());
        if (staticDock == null
                || !same(candidateLeft, staticDock.left)
                || !same(candidateTop, staticDock.top)
                || !same(candidateRight, staticDock.right)
                || !same(candidateBottom, staticDock.bottom)) {
            return null;
        }
        return mapRectToRoot(owner, root, local.left, local.top, local.right, local.bottom);
    }

    private static void publish(Object drawable, RectF rect) {
        if (drawable == null || rect == null) return;
        View owner;
        synchronized (LOCK) {
            WeakReference<View> ownerRef = DRAWABLE_OWNERS.get(drawable);
            owner = ownerRef != null ? ownerRef.get() : null;
            if (owner == null || owner != drawableOwnerRef.get()) return;
        }
        if (!owner.isAttachedToWindow() || owner.getRootView() != rootRef.get()) return;
        LIVE.update(rect.left, rect.top, rect.right, rect.bottom);
    }

    private static int resolveSidebarBackgroundId(View root) {
        String entry = SecurityCenterHookSpec.SIDEBAR_BACKGROUND_RESOURCE;
        String packageName = root.getContext().getPackageName();
        int id = root.getResources().getIdentifier(entry, "id", packageName);
        if (id == 0
                || !packageName.equals(root.getResources().getResourcePackageName(id))
                || !"id".equals(root.getResources().getResourceTypeName(id))
                || !entry.equals(root.getResources().getResourceEntryName(id))) {
            throw new IllegalStateException("missing id/" + entry);
        }
        return id;
    }

    private static Drawable resolveMorphDrawable(View owner) {
        List<Drawable> candidates = new ArrayList<>(2);
        Drawable background = owner.getBackground();
        if (background != null && resolveMorphMethod(background.getClass()) != null) {
            candidates.add(background);
        }
        if (owner instanceof ImageView) {
            Drawable image = ((ImageView) owner).getDrawable();
            if (image != null && image != background && resolveMorphMethod(image.getClass()) != null) {
                candidates.add(image);
            }
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /** Resolve the observed SidebarLineDrawable update by stable method shape, not vendor class name. */
    private static Method resolveMorphMethod(Class<?> drawableClass) {
        Method match = null;
        Class<?> current = drawableClass;
        while (current != null && Drawable.class.isAssignableFrom(current)) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (method.getReturnType() == void.class
                        && params.length == 2
                        && params[0] == RectF.class
                        && params[1] == float.class) {
                    if (match != null) return null;
                    match = method;
                }
            }
            current = current.getSuperclass();
        }
        if (match != null) match.setAccessible(true);
        return match;
    }

    private static Snapshot mapRectToRoot(
            View source, View root, float left, float top, float right, float bottom) {
        if (source == null || root == null || !finite(left) || !finite(top)
                || !finite(right) || !finite(bottom) || right <= left || bottom <= top) return null;
        try {
            Matrix sourceToGlobal = new Matrix();
            source.transformMatrixToGlobal(sourceToGlobal);
            Matrix rootToGlobal = new Matrix();
            root.transformMatrixToGlobal(rootToGlobal);
            Matrix globalToRoot = new Matrix();
            if (!rootToGlobal.invert(globalToRoot)) return null;
            float[] points = new float[]{
                    left, top,
                    right, top,
                    right, bottom,
                    left, bottom
            };
            sourceToGlobal.mapPoints(points);
            globalToRoot.mapPoints(points);
            float mappedLeft = min(points[0], points[2], points[4], points[6]);
            float mappedTop = min(points[1], points[3], points[5], points[7]);
            float mappedRight = max(points[0], points[2], points[4], points[6]);
            float mappedBottom = max(points[1], points[3], points[5], points[7]);
            return finite(mappedLeft) && finite(mappedTop) && finite(mappedRight)
                    && finite(mappedBottom) && mappedRight > mappedLeft && mappedBottom > mappedTop
                    ? new Snapshot(mappedLeft, mappedTop, mappedRight, mappedBottom)
                    : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean same(float first, float second) {
        return Math.abs(first - second) < 0.75f;
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }

    private static float min(float a, float b, float c, float d) {
        return Math.min(Math.min(a, b), Math.min(c, d));
    }

    private static float max(float a, float b, float c, float d) {
        return Math.max(Math.max(a, b), Math.max(c, d));
    }

    private static void log(String message, Throwable error) {
        try {
            if (error != null) Api101Bridge.log(TAG + " " + message + ": " + error);
            else Api101Bridge.log(TAG + " " + message);
        } catch (Throwable ignored) {}
    }
}
