package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** One continuous Dock-domain batch: Dock body + Dock-local item geometry + one output swap. */
final class DockGlassCompositor {
    static final int ONE_OUTPUT_SWAP = 1;

    private final WeakReference<View> dockRootRef;
    private final ArrayList<DockGlassItemNode> cachedItems = new ArrayList<>();
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);
    private volatile DockGlassSceneSnapshot latestScene = DockGlassSceneSnapshot.EMPTY;

    private long seenRegistryRevision = -1L;
    private long lastUiFingerprint = Long.MIN_VALUE;
    private int lastFramebufferWidth = -1;
    private int lastFramebufferHeight = -1;
    private float lastSampleInsetLeft = Float.NaN;
    private float lastSampleInsetTop = Float.NaN;
    private float lastScaleX = Float.NaN;
    private float lastScaleY = Float.NaN;

    DockGlassCompositor(View dockRoot) {
        dockRootRef = new WeakReference<>(dockRoot);
    }

    void setIconStyle(GlassComponentStyle style) {
        GlassComponentStyle next = style != null
                ? style : new GlassComponentStyle(false, 0f, 0f);
        GlassComponentStyle old = iconStyle;
        iconStyle = next;
        if (old.enabled != next.enabled
                || Float.compare(old.sizeOffsetDp, next.sizeOffsetDp) != 0
                || Float.compare(old.cornerRadiusDp, next.cornerRadiusDp) != 0) {
            invalidateUiScene();
        }
        if (!next.enabled) latestScene = DockGlassSceneSnapshot.EMPTY;
    }

    private void invalidateUiScene() {
        seenRegistryRevision = -1L;
        lastUiFingerprint = Long.MIN_VALUE;
        lastFramebufferWidth = -1;
        lastFramebufferHeight = -1;
    }

    /**
     * UI-thread only. Polls a cheap Dock-local fingerprint every pre-draw and rebuilds the
     * immutable item scene only when an item/layout/style actually changed. Whole-Dock movement is
     * intentionally excluded because the independent Dock TextureView already follows that layer.
     */
    void refreshUiSceneIfNeeded(
            int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop,
            float scaleX, float scaleY) {
        View dockRoot = dockRootRef.get();
        GlassComponentStyle style = iconStyle;
        if (dockRoot == null || style == null || !style.enabled
                || framebufferWidth <= 0 || framebufferHeight <= 0) {
            cachedItems.clear();
            latestScene = DockGlassSceneSnapshot.EMPTY;
            invalidateUiScene();
            return;
        }

        long registryRevision = DockGlassItemRegistry.revision();
        boolean deadCandidate = false;
        for (DockGlassItemNode item : cachedItems) {
            if (item.view() == null || !item.belongsTo(dockRoot)) {
                deadCandidate = true;
                break;
            }
        }
        if (registryRevision != seenRegistryRevision || deadCandidate) {
            cachedItems.clear();
            for (View view : DockGlassItemRegistry.snapshotForRoot(dockRoot.getRootView())) {
                DockGlassItemNode item = new DockGlassItemNode(
                        view, LauncherGlassNodeKind.ICON, style);
                if (item.belongsTo(dockRoot)) cachedItems.add(item);
            }
            seenRegistryRevision = registryRevision;
            lastUiFingerprint = Long.MIN_VALUE;
        }

        long fingerprint = 0xcbf29ce484222325L;
        fingerprint = mix(fingerprint, cachedItems.size());
        for (DockGlassItemNode item : cachedItems) {
            fingerprint = mix(fingerprint, item.uiFingerprint(dockRoot));
        }
        boolean mappingChanged = lastFramebufferWidth != framebufferWidth
                || lastFramebufferHeight != framebufferHeight
                || Float.compare(lastSampleInsetLeft, sampleInsetLeft) != 0
                || Float.compare(lastSampleInsetTop, sampleInsetTop) != 0
                || Float.compare(lastScaleX, scaleX) != 0
                || Float.compare(lastScaleY, scaleY) != 0;
        if (!mappingChanged && fingerprint == lastUiFingerprint) return;

        Matrix rootGlobal = new Matrix();
        dockRoot.transformMatrixToGlobal(rootGlobal);
        Matrix rootInverse = new Matrix();
        if (!rootGlobal.invert(rootInverse)) {
            latestScene = DockGlassSceneSnapshot.EMPTY;
            return;
        }

        ArrayList<LauncherGlassGeometry.Snapshot> geometry = new ArrayList<>(cachedItems.size());
        for (DockGlassItemNode item : cachedItems) {
            LauncherGlassGeometry.Snapshot snapshot = item.capture(
                    dockRoot, rootInverse, framebufferWidth, framebufferHeight,
                    sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
            if (snapshot != null) geometry.add(snapshot);
        }
        latestScene = new DockGlassSceneSnapshot(
                geometry.toArray(new LauncherGlassGeometry.Snapshot[0]));
        lastUiFingerprint = fingerprint;
        lastFramebufferWidth = framebufferWidth;
        lastFramebufferHeight = framebufferHeight;
        lastSampleInsetLeft = sampleInsetLeft;
        lastSampleInsetTop = sampleInsetTop;
        lastScaleX = scaleX;
        lastScaleY = scaleY;
    }

    DockGlassSceneSnapshot latestScene() {
        return latestScene;
    }

    private static long mix(long hash, long value) {
        hash ^= value;
        return hash * 0x100000001b3L;
    }

    void beginGlassFrame(PrismalRenderer renderer) {
        renderer.beginGlassFrame();
    }

    void drawDockBody(PrismalRenderer renderer, PrismalGeometry geometry, PrismalParams params) {
        renderer.drawGlass(geometry, params);
    }

    void drawItem(PrismalRenderer renderer, LauncherGlassGeometry.Snapshot geometry,
                  PrismalParams params, int framebufferWidth, int framebufferHeight) {
        if (geometry == null) return;
        renderer.drawGlass(new PrismalGeometry(
                framebufferWidth, framebufferHeight,
                geometry.centerX, geometry.centerY,
                geometry.width, geometry.height, geometry.cornerRadius), params);
    }

    int drawFrame(PrismalRenderer renderer, PrismalGeometry dockBody, PrismalParams params,
                  DockGlassSceneSnapshot scene,
                  int framebufferWidth, int framebufferHeight) {
        if (renderer == null || dockBody == null || params == null) return 0;
        DockGlassSceneSnapshot stableScene = scene != null ? scene : DockGlassSceneSnapshot.EMPTY;
        beginGlassFrame(renderer);
        drawDockBody(renderer, dockBody, params);
        for (LauncherGlassGeometry.Snapshot item : stableScene.items) {
            drawItem(renderer, item, params, framebufferWidth, framebufferHeight);
        }
        return stableScene.size();
    }
}
