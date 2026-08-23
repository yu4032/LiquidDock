package com.hellovoid.liquiddock;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** One transparent static Launcher glass output for an entire stable Launcher root. */
final class LauncherGlassStaticLayer extends TextureView implements TextureView.SurfaceTextureListener {
    private static final WeakHashMap<View, LauncherGlassStaticLayer> BY_ROOT = new WeakHashMap<>();

    private final WeakReference<View> rootRef;
    private final LauncherGlassSession session;
    private final Handler mainHandler;
    private Surface outputSurface;
    private boolean disposed;
    private final View.OnAttachStateChangeListener rootAttachListener;

    private LauncherGlassStaticLayer(Context context, View root, LauncherGlassSession session) {
        super(context);
        rootRef = new WeakReference<>(root);
        this.session = session;
        mainHandler = new Handler(context.getMainLooper());
        setOpaque(false);
        setVisibility(View.INVISIBLE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setSurfaceTextureListener(this);
        rootAttachListener = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}

            @Override public void onViewDetachedFromWindow(View v) {
                mainHandler.post(() -> {
                    View stableRoot = rootRef.get();
                    if (stableRoot == v && !v.isAttachedToWindow()) {
                        forget(stableRoot, LauncherGlassStaticLayer.this);
                    }
                });
            }
        };
        root.addOnAttachStateChangeListener(rootAttachListener);
    }

    static synchronized LauncherGlassStaticLayer acquire(View root, LauncherGlassSession session) {
        if (root == null || session == null || !(root instanceof ViewGroup)
                || !root.isAttachedToWindow()) return null;
        LauncherGlassStaticLayer existing = BY_ROOT.get(root);
        if (existing != null && !existing.disposed && existing.getParent() == root) return existing;
        ViewGroup rootGroup = (ViewGroup) root;
        LauncherGlassStaticLayer layer = new LauncherGlassStaticLayer(root.getContext(), root, session);
        rootGroup.addView(layer, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        BY_ROOT.put(root, layer);
        MainHook.log("[DC][LauncherGlass] shared static root layer attached root="
                + root.getClass().getSimpleName());
        return layer;
    }

    private static synchronized void forget(View root, LauncherGlassStaticLayer layer) {
        if (root != null && BY_ROOT.get(root) == layer) BY_ROOT.remove(root);
    }

    void setSceneVisible(boolean visible) {
        if (disposed) return;
        int target = visible ? View.VISIBLE : View.INVISIBLE;
        if (getVisibility() != target) setVisibility(target);
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        View root = rootRef.get();
        if (root != null) {
            root.removeOnAttachStateChangeListener(rootAttachListener);
            forget(root, this);
        }
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
    }

    @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
        if (disposed || texture == null) return;
        Surface next = new Surface(texture);
        Surface old = outputSurface;
        outputSurface = next;
        if (old != null) session.detachStaticOutput(old);
        session.attachStaticOutput(next, Math.max(1, width), Math.max(1, height));
    }

    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        if (!disposed) session.resizeStaticOutput(Math.max(1, width), Math.max(1, height));
    }

    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        Surface current = outputSurface;
        outputSurface = null;
        if (current != null) session.detachStaticOutput(current);
        return true;
    }

    @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
}
