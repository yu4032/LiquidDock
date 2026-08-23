package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.view.View;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** One Dock-domain batch: UI publishes geometry, GL consumes it with one output swap. */
final class DockGlassCompositor {
    static final int ONE_OUTPUT_SWAP = 1;

    private final WeakReference<View> dockRootRef;
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);

    DockGlassCompositor(View dockRoot) {
        dockRootRef = new WeakReference<>(dockRoot);
    }

    void setIconStyle(GlassComponentStyle style) {
        iconStyle = style != null ? style : new GlassComponentStyle(false, 0f, 0f);
    }

    /** UI-thread only: capture one coherent Dock item geometry generation. */
    DockGlassSceneSnapshot captureUiSnapshot(
            int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop,
            float scaleX, float scaleY) {
        View dockRoot = dockRootRef.get();
        GlassComponentStyle style = iconStyle;
        if (dockRoot == null || style == null || !style.enabled
                || framebufferWidth <= 0 || framebufferHeight <= 0) {
            return DockGlassSceneSnapshot.EMPTY;
        }

        Matrix rootGlobal = new Matrix();
        dockRoot.transformMatrixToGlobal(rootGlobal);
        Matrix rootInverse = new Matrix();
        if (!rootGlobal.invert(rootInverse)) return DockGlassSceneSnapshot.EMPTY;

        ArrayList<LauncherGlassGeometry.Snapshot> geometry = new ArrayList<>();
        for (View view : DockGlassItemRegistry.snapshotForRoot(dockRoot.getRootView())) {
            LauncherGlassGeometry.Snapshot item = new DockGlassItemNode(
                    view, LauncherGlassNodeKind.ICON, style).capture(
                    dockRoot, rootInverse, framebufferWidth, framebufferHeight,
                    sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
            if (item != null) geometry.add(item);
        }
        return new DockGlassSceneSnapshot(
                geometry.toArray(new LauncherGlassGeometry.Snapshot[0]));
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
