package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.PassBlurQualityKeys;

/** Pure policy for shared PassBlur/Prismal quality controls. */
final class PassBlurQualityPolicy {
    static final int DEFAULT_CAPTURE_SCALE_PERCENT = PassBlurQualityKeys.CAPTURE_SCALE_DEFAULT;
    static final int MIN_CAPTURE_SCALE_PERCENT = PassBlurQualityKeys.CAPTURE_SCALE_MIN;
    static final int MAX_CAPTURE_SCALE_PERCENT = PassBlurQualityKeys.CAPTURE_SCALE_MAX;
    static final int DEFAULT_RENDER_FPS = PassBlurQualityKeys.RENDER_FPS_DEFAULT;

    private PassBlurQualityPolicy() {}

    static float captureScale(int requestedPercent) {
        int safePercent = Math.max(MIN_CAPTURE_SCALE_PERCENT,
                Math.min(MAX_CAPTURE_SCALE_PERCENT, requestedPercent));
        return safePercent / 100f;
    }

    /** Keep procedural SDF/refraction at native pixel density even if backdrop sampling is reduced. */
    static int workspaceOpticsScalePercent(boolean workspace, int requestedPercent) {
        return MAX_CAPTURE_SCALE_PERCENT;
    }

    static float bridgeScale(boolean launcherWorkspace, int workspacePercent) {
        // HyperOS PassBlur scale participates in producer geometry/SurfaceTexture semantics;
        // it is not a safe pure-resolution control for strict behind-content correspondence.
        // Native PassBlur therefore remains at 1.0; local FBOs carry the quality reduction.
        return 1.0f;
    }

    static int renderFps(int requestedFps) {
        return requestedFps <= 0 ? DEFAULT_RENDER_FPS : requestedFps;
    }

    static int applyGlobalRenderFpsCap(int requestedFps, int globalCapFps) {
        int requested = renderFps(requestedFps);
        int global = renderFps(globalCapFps);
        if (global == DEFAULT_RENDER_FPS) return requested;
        if (requested == DEFAULT_RENDER_FPS) return global;
        return Math.min(requested, global);
    }

    static boolean requiresFreshConsumerFrame(long consumedGeneration, long sceneGeneration) {
        return consumedGeneration != sceneGeneration;
    }
}
