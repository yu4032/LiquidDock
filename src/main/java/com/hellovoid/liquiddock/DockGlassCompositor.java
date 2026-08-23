package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;
import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Dock body and Dock icon glass are drawn in one Dock-local Prismal frame/output swap. */
final class DockGlassCompositor {
    private final WeakReference<View> dockRootRef;
    private final ArrayList<DockGlassItemNode> cached = new ArrayList<>();
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);
    private volatile DockGlassSceneSnapshot latestScene = DockGlassSceneSnapshot.EMPTY;
    private long seenRevision = -1L;
    private long lastFingerprint = Long.MIN_VALUE;
    private int lastW = -1, lastH = -1;
    private float lastInsetL = Float.NaN, lastInsetT = Float.NaN;

    DockGlassCompositor(View dockRoot) { dockRootRef = new WeakReference<>(dockRoot); }
    void setIconStyle(GlassComponentStyle style) {
        iconStyle = style != null ? style : new GlassComponentStyle(false, 0f, 0f);
        seenRevision = -1L; lastFingerprint = Long.MIN_VALUE;
        if (!iconStyle.enabled) latestScene = DockGlassSceneSnapshot.EMPTY;
    }
    void refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View dockRoot = dockRootRef.get();
        if (dockRoot == null || !GlassRuntimeState.isEnabled() || !iconStyle.enabled) {
            cached.clear(); latestScene = DockGlassSceneSnapshot.EMPTY; return;
        }
        long revision = DockGlassItemRegistry.revision();
        boolean dead = false;
        for (DockGlassItemNode item : cached) if (item.view() == null || !item.belongsTo(dockRoot)) { dead = true; break; }
        if (revision != seenRevision || dead) {
            cached.clear();
            for (View candidate : DockGlassItemRegistry.snapshotForRoot(dockRoot.getRootView())) {
                DockGlassItemNode item = new DockGlassItemNode(candidate, iconStyle);
                if (item.belongsTo(dockRoot)) cached.add(item);
            }
            seenRevision = revision; lastFingerprint = Long.MIN_VALUE;
        }
        long fingerprint = 0xcbf29ce484222325L;
        for (DockGlassItemNode item : cached) fingerprint = (fingerprint ^ item.uiFingerprint(dockRoot)) * 0x100000001b3L;
        boolean mappingChanged = framebufferWidth != lastW || framebufferHeight != lastH
                || Float.compare(sampleInsetLeft, lastInsetL) != 0 || Float.compare(sampleInsetTop, lastInsetT) != 0;
        if (!mappingChanged && fingerprint == lastFingerprint) return;
        Matrix rootGlobal = new Matrix(); dockRoot.transformMatrixToGlobal(rootGlobal);
        Matrix rootInverse = new Matrix();
        if (!rootGlobal.invert(rootInverse)) { latestScene = DockGlassSceneSnapshot.EMPTY; return; }
        ArrayList<LauncherGlassGeometry.Snapshot> out = new ArrayList<>();
        for (DockGlassItemNode item : cached) {
            LauncherGlassGeometry.Snapshot g = item.capture(dockRoot, rootInverse,
                    framebufferWidth, framebufferHeight, sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
            if (g != null) out.add(g);
        }
        latestScene = new DockGlassSceneSnapshot(out.toArray(new LauncherGlassGeometry.Snapshot[0]));
        lastFingerprint = fingerprint; lastW = framebufferWidth; lastH = framebufferHeight;
        lastInsetL = sampleInsetLeft; lastInsetT = sampleInsetTop;
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
}
