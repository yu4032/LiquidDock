package com.hellovoid.liquiddock;

/** Pure policy for experimental Workspace PassBlur quality controls. */
final class PassBlurQualityPolicy {
    static final int MIN_CAPTURE_SCALE_PERCENT = 50;
    static final int MAX_CAPTURE_SCALE_PERCENT = 100;
    static final int MAX_RENDER_FPS = 60;

    private PassBlurQualityPolicy() {}

    static float captureScale(int requestedPercent) {
        int safePercent = Math.max(MIN_CAPTURE_SCALE_PERCENT,
                Math.min(MAX_CAPTURE_SCALE_PERCENT, requestedPercent));
        return safePercent / 100f;
    }

    static float bridgeScale(boolean launcherWorkspace, int workspacePercent) {
        return launcherWorkspace ? captureScale(workspacePercent) : 1.0f;
    }

    static int renderFps(int requestedFps) {
        if (requestedFps <= 0) return 0;
        return Math.min(MAX_RENDER_FPS, requestedFps);
    }

    static boolean requiresFreshConsumerFrame(long consumedGeneration, long sceneGeneration) {
        return consumedGeneration != sceneGeneration;
    }
}
