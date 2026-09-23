package com.hellovoid.liquiddock;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Output-only glass layer inserted behind SystemUI HandleMenu's native windowing controls. */
final class SystemUiHandleMenuGlassSinkView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";
    private static final String CORNER_RADIUS_RESOURCE = "desktop_mode_handle_menu_corner_radius";

    private final WeakReference<ViewGroup> hostRef;
    private final WeakReference<View> targetRef;
    private final SystemUiHandleMenuGlassSession session;
    private Surface outputSurface;
    private boolean presented;
    private boolean disposed;

    private SystemUiHandleMenuGlassSinkView(
            Context context,
            ViewGroup host,
            View target,
            SystemUiHandleMenuGlassSession session) {
        super(context);
        hostRef = new WeakReference<>(host);
        targetRef = new WeakReference<>(target);
        this.session = session;
        setOpaque(false);
        setAlpha(0f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    static SystemUiHandleMenuGlassSinkView attachInsideHost(
            ViewGroup host, View target, SystemUiHandleMenuGlassSession session) {
        if (host == null || target == null || session == null) return null;
        SystemUiHandleMenuGlassSinkView sink =
                new SystemUiHandleMenuGlassSinkView(host.getContext(), host, target, session);
        host.addView(sink, 0, new ViewGroup.LayoutParams(0, 0));
        sink.syncFromTarget();
        return sink;
    }

    boolean syncFromTarget() {
        ViewGroup host = hostRef.get();
        View target = targetRef.get();
        if (disposed || host == null || target == null || getParent() != host) return false;
        Rect targetRect = new Rect();
        Rect hostRect = new Rect();
        if (!target.getGlobalVisibleRect(targetRect) || !host.getGlobalVisibleRect(hostRect)
                || targetRect.width() <= 0 || targetRect.height() <= 0) return false;
        int left = targetRect.left - hostRect.left;
        int top = targetRect.top - hostRect.top;
        int right = left + targetRect.width();
        int bottom = top + targetRect.height();
        boolean changed = getLeft() != left || getTop() != top
                || getRight() != right || getBottom() != bottom;
        if (changed) {
            // Keep layout params at 0x0 so this child never participates in vendor measurement.
            layout(left, top, right, bottom);
        }
        float desiredAlpha = presented ? 1f : 0f;
        if (getAlpha() != desiredAlpha) {
            setAlpha(desiredAlpha);
            changed = true;
        }
        if (getVisibility() != target.getVisibility()) {
            setVisibility(target.getVisibility());
            changed = true;
        }
        return changed;
    }

    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View target = targetRef.get();
        if (disposed || root == null || target == null
                || target.getVisibility() != View.VISIBLE || !target.isShown()
                || target.getWidth() <= 0 || target.getHeight() <= 0
                || root.getWidth() <= 0 || root.getHeight() <= 0) return null;
        syncFromTarget();

        Rect targetRect = new Rect();
        if (!target.getGlobalVisibleRect(targetRect)
                || targetRect.width() <= 0 || targetRect.height() <= 0) return null;
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        LauncherGlassScreenSpace.Bounds bounds = LauncherGlassScreenSpace.relativeToRoot(
                rootLocation[0], rootLocation[1],
                targetRect.left, targetRect.top, targetRect.right, targetRect.bottom);

        float visualScale = Math.min(
                targetRect.width() / (float) Math.max(1, target.getWidth()),
                targetRect.height() / (float) Math.max(1, target.getHeight()));
        float radius = resolveCornerRadius(target) * Math.max(0.01f, visualScale);
        return LauncherGlassGeometry.resolve(
                root.getWidth(), root.getHeight(),
                bounds.left, bounds.top, bounds.right, bounds.bottom, radius);
    }

    private static float resolveCornerRadius(View target) {
        Resources resources = target.getResources();
        if (resources != null) {
            int id = resources.getIdentifier(
                    CORNER_RADIUS_RESOURCE, "dimen", SYSTEM_UI_PACKAGE);
            if (id != 0) {
                try { return Math.max(0f, resources.getDimensionPixelSize(id)); }
                catch (Throwable ignored) {}
            }
        }
        return Math.min(target.getWidth(), target.getHeight()) * 0.5f;
    }

    void reveal() {
        if (disposed) return;
        presented = true;
        syncFromTarget();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = new Surface(texture);
        Surface previous = outputSurface;
        outputSurface = next;
        if (previous != null) session.detachOutput(previous);
        session.attachOutput(next, Math.max(1, width), Math.max(1, height));
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture texture, int width, int height) {
        if (!disposed) session.resizeOutput(Math.max(1, width), Math.max(1, height));
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(current);
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
