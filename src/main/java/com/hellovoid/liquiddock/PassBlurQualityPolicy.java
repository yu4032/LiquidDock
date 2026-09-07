package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.PassBlurQualityKeys;

/** Pure policy for Workspace PassBlur quality controls. */
final class PassBlurQualityPolicy {
    static final int DEFAULT_CAPTURE_SCALE_PERCENT = PassBlurQualityKeys.CAPTURE_SCALE_DEFAULT;
    static final int MIN_CAPTURE_SCALE_PERCENT = PassBlurQualityKeys.CAPTURE_SCALE_MIN;
    static final int MAX_CAPTURE_SCALE_PERCENT = PassBlurQualityKeys.CAPTURE_SCALE_MAX;
    static final int DEFAULT_RENDER_FPS = PassBlurQualityKeys.RENDER_FPS_DEFAULT;
    static final int MAX_RENDER_FPS = PassBlurQualityKeys.RENDER_FPS_MAX;

    private PassBlurQualityPolicy() {}

    static float captureScale(int requestedPercent) {
        int safePercent = Math.max(MIN_CAPTURE_SCALE_PERCENT,
                Math.min(MAX_CAPTURE_SCALE_PERCENT, requestedPercent));
        return safePercent / 100f;
    }

    static float bridgeScale(boolean launcherWorkspace, int workspacePercent) {
        // HyperOS PassBlur scale participates in producer geometry/SurfaceTexture semantics;
        // it is not a safe pure-resolution control for strict behind-content correspondence.
        // Native PassBlur therefore remains at 1.0; local FBOs carry the quality reduction.
        return 1.0f;
    }

    static int renderFps(int requestedFps) {
        if (requestedFps <= 0) return DEFAULT_RENDER_FPS;
        return Math.min(MAX_RENDER_FPS, requestedFps);
    }

    static boolean requiresFreshConsumerFrame(long consumedGeneration, long sceneGeneration) {
        return consumedGeneration != sceneGeneration;
    }
}
