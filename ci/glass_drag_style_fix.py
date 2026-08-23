from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "src/main/java/com/hellovoid/liquiddock"

def rep(path, old, new, count=1):
    file = ROOT / path
    text = file.read_text()
    actual = text.count(old)
    if actual != count:
        raise RuntimeError(f"{path}: pattern count={actual}, expected={count}: {old[:100]!r}")
    file.write_text(text.replace(old, new, count))

rep("LauncherGlassStaticNode.java",
'''    private GlassComponentStyle componentStyle() {''',
'''    GlassComponentStyle componentStyle() {''')

rep("MiuixLauncherDragOverlayHook.java",
'''    private static final class ResolvedSource {
        final View source;
        final LauncherGlassDragState.Kind kind;
        final float cornerRadiusPx;
        final LauncherGlassStaticNode staticSink;

        ResolvedSource(
                View source,
                LauncherGlassDragState.Kind kind,
                float cornerRadiusPx,
                LauncherGlassStaticNode staticSink) {
            this.source = source;
            this.kind = kind;
            this.cornerRadiusPx = cornerRadiusPx;
            this.staticSink = staticSink;
        }
    }''',
'''    private static final class ResolvedSource {
        final View source;
        final LauncherGlassDragState.Kind kind;
        final LauncherGlassNodeKind nodeKind;
        final GlassComponentStyle style;
        final float cornerRadiusPx;
        final float[] visualBounds;
        final LauncherGlassStaticNode staticSink;

        ResolvedSource(
                View source,
                LauncherGlassDragState.Kind kind,
                LauncherGlassNodeKind nodeKind,
                GlassComponentStyle style,
                float cornerRadiusPx,
                float[] visualBounds,
                LauncherGlassStaticNode staticSink) {
            this.source = source;
            this.kind = kind;
            this.nodeKind = nodeKind;
            this.style = style;
            this.cornerRadiusPx = cornerRadiusPx;
            this.visualBounds = visualBounds;
            this.staticSink = staticSink;
        }
    }''')

rep("MiuixLauncherDragOverlayHook.java",
'''        ResolvedSource resolved = resolveSource(child);''',
'''        ResolvedSource resolved = resolveSource(child, glassConfig);''')

rep("MiuixLauncherDragOverlayHook.java",
'''        boolean active = LauncherGlassDragOverlay.begin(
                resolved.source,
                glassConfig,
                child,
                resolved.kind,
                resolved.cornerRadiusPx);''',
'''        if (resolved.style == null || !resolved.style.enabled) return;
        boolean active = LauncherGlassDragOverlay.begin(
                resolved.source,
                glassConfig,
                child,
                resolved.kind,
                resolved.nodeKind,
                resolved.style,
                resolved.cornerRadiusPx,
                resolved.visualBounds);''')

rep("MiuixLauncherDragOverlayHook.java",
'''    private static ResolvedSource resolveSource(View child) {''',
'''    private static ResolvedSource resolveSource(
            View child, LiquidDockConfig.Glass glassConfig) {''')

rep("MiuixLauncherDragOverlayHook.java",
'''        return new ResolvedSource(
                child,
                metadata.kind,
                resolveCornerRadius(radiusSource, metadata.kind, child),
                staticSink);''',
'''        LauncherGlassNodeKind nodeKind = staticSink != null
                ? staticSink.nodeKind() : nodeKindFor(metadata.kind);
        GlassComponentStyle style = staticSink != null
                ? staticSink.componentStyle() : styleFor(glassConfig, nodeKind);
        return new ResolvedSource(
                child,
                metadata.kind,
                nodeKind,
                style,
                resolveCornerRadius(radiusSource, metadata.kind, child),
                resolveVisualBounds(child, metadata.staticHost, nodeKind),
                staticSink);''')

rep("MiuixLauncherDragOverlayHook.java",
'''\n\n\n    private static float resolveCornerRadius(\n''',
'''\n\n    private static LauncherGlassNodeKind nodeKindFor(LauncherGlassDragState.Kind kind) {
        if (kind == LauncherGlassDragState.Kind.ICON) return LauncherGlassNodeKind.ICON;
        if (kind == LauncherGlassDragState.Kind.WIDGET) return LauncherGlassNodeKind.WIDGET;
        return LauncherGlassNodeKind.LARGE_FOLDER;
    }

    private static GlassComponentStyle styleFor(
            LiquidDockConfig.Glass glassConfig, LauncherGlassNodeKind nodeKind) {
        if (glassConfig == null) return new GlassComponentStyle(true, 0f, 0f);
        switch (nodeKind) {
            case ICON: return glassConfig.iconStyle;
            case WIDGET: return glassConfig.widgetStyle;
            case SMALL_FOLDER: return glassConfig.smallFolderStyle;
            case LARGE_FOLDER:
            default: return glassConfig.largeFolderStyle;
        }
    }

    /** Resolve the originating visual footprint once; DragView remains the moving authority. */
    private static float[] resolveVisualBounds(
            View dragView, View originalHost, LauncherGlassNodeKind nodeKind) {
        if (dragView == null) return null;
        float dragWidth = Math.max(1f, dragView.getWidth());
        float dragHeight = Math.max(1f, dragView.getHeight());
        if (nodeKind != LauncherGlassNodeKind.ICON) {
            return new float[]{0f, 0f, dragWidth, dragHeight};
        }
        View visualHost = originalHost != null ? originalHost : dragView;
        LauncherGlassIconGeometry.Bounds icon = LauncherGlassIconGeometry.resolve(visualHost);
        if (icon == null) return new float[]{0f, 0f, dragWidth, dragHeight};
        float hostWidth = Math.max(1f, visualHost.getWidth());
        float hostHeight = Math.max(1f, visualHost.getHeight());
        return new float[]{
                icon.left / hostWidth * dragWidth,
                icon.top / hostHeight * dragHeight,
                icon.right / hostWidth * dragWidth,
                icon.bottom / hostHeight * dragHeight
        };
    }

    private static float resolveCornerRadius(
''')

rep("LauncherGlassDragOverlay.java",
'''    private float activeCornerRadiusPx;
    private boolean tracking;''',
'''    private float activeCornerRadiusPx;
    private float activeVisualLeft;
    private float activeVisualTop;
    private float activeVisualRight;
    private float activeVisualBottom;
    private boolean tracking;''')

rep("LauncherGlassDragOverlay.java",
'''    static boolean begin(
            View source,
            LiquidDockConfig.Glass glassConfig,
            Object token,
            LauncherGlassDragState.Kind kind,
            float cornerRadiusPx) {
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);
        return overlay != null && overlay.beginInternal(token, kind, source, cornerRadiusPx);
    }''',
'''    static boolean begin(
            View source,
            LiquidDockConfig.Glass glassConfig,
            Object token,
            LauncherGlassDragState.Kind kind,
            LauncherGlassNodeKind nodeKind,
            GlassComponentStyle style,
            float cornerRadiusPx,
            float[] visualBounds) {
        LauncherGlassDragOverlay overlay = acquire(source, glassConfig);
        return overlay != null && overlay.beginInternal(
                token, kind, nodeKind, style, source, cornerRadiusPx, visualBounds);
    }''')

rep("LauncherGlassDragOverlay.java",
'''    private boolean beginInternal(
            Object token,
            LauncherGlassDragState.Kind kind,
            View source,
            float cornerRadiusPx) {
        if (released || token == null || source == null) return false;
        LauncherGlassDragState.Bounds bounds = readRootBounds(source);
        if (bounds == null) return false;
        float resolvedRadiusPx = Math.max(0f,
                Float.isFinite(cornerRadiusPx) ? cornerRadiusPx : 0f);
        if (kind == LauncherGlassDragState.Kind.FOLDER) {
            float density = source.getResources().getDisplayMetrics().density;
            resolvedRadiusPx = LauncherGlassCornerRadiusPolicy.resolve(
                    glassConfig != null ? glassConfig.folderCornerRadiusDp : 0f,
                    density, resolvedRadiusPx, resolvedRadiusPx);
        }
        activeCornerRadiusPx = resolvedRadiusPx;
        sourceRef = new WeakReference<>(source);''',
'''    private boolean beginInternal(
            Object token,
            LauncherGlassDragState.Kind kind,
            LauncherGlassNodeKind nodeKind,
            GlassComponentStyle style,
            View source,
            float cornerRadiusPx,
            float[] visualBounds) {
        if (released || token == null || source == null) return false;
        LauncherGlassDragState.Bounds bounds = readRootBounds(source);
        if (bounds == null) return false;
        if (style == null) style = new GlassComponentStyle(true, 0f, 0f);
        float left = 0f;
        float top = 0f;
        float right = Math.max(1f, source.getWidth());
        float bottom = Math.max(1f, source.getHeight());
        if (visualBounds != null && visualBounds.length >= 4
                && Float.isFinite(visualBounds[0]) && Float.isFinite(visualBounds[1])
                && Float.isFinite(visualBounds[2]) && Float.isFinite(visualBounds[3])
                && visualBounds[2] > visualBounds[0] && visualBounds[3] > visualBounds[1]) {
            left = visualBounds[0];
            top = visualBounds[1];
            right = visualBounds[2];
            bottom = visualBounds[3];
        }
        float density = source.getResources().getDisplayMetrics().density;
        float[] styledBounds = LauncherGlassBoundsPolicy.apply(
                left, top, right, bottom, style.sizeOffsetDp * density);
        activeVisualLeft = styledBounds[0];
        activeVisualTop = styledBounds[1];
        activeVisualRight = styledBounds[2];
        activeVisualBottom = styledBounds[3];
        float resolvedRadiusPx = style.cornerRadiusDp > 0f
                ? style.cornerRadiusDp * density
                : Math.max(0f, Float.isFinite(cornerRadiusPx) ? cornerRadiusPx : 0f);
        activeCornerRadiusPx = LauncherGlassBoundsPolicy.capRadius(
                resolvedRadiusPx,
                activeVisualRight - activeVisualLeft,
                activeVisualBottom - activeVisualTop);
        sourceRef = new WeakReference<>(source);''')

rep("LauncherGlassDragOverlay.java",
'''            sink = LauncherGlassSinkView.attachToMaterial(
                    carrier, activeCornerRadiusPx, glassConfig);
            if (sink == null) return false;
        }
        sink.setNativeCornerRadiusPx(activeCornerRadiusPx);''',
'''            sink = LauncherGlassSinkView.attachToMaterial(
                    carrier, activeCornerRadiusPx, glassConfig);
            if (sink == null) return false;
        }
        sink.setLocalVisualBounds(
                activeVisualLeft, activeVisualTop, activeVisualRight, activeVisualBottom);
        sink.setNativeCornerRadiusPx(activeCornerRadiusPx);''')

rep("LauncherGlassDragOverlay.java",
'''        carrier.setVisibility(View.VISIBLE);
        sink.setNativeCornerRadiusPx(activeCornerRadiusPx);''',
'''        carrier.setVisibility(View.VISIBLE);
        sink.setLocalVisualBounds(
                activeVisualLeft, activeVisualTop, activeVisualRight, activeVisualBottom);
        sink.setNativeCornerRadiusPx(activeCornerRadiusPx);
        if (sink.syncFromMaterial()) sink.requestLifecycleRefresh();''')

rep("LauncherGlassSinkView.java",
'''    private volatile float nativeCornerRadiusPx;
    private volatile boolean disposed;''',
'''    private volatile float nativeCornerRadiusPx;
    private volatile float localVisualLeft = Float.NaN;
    private volatile float localVisualTop = Float.NaN;
    private volatile float localVisualRight = Float.NaN;
    private volatile float localVisualBottom = Float.NaN;
    private volatile boolean disposed;''')

rep("LauncherGlassSinkView.java",
'''    void setNativeCornerRadiusPx(float cornerRadiusPx) {''',
'''    void setLocalVisualBounds(float left, float top, float right, float bottom) {
        if (disposed || !Float.isFinite(left) || !Float.isFinite(top)
                || !Float.isFinite(right) || !Float.isFinite(bottom)
                || right <= left || bottom <= top) return;
        if (localVisualLeft == left && localVisualTop == top
                && localVisualRight == right && localVisualBottom == bottom) return;
        localVisualLeft = left;
        localVisualTop = top;
        localVisualRight = right;
        localVisualBottom = bottom;
        syncFromMaterial();
        requestLifecycleRefresh();
    }

    void setNativeCornerRadiusPx(float cornerRadiusPx) {''')

rep("LauncherGlassSinkView.java",
'''        int width = Math.max(1, material.getWidth());
        int height = Math.max(1, material.getHeight());
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            setLayoutParams(lp);
            changed = true;
        }
        changed |= setFloatIfChanged(this::getX, this::setX, material.getX());
        changed |= setFloatIfChanged(this::getY, this::setY, material.getY());
        if (getPivotX() != material.getPivotX()) { setPivotX(material.getPivotX()); changed = true; }
        if (getPivotY() != material.getPivotY()) { setPivotY(material.getPivotY()); changed = true; }''',
'''        float left = Float.isFinite(localVisualLeft) ? localVisualLeft : 0f;
        float top = Float.isFinite(localVisualTop) ? localVisualTop : 0f;
        float right = Float.isFinite(localVisualRight)
                ? localVisualRight : Math.max(1f, material.getWidth());
        float bottom = Float.isFinite(localVisualBottom)
                ? localVisualBottom : Math.max(1f, material.getHeight());
        int width = Math.max(1, Math.round(right - left));
        int height = Math.max(1, Math.round(bottom - top));
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null && (lp.width != width || lp.height != height)) {
            lp.width = width;
            lp.height = height;
            setLayoutParams(lp);
            changed = true;
        }
        changed |= setFloatIfChanged(this::getX, this::setX, material.getX() + left);
        changed |= setFloatIfChanged(this::getY, this::setY, material.getY() + top);
        float pivotX = material.getPivotX() - left;
        float pivotY = material.getPivotY() - top;
        if (getPivotX() != pivotX) { setPivotX(pivotX); changed = true; }
        if (getPivotY() != pivotY) { setPivotY(pivotY); changed = true; }''')

print("drag component-style handoff fix applied")
