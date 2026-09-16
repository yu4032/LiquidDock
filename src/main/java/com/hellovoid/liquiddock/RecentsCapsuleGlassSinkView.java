package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;

/**
 * Transparent Prismal output hosted inside one native Recents capsule without taking measured
 * layout space. The native capsule keeps its original parent/LayoutParams and interaction tree.
 */
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

    static RecentsCapsuleGlassSinkView attachInsideTarget(
            View target,
            RecentsCapsuleGlassSession session,
            RecentsCapsuleGlassSession.Target targetId) {
        if (!(target instanceof ViewGroup) || session == null || targetId == null) return null;
        ViewGroup targetGroup = (ViewGroup) target;
        RecentsCapsuleGlassSinkView sink = new RecentsCapsuleGlassSinkView(
                target.getContext(), target, session, targetId);
        // Zero measured size means this child cannot change LinearLayout total length, gravity,
        // weights or the positions of the vendor content. syncFromTarget() applies the visual
        // TextureView frame only after the vendor layout pass has finished.
        targetGroup.addView(sink, 0, new ViewGroup.LayoutParams(0, 0));
        sink.syncFromTarget();
        return sink;
    }

    boolean syncFromTarget() {
        View target = targetRef.get();
        if (disposed || target == null || getParent() != target) return false;
        int width = Math.max(1, target.getWidth());
        int height = Math.max(1, target.getHeight());
        boolean changed = getLeft() != 0 || getTop() != 0
                || getRight() != width || getBottom() != height;
        if (changed) {
            // Keep LayoutParams at 0x0. Direct layout changes only the final child frame and does
            // not feed back into the vendor measure/layout calculation.
            layout(0, 0, width, height);
        }
        float desiredAlpha = presented ? 1f : 0f;
        if (getAlpha() != desiredAlpha) {
            setAlpha(desiredAlpha);
            changed = true;
        }
        int desiredVisibility = target.getVisibility();
        if (getVisibility() != desiredVisibility) {
            setVisibility(desiredVisibility);
            changed = true;
        }
        return changed;
    }

    LauncherGlassGeometry.Snapshot captureGeometry(View root) {
        View target = targetRef.get();
        if (disposed || root == null || target == null
                || target.getVisibility() != View.VISIBLE || !target.isShown()
                || target.getWidth() <= 0 || target.getHeight() <= 0) return null;
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
}
