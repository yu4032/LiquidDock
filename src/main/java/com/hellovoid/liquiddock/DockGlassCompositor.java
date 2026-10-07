package com.hellovoid.liquiddock;

import android.graphics.Matrix;
import android.os.SystemClock;
import android.view.View;
import com.hellovoid.prismal.PrismalGeometry;
import com.hellovoid.prismal.PrismalHighlightProfile;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Dock body and Dock icon glass are drawn in one output-local Prismal frame/output swap. */
final class DockGlassCompositor {
    private static final long HASH_SEED = 0xcbf29ce484222325L;
    private static final long HASH_PRIME = 0x100000001b3L;

    private static final class CachedItem {
        final DockGlassItemNode node;
        long uiFingerprint = Long.MIN_VALUE;
        long proxyFingerprint = Long.MIN_VALUE;
        long pendingUiFingerprint = Long.MIN_VALUE;
        long pendingProxyFingerprint = Long.MIN_VALUE;
        DockIconAnimationState.Sample pendingAnimationSample;
        LauncherGlassGeometry.Snapshot geometry;

        CachedItem(DockGlassItemNode node) {
            this.node = node;
        }
    }

    private final WeakReference<View> ownershipRootRef;
    private final WeakReference<View> outputRootRef;
    private final ArrayList<CachedItem> cached = new ArrayList<>();
    private final Matrix outputGlobalScratch = new Matrix();
    private final Matrix outputInverseScratch = new Matrix();
    private volatile GlassComponentStyle iconStyle = new GlassComponentStyle(false, 0f, 0f);
    private volatile PrismalHighlightProfile iconHighlightProfile =
            PrismalHighlightProfile.ALL_ENABLED;
    private volatile float workstationIconCornerRadiusDp;
    private volatile DockGlassSceneSnapshot latestScene = DockGlassSceneSnapshot.EMPTY;
    private long seenRevision = -1L;
    private long lastFingerprint = Long.MIN_VALUE;
    private long lastOutputFingerprint = Long.MIN_VALUE;
    private int lastW = -1, lastH = -1;
    private float lastInsetL = Float.NaN, lastInsetT = Float.NaN;
    private float lastScaleX = Float.NaN, lastScaleY = Float.NaN;
    private boolean lastWorkstationMode;

    DockGlassCompositor(View ownershipRoot, View outputRoot) {
        ownershipRootRef = new WeakReference<>(ownershipRoot);
        outputRootRef = new WeakReference<>(outputRoot);
    }

    void setIconStyle(GlassComponentStyle style, PrismalHighlightProfile highlightProfile) {
        iconStyle = style != null ? style : new GlassComponentStyle(false, 0f, 0f);
        iconHighlightProfile = highlightProfile != null
                ? highlightProfile : PrismalHighlightProfile.ALL_ENABLED;
        seenRevision = -1L;
        lastFingerprint = Long.MIN_VALUE;
        if (!iconStyle.enabled) latestScene = DockGlassSceneSnapshot.EMPTY;
    }

    void setWorkstationIconCornerRadiusDp(float radiusDp) {
        workstationIconCornerRadiusDp = Math.max(0f, radiusDp);
        seenRevision = -1L;
        lastFingerprint = Long.MIN_VALUE;
    }

    void invalidateUiScene() {
        lastFingerprint = Long.MIN_VALUE;
        lastOutputFingerprint = Long.MIN_VALUE;
        for (CachedItem item : cached) {
            item.uiFingerprint = Long.MIN_VALUE;
            item.proxyFingerprint = Long.MIN_VALUE;
        }
    }

    boolean refreshUiSceneIfNeeded(int framebufferWidth, int framebufferHeight,
            float sampleInsetLeft, float sampleInsetTop, float scaleX, float scaleY) {
        View ownershipRoot = ownershipRootRef.get();
        View outputRoot = outputRootRef.get();
        if (ownershipRoot == null || outputRoot == null
                || !GlassRuntimeState.isEnabled() || !iconStyle.enabled) {
            boolean changed = latestScene.size() > 0;
            cached.clear();
            latestScene = DockGlassSceneSnapshot.EMPTY;
            return changed;
        }

        boolean workstationMode = MainHook.isWorkstationMode();
        if (workstationMode != lastWorkstationMode) {
            lastWorkstationMode = workstationMode;
            seenRevision = -1L;
        }
        long revision = DockGlassItemRegistry.revision();
        boolean dead = false;
        for (CachedItem cachedItem : cached) {
            DockGlassItemNode item = cachedItem.node;
            if (item.view() == null || !item.belongsTo(ownershipRoot)) {
                dead = true;
                break;
            }
        }
        if (revision != seenRevision || dead) {
            float resolvedRadiusDp = WorkstationDockIconRadiusPolicy.resolve(
                    iconStyle.cornerRadiusDp, workstationIconCornerRadiusDp, 1f,
                    workstationMode);
            GlassComponentStyle resolvedStyle = new GlassComponentStyle(
                    iconStyle.enabled, iconStyle.sizeOffsetDp, resolvedRadiusDp);
            cached.clear();
            for (View candidate : DockGlassItemRegistry.snapshotForRoot(ownershipRoot.getRootView())) {
                DockGlassItemNode item = new DockGlassItemNode(candidate, resolvedStyle);
                if (item.belongsTo(ownershipRoot)) cached.add(new CachedItem(item));
            }
            seenRevision = revision;
            lastFingerprint = Long.MIN_VALUE;
        }

        long nowMs = SystemClock.uptimeMillis();
        long fingerprint = HASH_SEED;
        long outputFingerprint = mixOutputRoot(HASH_SEED, outputRoot);
        boolean geometryMappingChanged = framebufferWidth != lastW || framebufferHeight != lastH
                || Float.compare(sampleInsetLeft, lastInsetL) != 0
                || Float.compare(sampleInsetTop, lastInsetT) != 0
                || Float.compare(scaleX, lastScaleX) != 0
                || Float.compare(scaleY, lastScaleY) != 0
                || outputFingerprint != lastOutputFingerprint;
        boolean anyGeometryChanged = geometryMappingChanged;
        for (int i = 0; i < cached.size(); i++) {
            CachedItem cachedItem = cached.get(i);
            DockGlassItemNode item = cachedItem.node;
            long uiFingerprint = item.uiFingerprint(ownershipRoot);
            DockIconAnimationState.Sample animationSample = item.animationSample(nowMs);
            long proxyFingerprint = proxyFingerprint(animationSample);
            cachedItem.pendingUiFingerprint = uiFingerprint;
            cachedItem.pendingProxyFingerprint = proxyFingerprint;
            cachedItem.pendingAnimationSample = animationSample;
            if (cachedItem.uiFingerprint != uiFingerprint
                    || cachedItem.proxyFingerprint != proxyFingerprint) {
                anyGeometryChanged = true;
            }
            fingerprint = mix(fingerprint, uiFingerprint);
            fingerprint = mix(fingerprint, proxyFingerprint);
            fingerprint = mix(fingerprint, Float.floatToIntBits(animationSample.opacity));
        }
        fingerprint = mix(fingerprint, outputFingerprint);
        if (!geometryMappingChanged && fingerprint == lastFingerprint) return false;

        Matrix outputInverse = null;
        if (anyGeometryChanged) {
            outputGlobalScratch.reset();
            outputRoot.transformMatrixToGlobal(outputGlobalScratch);
            outputInverseScratch.reset();
            if (!outputGlobalScratch.invert(outputInverseScratch)) {
                boolean changed = latestScene.size() > 0;
                latestScene = DockGlassSceneSnapshot.EMPTY;
                return changed;
            }
            outputInverse = outputInverseScratch;
        }

        ArrayList<DockGlassSceneSnapshot.Item> out = new ArrayList<>(cached.size());
        for (int i = 0; i < cached.size(); i++) {
            CachedItem cachedItem = cached.get(i);
            DockGlassItemNode item = cachedItem.node;
            long uiFingerprint = cachedItem.pendingUiFingerprint;
            long proxyFingerprint = cachedItem.pendingProxyFingerprint;
            DockIconAnimationState.Sample animationSample = cachedItem.pendingAnimationSample;
            if (geometryMappingChanged || cachedItem.uiFingerprint != uiFingerprint
                    || cachedItem.proxyFingerprint != proxyFingerprint) {
                if (animationSample.proxyActive) {
                    cachedItem.geometry = animationSample.proxyRect != null
                            ? item.captureProxy(animationSample.proxyRect, outputInverse,
                                    framebufferWidth, framebufferHeight,
                                    sampleInsetLeft, sampleInsetTop, scaleX, scaleY)
                            : null;
                } else {
                    cachedItem.geometry = item.capture(
                            ownershipRoot, outputInverse,
                            framebufferWidth, framebufferHeight,
                            sampleInsetLeft, sampleInsetTop, scaleX, scaleY);
                }
                cachedItem.uiFingerprint = uiFingerprint;
                cachedItem.proxyFingerprint = proxyFingerprint;
            }
            if (animationSample.opacity <= 0f || cachedItem.geometry == null) continue;
            LauncherGlassGeometry.Snapshot geometry = cachedItem.geometry;
            PrismalGeometry renderGeometry = new PrismalGeometry(
                    framebufferWidth, framebufferHeight,
                    geometry.centerX, geometry.centerY, geometry.width, geometry.height,
                    geometry.cornerRadius);
            out.add(new DockGlassSceneSnapshot.Item(
                    geometry, renderGeometry, animationSample.opacity));
        }
        if (out.isEmpty()) {
            latestScene = DockGlassSceneSnapshot.EMPTY;
        } else {
            latestScene = DockGlassSceneSnapshot.takeOwnership(
                    out.toArray(new DockGlassSceneSnapshot.Item[out.size()]));
        }
        lastFingerprint = fingerprint;
        lastOutputFingerprint = outputFingerprint;
        lastW = framebufferWidth;
        lastH = framebufferHeight;
        lastInsetL = sampleInsetLeft;
        lastInsetT = sampleInsetTop;
        lastScaleX = scaleX;
        lastScaleY = scaleY;
        return true;
    }

    DockGlassSceneSnapshot latestScene() { return latestScene; }

    int drawFrame(PrismalRenderer renderer, PrismalGeometry dockBody, PrismalParams params,
            PrismalHighlightProfile dockBodyHighlightProfile,
            DockGlassSceneSnapshot scene, int framebufferWidth, int framebufferHeight) {
        PrismalHighlightProfile bodyHighlightProfile = dockBodyHighlightProfile != null
                ? dockBodyHighlightProfile : PrismalHighlightProfile.ALL_ENABLED;
        renderer.beginGlassFrame();
        renderer.drawGlass(dockBody, params, bodyHighlightProfile);
        DockGlassSceneSnapshot stable = scene != null ? scene : DockGlassSceneSnapshot.EMPTY;
        for (DockGlassSceneSnapshot.Item item : stable.items) {
            PrismalGeometry geometry = item.prismalGeometry;
            if (geometry == null) {
                LauncherGlassGeometry.Snapshot raw = item.geometry;
                geometry = new PrismalGeometry(framebufferWidth, framebufferHeight,
                        raw.centerX, raw.centerY, raw.width, raw.height, raw.cornerRadius);
            }
            renderer.drawGlass(geometry, params, iconHighlightProfile, item.opacity);
        }
        return stable.size();
    }

    private static long proxyFingerprint(DockIconAnimationState.Sample sample) {
        if (sample == null || !sample.proxyActive) return 0L;
        long hash = mix(HASH_SEED, 1L);
        float[] rect = sample.proxyRect;
        if (rect == null || rect.length != 4) return mix(hash, 0L);
        for (float value : rect) hash = mix(hash, Float.floatToIntBits(value));
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * HASH_PRIME;
    }

    private static long mixOutputRoot(long hash, View view) {
        hash = mix(hash, view.getLeft());
        hash = mix(hash, view.getTop());
        hash = mix(hash, Float.floatToIntBits(view.getTranslationX()));
        hash = mix(hash, Float.floatToIntBits(view.getTranslationY()));
        hash = mix(hash, Float.floatToIntBits(view.getScaleX()));
        hash = mix(hash, Float.floatToIntBits(view.getScaleY()));
        return hash;
    }
}
