package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/** Transparent Prismal output kept as a sibling immediately behind one native Recents capsule. */
final class RecentsCapsuleGlassSinkView extends TextureView
        implements TextureView.SurfaceTextureListener {
    private final WeakReference<View> targetRef;
    private final RecentsCapsuleGlassSession session;
    private final RecentsCapsuleGlassSession.Target targetId;
    private Surface outputSurface;
    private boolean presented;
    private boolean disposed;

    private RecentsCapsuleGlassSinkView(
            Context context,
            View target,
            RecentsCapsuleGlassSession session,
            RecentsCapsuleGlassSession.Target targetId) {
        super(context);
        targetRef = new WeakReference<>(target);
        this.session = session;
        this.targetId = targetId;
        setOpaque(false);
        setAlpha(0f);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
    }

    static RecentsCapsuleGlassSinkView attachBehindTarget(
            View target,
            RecentsCapsuleGlassSession session,
            RecentsCapsuleGlassSession.Target targetId) {
        if (target == null || session == null || targetId == null
                || !(target.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) target.getParent();
        RecentsCapsuleGlassSinkView sink = new RecentsCapsuleGlassSinkView(
                target.getContext(), target, session, targetId);
        int index = Math.max(0, parent.indexOfChild(target));
        parent.addView(sink, index, new ViewGroup.LayoutParams(
                Math.max(1, target.getWidth()), Math.max(1, target.getHeight())));
        sink.syncFromTarget();
        return sink;
    }

    boolean syncFromTarget() {
        View target = targetRef.get();
        if (disposed || target == null || target.getParent() != getParent()) return false;
        boolean changed = false;
        int width = Math.max(1, target.getWidth());
        int height = Math.max(1, target.getHeight());
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            setLayoutParams(lp);
            changed = true;
        }
        changed |= setFloatIfChanged(getX(), target.getX(), this::setX);
        changed |= setFloatIfChanged(getY(), target.getY(), this::setY);
        if (getPivotX() != target.getPivotX()) { setPivotX(target.getPivotX()); changed = true; }
        if (getPivotY() != target.getPivotY()) { setPivotY(target.getPivotY()); changed = true; }
        if (getScaleX() != target.getScaleX()) { setScaleX(target.getScaleX()); changed = true; }
        if (getScaleY() != target.getScaleY()) { setScaleY(target.getScaleY()); changed = true; }
        if (getRotation() != target.getRotation()) { setRotation(target.getRotation()); changed = true; }
        float desiredAlpha = presented ? target.getAlpha() : 0f;
        if (getAlpha() != desiredAlpha) { setAlpha(desiredAlpha); changed = true; }
        int desiredVisibility = target.getVisibility();
        if (getVisibility() != desiredVisibility) { setVisibility(desiredVisibility); changed = true; }
        return changed;
    }

    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View target = targetRef.get();
        if (disposed || root == null || target == null
                || target.getVisibility() != View.VISIBLE || !target.isShown()
                || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
        syncFromTarget();
        Rect sinkRect = new Rect();
        if (!getGlobalVisibleRect(sinkRect) || sinkRect.width() <= 0 || sinkRect.height() <= 0) {
            return null;
        }
        int[] rootLocation = new int[2];
        root.getLocationOnScreen(rootLocation);
        LauncherGlassScreenSpace.Bounds bounds = LauncherGlassScreenSpace.relativeToRoot(
                rootLocation[0], rootLocation[1],
                sinkRect.left, sinkRect.top, sinkRect.right, sinkRect.bottom);
        float visualScale = Math.min(
                sinkRect.width() / (float) Math.max(1, getWidth()),
                sinkRect.height() / (float) Math.max(1, getHeight()));
        float radius = Math.min(target.getWidth(), target.getHeight()) * 0.5f
                * Math.max(0.01f, visualScale);
        return LauncherGlassGeometry.resolve(
                root.getWidth(), root.getHeight(),
                bounds.left, bounds.top, bounds.right, bounds.bottom, radius);
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
        if (current != null) session.detachOutput(targetId, current);
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachOutput(targetId, old);
        session.attachOutput(targetId, next, Math.max(1, width), Math.max(1, height));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed) session.resizeOutput(targetId, Math.max(1, width), Math.max(1, height));
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachOutput(targetId, current);
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}

    private interface FloatSetter { void set(float value); }

    private static boolean setFloatIfChanged(float current, float next, FloatSetter setter) {
        if (Math.abs(current - next) < 0.01f) return false;
        setter.set(next);
        return true;
    }
}
