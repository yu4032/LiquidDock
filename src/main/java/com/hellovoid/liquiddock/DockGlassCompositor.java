package com.hellovoid.liquiddock;

import android.view.View;
import android.view.ViewGroup;

import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/** One Dock-domain batch: Dock body + item nodes, followed by one TextureView output swap. */
final class DockGlassCompositor {
    static final int ONE_OUTPUT_SWAP = 1;

    private final WeakReference<View> dockRootRef;
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);
    private final ArrayList<DockGlassItemNode> items = new ArrayList<>();

    DockGlassCompositor(View dockRoot) {
        dockRootRef = new WeakReference<>(dockRoot);
    }

    void setIconStyle(GlassComponentStyle style) {
        iconStyle = style != null ? style : new GlassComponentStyle(false, 0f, 0f);
        syncItems();
    }

    void syncItems() {
        View dockRoot = dockRootRef.get();
        if (dockRoot == null) return;
        ArrayList<DockGlassItemNode> next = new ArrayList<>();
        if (iconStyle.enabled) collect(dockRoot.getRootView(), dockRoot, next);
        synchronized (items) {
            items.clear();
            items.addAll(next);
        }
    }

    private void collect(View view, View dockRoot, List<DockGlassItemNode> out) {
        if (view == null || view == dockRoot) {
            // The material body itself is drawn separately; still scan its root descendants below.
        } else {
            String name = view.getClass().getName();
            if (name.endsWith(".ShortcutIcon") || "ShortcutIcon".equals(view.getClass().getSimpleName())) {
                out.add(new DockGlassItemNode(view, LauncherGlassNodeKind.ICON, iconStyle));
                return;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), dockRoot, out);
        }
    }

    void beginGlassFrame(PrismalRenderer renderer) {
        renderer.beginGlassFrame();
    }

    void drawDockBody(PrismalRenderer renderer, PrismalGeometry geometry, PrismalParams params) {
        renderer.drawGlass(geometry, params);
    }

    void drawItem(PrismalRenderer renderer, LauncherGlassGeometry.Snapshot geometry,
                  PrismalParams params) {
        if (geometry == null) return;
        renderer.drawGlass(new PrismalGeometry(
                geometry.rootWidth, geometry.rootHeight,
                geometry.centerX, geometry.centerY,
                geometry.width, geometry.height, geometry.cornerRadius), params);
    }

    int drawFrame(PrismalRenderer renderer, PrismalGeometry dockBody, PrismalParams params,
                  int framebufferWidth, int framebufferHeight,
                  float sampleInsetLeft, float sampleInsetTop,
                  float scaleX, float scaleY) {
        View dockRoot = dockRootRef.get();
        if (renderer == null || dockRoot == null || dockBody == null || params == null) return 0;
        syncItems();
        beginGlassFrame(renderer);
        drawDockBody(renderer, dockBody, params);
        List<DockGlassItemNode> snapshot;
        synchronized (items) { snapshot = new ArrayList<>(items); }
        for (DockGlassItemNode item : snapshot) {
            drawItem(renderer, item.capture(dockRoot, framebufferWidth, framebufferHeight,
                    sampleInsetLeft, sampleInsetTop, scaleX, scaleY), params);
        }
        return snapshot.size();
    }
}
