package com.hellovoid.liquiddock;

/**
 * Endpoint-local scheduling state for real Workspace PassBlur source frames.
 *
 * <p>This class never creates work on its own. It only gates callbacks that SurfaceTexture has
 * already delivered, preserving source-driven idle behavior and allowing fresh scene generations
 * to bypass the configured consumer frame cap.</p>
 */
final class PassBlurSourceFrameGate {
    private final PassBlurFrameRateLimiter limiter;

    PassBlurSourceFrameGate(int renderFps) {
        limiter = new PassBlurFrameRateLimiter(renderFps);
    }

    boolean shouldSchedule(long nowNanos, long consumedGeneration, long sceneGeneration) {
        return limiter.shouldSchedule(
                nowNanos,
                PassBlurQualityPolicy.requiresFreshConsumerFrame(
                        consumedGeneration, sceneGeneration));
    }
}
