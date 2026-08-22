from pathlib import Path

ROOT = Path('.')


def read(path):
    return (ROOT / path).read_text()


def write(path, text):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def replace_once(path, old, new):
    text = read(path)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f'{path}: expected one anchor, found {count}: {old[:100]!r}')
    write(path, text.replace(old, new, 1))


def replace_section(path, start, end, replacement):
    text = read(path)
    a = text.find(start)
    if a < 0:
        raise RuntimeError(f'{path}: missing start {start!r}')
    b = text.find(end, a + len(start))
    if b < 0:
        raise RuntimeError(f'{path}: missing end {end!r}')
    write(path, text[:a] + replacement + text[b:])


# --- Pure surface-content UV mapping ---
write('src/main/java/com/hellovoid/liquiddock/LauncherGlassSurfaceContentRect.java', r'''package com.hellovoid.liquiddock;

/** Maps ViewRoot surface-buffer coordinates to the actual Decor/root content UV rectangle. */
final class LauncherGlassSurfaceContentRect {
    final float left;
    final float bottom;
    final float width;
    final float height;

    private LauncherGlassSurfaceContentRect(float left, float bottom, float width, float height) {
        this.left = left;
        this.bottom = bottom;
        this.width = width;
        this.height = height;
    }

    static LauncherGlassSurfaceContentRect full() {
        return new LauncherGlassSurfaceContentRect(0f, 0f, 1f, 1f);
    }

    static LauncherGlassSurfaceContentRect resolve(
            int surfaceWidth, int surfaceHeight,
            int insetLeft, int insetTop, int insetRight, int insetBottom) {
        if (surfaceWidth <= 0 || surfaceHeight <= 0) return full();
        int left = clamp(insetLeft, 0, surfaceWidth);
        int right = clamp(insetRight, 0, surfaceWidth);
        int top = clamp(insetTop, 0, surfaceHeight);
        int bottom = clamp(insetBottom, 0, surfaceHeight);
        int contentWidth = surfaceWidth - left - right;
        int contentHeight = surfaceHeight - top - bottom;
        if (contentWidth <= 0 || contentHeight <= 0) return full();
        return new LauncherGlassSurfaceContentRect(
                left / (float) surfaceWidth,
                bottom / (float) surfaceHeight,
                contentWidth / (float) surfaceWidth,
                contentHeight / (float) surfaceHeight);
    }

    boolean sameAs(LauncherGlassSurfaceContentRect other) {
        return other != null
                && Math.abs(left - other.left) < 0.00001f
                && Math.abs(bottom - other.bottom) < 0.00001f
                && Math.abs(width - other.width) < 0.00001f
                && Math.abs(height - other.height) < 0.00001f;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
''')


# --- Icon visual bounds: use TextView's own top-compound-drawable placement semantics. ---
write('src/main/java/com/hellovoid/liquiddock/LauncherGlassIconGeometry.java', r'''package com.hellovoid.liquiddock;

import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.TextView;

/** Resolves only the icon graphic inside a label-bearing Launcher ShortcutIcon. */
final class LauncherGlassIconGeometry {
    static final class Bounds {
        final float left;
        final float top;
        final float right;
        final float bottom;

        Bounds(float left, float top, float right, float bottom) {
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        float width() { return Math.max(0f, right - left); }
        float height() { return Math.max(0f, bottom - top); }
    }

    private LauncherGlassIconGeometry() {}

    static Bounds resolve(View host) {
        if (host == null || host.getWidth() <= 0 || host.getHeight() <= 0) return null;
        if (host instanceof TextView) {
            TextView text = (TextView) host;
            Drawable[] drawables = text.getCompoundDrawables();
            Drawable top = drawables != null && drawables.length > 1 ? drawables[1] : null;
            if (top == null) {
                Drawable[] relative = text.getCompoundDrawablesRelative();
                top = relative != null && relative.length > 1 ? relative[1] : null;
            }
            Bounds fromDrawable = topDrawableBounds(text, top);
            if (fromDrawable != null) return fromDrawable;

            int availableWidth = Math.max(1,
                    host.getWidth() - host.getPaddingLeft() - host.getPaddingRight());
            int labelReserve = Math.max(0, text.getLineHeight())
                    + Math.max(0, text.getCompoundDrawablePadding());
            int availableHeight = Math.max(1,
                    host.getHeight() - host.getPaddingTop() - host.getPaddingBottom() - labelReserve);
            int side = Math.max(1, Math.min(availableWidth, availableHeight));
            return fallback(host.getWidth(), host.getHeight(), side, side, host.getPaddingTop());
        }
        int side = Math.max(1, Math.min(host.getWidth(), host.getHeight()));
        return fallback(host.getWidth(), host.getHeight(), side, side, host.getPaddingTop());
    }

    private static Bounds topDrawableBounds(TextView text, Drawable drawable) {
        if (text == null || drawable == null) return null;
        Rect b = drawable.getBounds();
        int width = b.width() > 0 ? b.width() : drawable.getIntrinsicWidth();
        int height = b.height() > 0 ? b.height() : drawable.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return null;

        int boundLeft = b.width() > 0 ? b.left : 0;
        int boundTop = b.height() > 0 ? b.top : 0;
        int compoundLeft = text.getCompoundPaddingLeft();
        int compoundRight = text.getCompoundPaddingRight();
        int hspace = text.getWidth() - compoundRight - compoundLeft;
        float translateX = text.getScrollX() + compoundLeft + (hspace - width) * 0.5f;
        float translateY = text.getScrollY() + text.getPaddingTop();
        return clamp(text.getWidth(), text.getHeight(),
                translateX + boundLeft, translateY + boundTop,
                translateX + boundLeft + width, translateY + boundTop + height);
    }

    static Bounds fallback(
            int hostWidth, int hostHeight,
            int iconWidth, int iconHeight, int topOffset) {
        int safeHostWidth = Math.max(1, hostWidth);
        int safeHostHeight = Math.max(1, hostHeight);
        float top = Math.max(0f, Math.min(safeHostHeight - 1f, topOffset));
        float width = Math.max(1f, Math.min(safeHostWidth, iconWidth));
        float height = Math.max(1f, Math.min(safeHostHeight - top, iconHeight));
        float left = Math.max(0f, (safeHostWidth - width) * 0.5f);
        return clamp(safeHostWidth, safeHostHeight, left, top, left + width, top + height);
    }

    private static Bounds clamp(
            int hostWidth, int hostHeight,
            float left, float top, float right, float bottom) {
        float l = Math.max(0f, Math.min(hostWidth, left));
        float t = Math.max(0f, Math.min(hostHeight, top));
        float r = Math.max(l, Math.min(hostWidth, right));
        float b = Math.max(t, Math.min(hostHeight, bottom));
        return r > l && b > t ? new Bounds(l, t, r, b) : null;
    }
}
''')


# --- General lightweight static node ---
write('src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java', r'''package com.hellovoid.liquiddock;

import android.animation.ValueAnimator;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;

import com.hellovoid.prismal.PrismalInteractionState;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Lightweight static Launcher glass binding. Owns no View, Surface, EGL surface or GPU resource. */
final class LauncherGlassStaticNode {
    private static final Map<View, WeakReference<LauncherGlassStaticNode>> BY_MATERIAL =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final long PRESS_IN_DURATION_MS = 90L;
    private static final long PRESS_OUT_DURATION_MS = 160L;

    private final WeakReference<View> materialRef;
    private final LauncherGlassDragState.Kind kind;
    private final LauncherGlassScrollMotionTracker workspaceScrollMotion =
            new LauncherGlassScrollMotionTracker();
    private WeakReference<View> workspaceRef = new WeakReference<>(null);
    private volatile LauncherGlassSession session;
    private final LiquidDockConfig.Glass glassConfig;
    private volatile float nativeCornerRadiusPx;
    private volatile boolean disposed;
    private volatile boolean suppressedByFolderOpen;
    private volatile boolean suppressedByDrag;
    private boolean geometryDirty = true;
    private boolean pressTarget;
    private float pressProgress;
    private float glowCenterX = 0.5f;
    private float glowCenterY = 0.5f;
    private ValueAnimator pressAnimator;
    private Object lastParent;
    private int lastLeft = Integer.MIN_VALUE;
    private int lastTop = Integer.MIN_VALUE;
    private int lastRight = Integer.MIN_VALUE;
    private int lastBottom = Integer.MIN_VALUE;
    private int lastVisibility = Integer.MIN_VALUE;
    private float lastAlpha = Float.NaN;
    private final float[] lastMatrix = new float[9];
    private boolean matrixInitialized;
    private final View.OnAttachStateChangeListener materialAttachListener;

    private LauncherGlassStaticNode(
            View materialHost,
            LauncherGlassDragState.Kind kind,
            LauncherGlassSession session,
            float cornerRadiusPx,
            LiquidDockConfig.Glass glassConfig) {
        materialRef = new WeakReference<>(materialHost);
        this.kind = kind != null ? kind : LauncherGlassDragState.Kind.FOLDER;
        this.session = session;
        this.glassConfig = glassConfig;
        nativeCornerRadiusPx = Math.max(0f, cornerRadiusPx);
        materialAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {
                geometryDirty = true;
                LauncherGlassSession live = ensureLiveSession();
                if (live != null) live.registerStaticNode(LauncherGlassStaticNode.this);
            }

            @Override public void onViewDetachedFromWindow(View v) {
                resetPressInteraction(false);
                LauncherGlassSession live = session;
                if (live != null) live.unregisterStaticNode(LauncherGlassStaticNode.this);
                geometryDirty = true;
            }
        };
        materialHost.addOnAttachStateChangeListener(materialAttachListener);
    }

    static LauncherGlassStaticNode attachToMaterial(
            View materialHost, float cornerRadiusPx, LiquidDockConfig.Glass glassConfig) {
        return attachToMaterial(materialHost, LauncherGlassDragState.Kind.FOLDER,
                cornerRadiusPx, glassConfig);
    }

    static LauncherGlassStaticNode attachToMaterial(
            View materialHost, LauncherGlassDragState.Kind kind,
            float cornerRadiusPx, LiquidDockConfig.Glass glassConfig) {
        if (materialHost == null) return null;
        LauncherGlassDragState.Kind resolvedKind = kind != null
                ? kind : LauncherGlassDragState.Kind.FOLDER;
        WeakReference<LauncherGlassStaticNode> reference = BY_MATERIAL.get(materialHost);
        LauncherGlassStaticNode existing = reference != null ? reference.get() : null;
        if (existing != null && !existing.disposed && existing.kind == resolvedKind) {
            existing.setNativeCornerRadiusPx(cornerRadiusPx);
            LauncherGlassSession live = existing.ensureLiveSession();
            if (live != null) live.registerStaticNode(existing);
            return existing;
        }
        if (existing != null && !existing.disposed) existing.dispose();
        LauncherGlassSession shared = LauncherGlassSessionRegistry.acquire(materialHost, glassConfig);
        if (shared == null) return null;
        LauncherGlassStaticNode node = new LauncherGlassStaticNode(
                materialHost, resolvedKind, shared, cornerRadiusPx, glassConfig);
        BY_MATERIAL.put(materialHost, new WeakReference<>(node));
        shared.registerStaticNode(node);
        return node;
    }

    static LauncherGlassStaticNode find(View materialHost) {
        if (materialHost == null) return null;
        WeakReference<LauncherGlassStaticNode> reference = BY_MATERIAL.get(materialHost);
        LauncherGlassStaticNode node = reference != null ? reference.get() : null;
        return node != null && !node.disposed ? node : null;
    }

    View materialHost() { return materialRef.get(); }
    LauncherGlassDragState.Kind kind() { return kind; }

    void requestLifecycleRefresh() {
        if (disposed) return;
        geometryDirty = true;
        LauncherGlassSession live = ensureLiveSession();
        if (live != null) live.requestLifecycleRefresh();
    }

    void setSuppressedByFolderOpen(boolean suppressed) {
        if (disposed || suppressedByFolderOpen == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByFolderOpen = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

    void setSuppressedByDrag(boolean suppressed) {
        if (disposed || suppressedByDrag == suppressed) return;
        if (suppressed) resetPressInteraction(false);
        suppressedByDrag = suppressed;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

    void setNativeCornerRadiusPx(float cornerRadiusPx) {
        if (disposed || !Float.isFinite(cornerRadiusPx)) return;
        float next = Math.max(0f, cornerRadiusPx);
        if (Math.abs(nativeCornerRadiusPx - next) < 0.01f) return;
        nativeCornerRadiusPx = next;
        geometryDirty = true;
        requestLifecycleRefresh();
    }

    void setPressInteraction(boolean pressed, float normalizedX, float normalizedY) {
        if (disposed) return;
        float nextX = clamp01(normalizedX);
        float nextY = clamp01(normalizedY);
        boolean centerChanged = glowCenterX != nextX || glowCenterY != nextY;
        glowCenterX = nextX;
        glowCenterY = nextY;
        if (pressTarget != pressed) {
            pressTarget = pressed;
            animatePressTo(pressed ? 1f : 0f);
        } else if (centerChanged) {
            publishInteraction();
        }
    }

    void resetPressInteraction(boolean animated) {
        if (disposed) return;
        pressTarget = false;
        if (animated && pressProgress > 0f) {
            animatePressTo(0f);
            return;
        }
        if (pressAnimator != null) {
            pressAnimator.cancel();
            pressAnimator = null;
        }
        pressProgress = 0f;
        glowCenterX = 0.5f;
        glowCenterY = 0.5f;
        publishInteraction();
    }

    private void animatePressTo(float target) {
        if (pressAnimator != null) pressAnimator.cancel();
        float start = pressProgress;
        if (Math.abs(start - target) < 0.001f) {
            pressProgress = target;
            publishInteraction();
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, target);
        pressAnimator = animator;
        animator.setDuration(target > start ? PRESS_IN_DURATION_MS : PRESS_OUT_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (pressAnimator != valueAnimator || disposed) return;
            pressProgress = (Float) valueAnimator.getAnimatedValue();
            publishInteraction();
        });
        animator.start();
    }

    private void publishInteraction() {
        LauncherGlassSession live = ensureLiveSession();
        if (disposed || live == null) return;
        live.updateStaticInteraction(this,
                new PrismalInteractionState(pressProgress, glowCenterX, glowCenterY));
    }

    boolean syncFromMaterial() {
        View material = materialRef.get();
        if (disposed || material == null) return false;
        boolean changed = geometryDirty;
        geometryDirty = false;
        changed |= consumeWorkspaceScrollMotion();
        Object parent = material.getParent();
        if (lastParent != parent) { lastParent = parent; changed = true; }
        int left = material.getLeft();
        int top = material.getTop();
        int right = material.getRight();
        int bottom = material.getBottom();
        if (lastLeft != left || lastTop != top || lastRight != right || lastBottom != bottom) {
            lastLeft = left;
            lastTop = top;
            lastRight = right;
            lastBottom = bottom;
            changed = true;
        }
        int visibility = material.getVisibility();
        if (lastVisibility != visibility) { lastVisibility = visibility; changed = true; }
        float alpha = material.getAlpha();
        if (!Float.isFinite(lastAlpha) || Math.abs(lastAlpha - alpha) >= 0.01f) {
            lastAlpha = alpha;
            changed = true;
        }
        float[] matrix = new float[9];
        material.getMatrix().getValues(matrix);
        if (!matrixInitialized) {
            System.arraycopy(matrix, 0, lastMatrix, 0, matrix.length);
            matrixInitialized = true;
            changed = true;
        } else {
            for (int i = 0; i < matrix.length; i++) {
                if (Math.abs(lastMatrix[i] - matrix[i]) >= 0.001f) {
                    System.arraycopy(matrix, 0, lastMatrix, 0, matrix.length);
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    private boolean consumeWorkspaceScrollMotion() {
        View material = materialRef.get();
        View workspace = workspaceRef.get();
        if (workspace == null || !workspace.isAttachedToWindow()) {
            workspace = findWorkspaceAncestor(material);
            workspaceRef = new WeakReference<>(workspace);
        }
        if (workspace == null) return workspaceScrollMotion.update(null, 0, 0);
        return workspaceScrollMotion.update(workspace, workspace.getScrollX(), workspace.getScrollY());
    }

    private static View findWorkspaceAncestor(View material) {
        View cursor = material;
        while (cursor != null) {
            Class<?> type = cursor.getClass();
            if ("com.miui.home.launcher.Workspace".equals(type.getName())
                    || "Workspace".equals(type.getSimpleName())) return cursor;
            ViewParent parent = cursor.getParent();
            cursor = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View material = materialRef.get();
        if (disposed || material == null || root == null
                || suppressedByFolderOpen || suppressedByDrag
                || !material.isAttachedToWindow() || !material.isShown()
                || material.getAlpha() <= 0f) return null;
        int hostWidth = material.getWidth();
        int hostHeight = material.getHeight();
        if (hostWidth <= 0 || hostHeight <= 0 || root.getWidth() <= 0 || root.getHeight() <= 0) {
            return null;
        }

        float localLeft = 0f;
        float localTop = 0f;
        float localRight = hostWidth;
        float localBottom = hostHeight;
        if (kind == LauncherGlassDragState.Kind.ICON) {
            LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(material);
            if (icon == null || icon.width() <= 0f || icon.height() <= 0f) return null;
            localLeft = icon.left;
            localTop = icon.top;
            localRight = icon.right;
            localBottom = icon.bottom;
        }
        float localWidth = Math.max(1f, localRight - localLeft);
        float localHeight = Math.max(1f, localBottom - localTop);
        float[] points = new float[]{
                localLeft, localTop,
                localRight, localTop,
                localLeft, localBottom,
                localRight, localBottom
        };
        Matrix materialGlobal = new Matrix();
        material.transformMatrixToGlobal(materialGlobal);
        materialGlobal.mapPoints(points);
        Matrix rootGlobal = new Matrix();
        root.transformMatrixToGlobal(rootGlobal);
        Matrix globalToRoot = new Matrix();
        if (!rootGlobal.invert(globalToRoot)) return null;
        globalToRoot.mapPoints(points);

        float left = Math.min(Math.min(points[0], points[2]), Math.min(points[4], points[6]));
        float top = Math.min(Math.min(points[1], points[3]), Math.min(points[5], points[7]));
        float right = Math.max(Math.max(points[0], points[2]), Math.max(points[4], points[6]));
        float bottom = Math.max(Math.max(points[1], points[3]), Math.max(points[5], points[7]));
        float scaleX = distance(points[0], points[1], points[2], points[3]) / localWidth;
        float scaleY = distance(points[0], points[1], points[4], points[5]) / localHeight;
        float radiusScale = Math.max(0.01f, Math.min(scaleX, scaleY));
        return LauncherGlassGeometry.resolve(
                root.getWidth(), root.getHeight(), left, top, right, bottom,
                nativeCornerRadiusPx * radiusScale);
    }

    void dispose() {
        if (disposed) return;
        resetPressInteraction(false);
        disposed = true;
        View material = materialRef.get();
        if (material != null) {
            material.removeOnAttachStateChangeListener(materialAttachListener);
            WeakReference<LauncherGlassStaticNode> ref = BY_MATERIAL.get(material);
            if (ref != null && ref.get() == this) BY_MATERIAL.remove(material);
        }
        LauncherGlassSession live = session;
        if (live != null) live.unregisterStaticNode(this);
    }

    private LauncherGlassSession ensureLiveSession() {
        if (disposed) return null;
        View material = materialRef.get();
        LauncherGlassSession current = session;
        View stableRoot = LauncherGlassSessionRegistry.resolveStableRoot(material);
        if (stableRoot == null) return null;
        if (current != null && !current.isShutdown() && current.ownsRoot(stableRoot)) return current;
        if (current != null) current.unregisterStaticNode(this);
        LauncherGlassSession replacement = LauncherGlassSessionRegistry.acquire(material, glassConfig);
        session = replacement;
        return replacement;
    }

    private static float clamp01(float value) {
        if (!Float.isFinite(value)) return 0.5f;
        return Math.max(0f, Math.min(1f, value));
    }

    private static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
''')


# --- Discover real Launcher widget/icon host instances without global View hooks. ---
write('src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java', r'''package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Adds ShortcutIcon and widget hosts to the one root-wide static Launcher glass compositor. */
final class MiuixLauncherStaticGlassHook {
    private static final String TAG = "[DC][StaticGlassHook]";
    private static final int MAX_BIND_ATTEMPTS = 8;
    private static final Map<View, View.OnAttachStateChangeListener> BOOTSTRAP_OBSERVERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private MiuixLauncherStaticGlassHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        if (!glassConfig.widgetEnabled && !glassConfig.iconEnabled) return false;
        boolean any = false;
        if (glassConfig.iconEnabled) {
            any |= installHostClass(classLoader, "com.miui.home.launcher.ShortcutIcon",
                    LauncherGlassDragState.Kind.ICON, glassConfig);
        }
        if (glassConfig.widgetEnabled) {
            any |= installHostClass(classLoader, "com.miui.home.launcher.LauncherAppWidgetHostView",
                    LauncherGlassDragState.Kind.WIDGET, glassConfig);
            any |= installHostClass(classLoader, "com.miui.home.launcher.maml.MaMlHostView",
                    LauncherGlassDragState.Kind.WIDGET, glassConfig);
        }
        installed = any;
        if (any) MainHook.log(TAG + " widget/icon static glass hooks installed");
        return any;
    }

    private static boolean installHostClass(
            ClassLoader classLoader, String className,
            LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        try {
            Class<?> type = Class.forName(className, false, classLoader);
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            if (constructors.length == 0) return false;
            for (Constructor<?> constructor : constructors) {
                HookUtil.hook(constructor, chain -> {
                    Object[] args = chain.getArgs().toArray(new Object[0]);
                    Object result = chain.proceed(args);
                    Object owner = chain.getThisObject();
                    if (owner instanceof View) observeHost((View) owner, kind, glassConfig);
                    return result;
                });
            }
            MainHook.log(TAG + " hooked " + className + " constructors=" + constructors.length);
            return true;
        } catch (ClassNotFoundException missing) {
            MainHook.log(TAG + " optional host absent " + className);
            return false;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook failed " + className + ": " + error);
            return false;
        }
    }

    private static void observeHost(
            View host, LauncherGlassDragState.Kind kind, LiquidDockConfig.Glass glassConfig) {
        if (host == null) return;
        synchronized (BOOTSTRAP_OBSERVERS) {
            if (BOOTSTRAP_OBSERVERS.containsKey(host)) return;
            View.OnAttachStateChangeListener listener = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) {
                    scheduleBind(v, kind, glassConfig, 0);
                }
                @Override public void onViewDetachedFromWindow(View v) {}
            };
            BOOTSTRAP_OBSERVERS.put(host, listener);
            host.addOnAttachStateChangeListener(listener);
        }
        if (host.isAttachedToWindow()) scheduleBind(host, kind, glassConfig, 0);
    }

    private static void scheduleBind(
            View host, LauncherGlassDragState.Kind kind,
            LiquidDockConfig.Glass glassConfig, int attempt) {
        if (host == null || !host.isAttachedToWindow() || attempt > MAX_BIND_ATTEMPTS) return;
        if (host.getWidth() <= 0 || host.getHeight() <= 0) {
            host.postOnAnimation(() -> scheduleBind(host, kind, glassConfig, attempt + 1));
            return;
        }
        LauncherGlassStaticNode node = LauncherGlassStaticNode.find(host);
        if (node == null || node.kind() != kind) {
            float radius = resolveCornerRadius(host, kind);
            node = LauncherGlassStaticNode.attachToMaterial(host, kind, radius, glassConfig);
        } else {
            node.requestLifecycleRefresh();
        }
        if (node != null) removeBootstrapObserver(host);
    }

    private static void removeBootstrapObserver(View host) {
        View.OnAttachStateChangeListener listener;
        synchronized (BOOTSTRAP_OBSERVERS) {
            listener = BOOTSTRAP_OBSERVERS.remove(host);
        }
        if (listener != null) host.removeOnAttachStateChangeListener(listener);
    }

    private static float resolveCornerRadius(View host, LauncherGlassDragState.Kind kind) {
        if (kind == LauncherGlassDragState.Kind.ICON) {
            LauncherGlassIconGeometry.Bounds bounds = LauncherGlassIconGeometry.resolve(host);
            float min = bounds != null
                    ? Math.min(bounds.width(), bounds.height())
                    : Math.min(Math.max(1, host.getWidth()), Math.max(1, host.getHeight()));
            return Math.max(0f, min * 0.22f);
        }
        float nativeRadius = readCornerRadius(host);
        if (Float.isFinite(nativeRadius) && nativeRadius > 0f) return nativeRadius;
        float min = Math.min(Math.max(1, host.getWidth()), Math.max(1, host.getHeight()));
        return Math.max(0f, min * 0.08f);
    }

    private static float readCornerRadius(View host) {
        if (host == null) return Float.NaN;
        try {
            Field field = findField(host.getClass(), "mCornerRadius");
            field.setAccessible(true);
            Object value = field.get(host);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Throwable ignored) {}
        Drawable background = host.getBackground();
        if (background instanceof GradientDrawable) {
            return Math.max(0f, ((GradientDrawable) background).getCornerRadius());
        }
        return Float.NaN;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }
}
''')


# --- Session producer-space correction ---
replace_once('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java',
             'import android.graphics.Point;\n',
             'import android.graphics.Point;\nimport android.graphics.Rect;\n')

producer_block = r'''    private static final class ProducerGeometry {
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final int configRotation;
        final SurfaceControl rootSurface;
        final int insetLeft;
        final int insetTop;
        final int insetRight;
        final int insetBottom;
        final LauncherGlassSurfaceContentRect contentRect;

        ProducerGeometry(
                int surfaceWidth, int surfaceHeight, int bufferWidth, int bufferHeight,
                int configRotation, SurfaceControl rootSurface,
                int insetLeft, int insetTop, int insetRight, int insetBottom) {
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.configRotation = configRotation;
            this.rootSurface = rootSurface;
            this.insetLeft = insetLeft;
            this.insetTop = insetTop;
            this.insetRight = insetRight;
            this.insetBottom = insetBottom;
            contentRect = LauncherGlassSurfaceContentRect.resolve(
                    surfaceWidth, surfaceHeight,
                    insetLeft, insetTop, insetRight, insetBottom);
        }
    }

'''
replace_section('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java',
                '    private static final class ProducerGeometry {',
                '    private static final class NodeState {',
                producer_block)

replace_once('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java',
             '    private volatile int boundBufferHeight;\n',
             '    private volatile int boundBufferHeight;\n'
             '    private volatile LauncherGlassSurfaceContentRect contentRect =\n'
             '            LauncherGlassSurfaceContentRect.full();\n')

refresh_method = r'''    private boolean refreshProducerGeometryOnUi(View root) {
        ProducerGeometry geometry = readSurfaceGeometry(root);
        if (geometry == null) return false;
        int nextRotation = geometry.configRotation;
        LauncherGlassSurfaceContentRect nextContentRect = geometry.contentRect;
        boolean changed = nextRotation != configRotation
                || geometry.bufferWidth != boundBufferWidth
                || geometry.bufferHeight != boundBufferHeight
                || !nextContentRect.sameAs(contentRect);
        configRotation = nextRotation;
        if (changed) {
            boundBufferWidth = geometry.bufferWidth;
            boundBufferHeight = geometry.bufferHeight;
            contentRect = nextContentRect;
            MainHook.log(TAG + " producer geometry surface="
                    + geometry.surfaceWidth + "x" + geometry.surfaceHeight
                    + " buffer=" + geometry.bufferWidth + "x" + geometry.bufferHeight
                    + " insets=" + geometry.insetLeft + "," + geometry.insetTop
                    + "," + geometry.insetRight + "," + geometry.insetBottom);
            SurfaceTexture input = inputSurfaceTexture;
            if (input != null && geometry.bufferWidth > 0 && geometry.bufferHeight > 0) {
                postRender(() -> {
                    if (!shuttingDown && input == inputSurfaceTexture) {
                        input.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                    }
                }, null);
            }
        }
        Miuix307PassBlurBridge.Binding current = binding;
        if (current != null && (!current.rootSurface.isValid()
                || !isSameSurface(current.rootSurface, geometry.rootSurface))) {
            rebindProducer();
            return true;
        }
        if (changed && current != null && current.bound) {
            Miuix307PassBlurBridge.requestSingleUpdate(current, root);
        }
        return changed;
    }

'''
replace_section('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java',
                '    private boolean refreshProducerGeometryOnUi(View root) {',
                '    private boolean postRender(Runnable action, Runnable rejected) {',
                refresh_method)

replace_once('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java',
             '        boundBufferHeight = geometry.bufferHeight;\n'
             '        MainHook.log(TAG + " shared PassBlur producer bound " + debugLabel()\n'
             '                + " surface=" + next.rootName + " buffer="\n'
             '                + geometry.bufferWidth + "x" + geometry.bufferHeight);',
             '        boundBufferHeight = geometry.bufferHeight;\n'
             '        contentRect = geometry.contentRect;\n'
             '        MainHook.log(TAG + " shared PassBlur producer bound " + debugLabel()\n'
             '                + " surface=" + next.rootName + " buffer="\n'
             '                + geometry.bufferWidth + "x" + geometry.bufferHeight\n'
             '                + " insets=" + geometry.insetLeft + "," + geometry.insetTop\n'
             '                + "," + geometry.insetRight + "," + geometry.insetBottom);')

replace_once('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java',
             '        GLES20.glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"), 0f, 0f, 1f, 1f);',
             '        LauncherGlassSurfaceContentRect contentRect = this.contentRect;\n'
             '        GLES20.glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"),\n'
             '                contentRect.left, contentRect.bottom, contentRect.width, contentRect.height);')

read_geometry = r'''    private ProducerGeometry readSurfaceGeometry(View root) {
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
            Method method = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            method.setAccessible(true);
            Object value = method.invoke(viewRoot);
            SurfaceControl surfaceControl = value instanceof SurfaceControl
                    ? (SurfaceControl) value : null;
            return new ProducerGeometry(surfaceWidth, surfaceHeight,
                    bufferWidth, bufferHeight, rotation, surfaceControl,
                    surfaceInsets.left, surfaceInsets.top,
                    surfaceInsets.right, surfaceInsets.bottom);
        } catch (Throwable ignored) {
            return null;
        }
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

'''
replace_section('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java',
                '    private ProducerGeometry readSurfaceGeometry(View root) {',
                '    private static int readConfigRotation(View view) {',
                read_geometry)


# --- Folder is explicitly one kind of the generic static node. ---
replace_once('src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java',
             '        LauncherGlassStaticNode sink = LauncherGlassStaticNode.attachToMaterial(\n'
             '                material, radius, glassConfig);',
             '        LauncherGlassStaticNode sink = LauncherGlassStaticNode.attachToMaterial(\n'
             '                material, LauncherGlassDragState.Kind.FOLDER, radius, glassConfig);')


# --- Widget/icon drag static-node suppression + non-folder install gate. ---
replace_once('src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java',
             '        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled\n'
             '                || !runtimeConfig.glass.folderEnabled) {\n'
             '            return false;\n'
             '        }\n'
             '        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;',
             '        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled) {\n'
             '            return false;\n'
             '        }\n'
             '        boolean anyStaticGlass = runtimeConfig.glass.folderEnabled || runtimeConfig.glass.widgetEnabled\n'
             '                || runtimeConfig.glass.iconEnabled;\n'
             '        if (!anyStaticGlass) return false;\n'
             '        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;')

replace_once('src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java',
             '        final View folderMaterial;\n\n'
             '        Metadata(LauncherGlassDragState.Kind kind, View radiusSource, View folderMaterial) {\n'
             '            this.kind = kind != null ? kind : LauncherGlassDragState.Kind.ICON;\n'
             '            this.radiusSource = radiusSource;\n'
             '            this.folderMaterial = folderMaterial;\n'
             '        }',
             '        final View staticHost;\n\n'
             '        Metadata(LauncherGlassDragState.Kind kind, View radiusSource, View staticHost) {\n'
             '            this.kind = kind != null ? kind : LauncherGlassDragState.Kind.ICON;\n'
             '            this.radiusSource = radiusSource;\n'
             '            this.staticHost = staticHost;\n'
             '        }')

replace_once('src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java',
             '        LauncherGlassStaticNode staticSink = metadata.folderMaterial != null\n'
             '                ? LauncherGlassStaticNode.find(metadata.folderMaterial) : null;',
             '        LauncherGlassStaticNode staticSink = metadata.staticHost != null\n'
             '                ? LauncherGlassStaticNode.find(metadata.staticHost) : null;')

classify_view = r'''        if (value instanceof View) {
            View view = (View) value;
            String viewName = view.getClass().getName();
            if (viewName.endsWith(".FolderIcon")) {
                View material = readFolderMaterial(view);
                return new Metadata(LauncherGlassDragState.Kind.FOLDER,
                        material != null ? material : view, material);
            }
            String lowerView = viewName.toLowerCase(Locale.ROOT);
            if (lowerView.contains("appwidgethostview") || lowerView.contains("widget")
                    || lowerView.contains("gadget") || lowerView.contains("mamlhostview")) {
                return new Metadata(LauncherGlassDragState.Kind.WIDGET, view, view);
            }
            if (viewName.endsWith(".ShortcutIcon") || viewName.endsWith(".ItemIcon")) {
                return new Metadata(LauncherGlassDragState.Kind.ICON, view, view);
            }
            Object tag = view.getTag();
            if (tag != null && tag != value) {
                Metadata tagged = classifyMetadata(tag);
                if (tagged != null) return tagged;
            }
        }

'''
replace_section('src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java',
                '        if (value instanceof View) {',
                '        String name = value.getClass().getName().toLowerCase(Locale.ROOT);',
                classify_view)


# --- Config schema/runtime ---
replace_once('src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java',
             '        public static final ConfigKey<Boolean> FOLDER_GLASS = bool(\n'
             '                "liquid_folder_glass", true, true, true, ConfigKey.ExportMode.ALWAYS);\n'
             '        public static final ConfigKey<Integer> FOLDER_CORNER_RADIUS = integer(',
             '        public static final ConfigKey<Boolean> FOLDER_GLASS = bool(\n'
             '                "liquid_folder_glass", true, true, true, ConfigKey.ExportMode.ALWAYS);\n'
             '        public static final ConfigKey<Boolean> WIDGET_GLASS = bool(\n'
             '                "liquid_widget_glass", true, true, true, ConfigKey.ExportMode.ALWAYS);\n'
             '        public static final ConfigKey<Boolean> ICON_GLASS = bool(\n'
             '                "liquid_icon_glass", true, true, true, ConfigKey.ExportMode.ALWAYS);\n'
             '        public static final ConfigKey<Integer> FOLDER_CORNER_RADIUS = integer(')

replace_once('src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java',
             '        add(keys, Glass.ENABLED, Glass.FOLDER_GLASS, Glass.FOLDER_CORNER_RADIUS,\n',
             '        add(keys, Glass.ENABLED, Glass.FOLDER_GLASS, Glass.WIDGET_GLASS, Glass.ICON_GLASS,\n'
             '                Glass.FOLDER_CORNER_RADIUS,\n')

replace_once('src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
             '        final boolean enabled, folderEnabled;\n',
             '        final boolean enabled, folderEnabled, widgetEnabled, iconEnabled;\n')
replace_once('src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java',
             '            folderEnabled = c.b(ConfigSchema.Glass.FOLDER_GLASS.name(),\n'
             '                    ConfigSchema.Glass.FOLDER_GLASS.runtimeFallback());\n'
             '            folderCornerRadiusDp = c.f(ConfigSchema.Glass.FOLDER_CORNER_RADIUS.name(),',
             '            folderEnabled = c.b(ConfigSchema.Glass.FOLDER_GLASS.name(),\n'
             '                    ConfigSchema.Glass.FOLDER_GLASS.runtimeFallback());\n'
             '            widgetEnabled = c.b(ConfigSchema.Glass.WIDGET_GLASS.name(),\n'
             '                    ConfigSchema.Glass.WIDGET_GLASS.runtimeFallback());\n'
             '            iconEnabled = c.b(ConfigSchema.Glass.ICON_GLASS.name(),\n'
             '                    ConfigSchema.Glass.ICON_GLASS.runtimeFallback());\n'
             '            folderCornerRadiusDp = c.f(ConfigSchema.Glass.FOLDER_CORNER_RADIUS.name(),')


# --- Module install ---
replace_once('src/main/java/com/hellovoid/liquiddock/ModuleMain.java',
             '        MiuixFolderGlassHook.install(classLoader, runtimeConfig);\n',
             '        MiuixFolderGlassHook.install(classLoader, runtimeConfig);\n'
             '        MiuixLauncherStaticGlassHook.install(classLoader, runtimeConfig);\n')


# --- Compose UI ---
replace_once('src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt',
             '        IntSetting(prefs, folderCornerRadiusSpec, masterEnabled && liquidGlass && folderGlass)\n'
             '        ArrowPreference(',
             '        IntSetting(prefs, folderCornerRadiusSpec, masterEnabled && liquidGlass && folderGlass)\n'
             '        BooleanSetting(\n'
             '            prefs, ConfigSchema.Glass.WIDGET_GLASS,\n'
             '            "小部件玻璃",\n'
             '            "在小部件后方使用共享桌面玻璃层；透明区域可显示玻璃",\n'
             '            masterEnabled && liquidGlass,\n'
             '        )\n'
             '        BooleanSetting(\n'
             '            prefs, ConfigSchema.Glass.ICON_GLASS,\n'
             '            "图标玻璃",\n'
             '            "只在图标图形区域下方绘制玻璃，不覆盖文字；透明图标可透出玻璃",\n'
             '            masterEnabled && liquidGlass,\n'
             '        )\n'
             '        ArrowPreference(')


# --- Legacy UI ---
replace_once('src/main/res/xml/preferences.xml',
             '        <com.hellovoid.liquiddock.SeekBarPreference\n'
             '            android:key="liquid_folder_corner_radius"\n'
             '            android:title="Folder Corner Radius"\n'
             '            android:summary="%d dp (0 = Auto)"\n'
             '            android:defaultValue="0"\n'
             '            app:min="0"\n'
             '            app:max="96"\n'
             '            android:dependency="liquid_folder_glass" />\n',
             '        <com.hellovoid.liquiddock.SeekBarPreference\n'
             '            android:key="liquid_folder_corner_radius"\n'
             '            android:title="Folder Corner Radius"\n'
             '            android:summary="%d dp (0 = Auto)"\n'
             '            android:defaultValue="0"\n'
             '            app:min="0"\n'
             '            app:max="96"\n'
             '            android:dependency="liquid_folder_glass" />\n\n'
             '        <SwitchPreference\n'
             '            android:key="liquid_widget_glass"\n'
             '            android:title="Widget Liquid Glass"\n'
             '            android:summary="Draw shared liquid glass behind transparent widget regions"\n'
             '            android:defaultValue="true"\n'
             '            android:dependency="liquid_glass" />\n\n'
             '        <SwitchPreference\n'
             '            android:key="liquid_icon_glass"\n'
             '            android:title="Icon Liquid Glass"\n'
             '            android:summary="Draw glass only under the icon graphic; labels remain unchanged"\n'
             '            android:defaultValue="true"\n'
             '            android:dependency="liquid_glass" />\n')


# --- Export-default regression ---
replace_once('src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java',
             '        assertEquals(130, exported.size());',
             '        assertEquals(132, exported.size());')
replace_once('src/test/java/com/hellovoid/liquiddock/config/ConfigCodecTest.java',
             '        assertEquals(Boolean.TRUE, exported.get("liquid_folder_glass"));\n'
             '        assertEquals(0, exported.get("liquid_folder_corner_radius"));',
             '        assertEquals(Boolean.TRUE, exported.get("liquid_folder_glass"));\n'
             '        assertEquals(Boolean.TRUE, exported.get("liquid_widget_glass"));\n'
             '        assertEquals(Boolean.TRUE, exported.get("liquid_icon_glass"));\n'
             '        assertEquals(0, exported.get("liquid_folder_corner_radius"));')


# --- Sanity gates: fail the migration rather than commit a partial architecture. ---
checks = {
    'src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java': [
        'mWindowAttributes', 'surfaceInsets', 'contentRect.left', 'contentRect.bottom'],
    'src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java': [
        'LauncherGlassDragState.Kind', 'LauncherGlassIconGeometry'],
    'src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java': [
        'com.miui.home.launcher.ShortcutIcon', 'LauncherAppWidgetHostView', 'MaMlHostView'],
    'src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java': [
        'liquid_widget_glass', 'liquid_icon_glass'],
}
for path, needles in checks.items():
    text = read(path)
    for needle in needles:
        if needle not in text:
            raise RuntimeError(f'{path}: missing postcondition {needle}')

session = read('src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java')
marker = 'glUniform4f(requireUniform(normalizeProgram, "uBackdropRect"), 0f, 0f, 1f, 1f)'
if marker in session:
    raise RuntimeError('root normalization still hardcodes whole OES buffer')

hook = read('src/main/java/com/hellovoid/liquiddock/MiuixLauncherStaticGlassHook.java')
for forbidden in ['TextureView', 'new Surface(', 'View.class.getDeclaredMethod("onAttachedToWindow"']:
    if forbidden in hook:
        raise RuntimeError(f'static host hook violates one-surface contract: {forbidden}')

print('launcher static glass migration applied successfully')
