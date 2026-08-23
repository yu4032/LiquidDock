package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;
import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Dock body and Dock icon glass are drawn in one output-local Prismal frame/output swap. */
final class DockGlassCompositor {
    private final WeakReference<View> ownershipRootRef;
    private final WeakReference<View> outputRootRef;
    private final ArrayList<DockGlassItemNode> cached = new ArrayList<>();
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);
    private volatile DockGlassSceneSnapshot latestScene = DockGlassSceneSnapshot.EMPTY;
    private long seenRevision = -1L;
    private long lastFingerprint = Long.MIN_VALUE;
    private int lastW = -1, lastH = -1;
    private float lastInsetL = Float.NaN, lastInsetT = Float.NaN;

    DockGlassCompositor(View ownershipRoot, View outputRoot) {
        ownershipRootRef = new WeakReference<>(ownershipRoot);
        outputRootRef = new WeakReference<>(outputRoot);
    }

    void setIconStyle(GlassComponentStyle style) {
        iconStyle = style != null ? style : new GlassComponentStyle(false, 0f, 0f);
        seenRevision = -1L;
        lastFingerprint = Long.MIN_VALUE;
        if (!iconStyle.enabled) latestScene = DockGlassSceneSnapshot.EMPTY;
    }

    void refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View ownershipRoot = ownershipRootRef.get();
        View outputRoot = outputRootRef.get();
        if (ownershipRoot == null || outputRoot == null
                || !GlassRuntimeState.isEnabled() || !iconStyle.enabled) {
            cached.clear();
            latestScene = DockGlassSceneSnapshot.EMPTY;
            return;
        }

        long revision = DockGlassItemRegistry.revision();
        boolean dead = false;
        for (DockGlassItemNode item : cached) {
            if (item.view() == null || !item.belongsTo(ownershipRoot)) {
                dead = true;
                break;
            }
        }
        if (revision != seenRevision || dead) {
            cached.clear();
            for (View candidate : DockGlassItemRegistry.snapshotForRoot(ownershipRoot.getRootView())) {
                DockGlassItemNode item = new DockGlassItemNode(candidate, iconStyle);
                if (item.belongsTo(ownershipRoot)) cached.add(item);
            }
            seenRevision = revision;
            lastFingerprint = Long.MIN_VALUE;
        }

        long fingerprint = 0xcbf29ce484222325L;
        for (DockGlassItemNode item : cached) {
            fingerprint = (fingerprint ^ item.uiFingerprint(ownershipRoot)) * 0x100000001b3L;
        }
        fingerprint = mixOutputRoot(fingerprint, outputRoot);
        boolean mappingChanged = framebufferWidth != lastW || framebufferHeight != lastH
                || Float.compare(sampleInsetLeft, lastInsetL) != 0
                || Float.compare(sampleInsetTop, lastInsetT) != 0;
        if (!mappingChanged && fingerprint == lastFingerprint) return;

        Matrix outputGlobal = new Matrix();
        outputRoot.transformMatrixToGlobal(outputGlobal);
        Matrix outputInverse = new Matrix();
        if (!outputGlobal.invert(outputInverse)) {
            latestScene = DockGlassSceneSnapshot.EMPTY;
            return;
        }

        ArrayList<LauncherGlassGeometry.Snapshot> out = new ArrayList<>();
        for (DockGlassItemNode item : cached) {
            LauncherGlassGeometry.Snapshot geometry = item.capture(
                    ownershipRoot, outputInverse,
                    framebufferWidth, framebufferHeight,
                    sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
            if (geometry != null) out.add(geometry);
        }
        latestScene = new DockGlassSceneSnapshot(
                out.toArray(new LauncherGlassGeometry.Snapshot[0]));
        lastFingerprint = fingerprint;
        lastW = framebufferWidth;
        lastH = framebufferHeight;
        lastInsetL = sampleInsetLeft;
        lastInsetT = sampleInsetTop;
    }

    DockGlassSceneSnapshot latestScene() { return latestScene; }

    int drawFrame(PrismalRenderer renderer, PrismalGeometry dockBody, PrismalParams params,
            DockGlassSceneSnapshot scene, int framebufferWidth, int framebufferHeight) {
        renderer.beginGlassFrame();
        renderer.drawGlass(dockBody, params);
        DockGlassSceneSnapshot stable = scene != null ? scene : DockGlassSceneSnapshot.EMPTY;
        for (LauncherGlassGeometry.Snapshot item : stable.items) {
            renderer.drawGlass(new PrismalGeometry(framebufferWidth, framebufferHeight,
                    item.centerX, item.centerY, item.width, item.height, item.cornerRadius), params);
        }
        return stable.size();
    }

    private static long mixOutputRoot(long hash, View view) {
        hash = (hash ^ view.getLeft()) * 0x100000001b3L;
        hash = (hash ^ view.getTop()) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getTranslationX())) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getTranslationY())) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getScaleX())) * 0x100000001b3L;
        hash = (hash ^ Float.floatToIntBits(view.getScaleY())) * 0x100000001b3L;
        return hash;
    }
}
