package com.hellovoid.liquiddock;

/**
 * Endpoint-local render gate for real Workspace PassBlur source frames.
 *
 * <p>This class never creates work on its own. It only decides whether an observed OES source
 * frame should trigger the expensive Prismal/output render. Callers must still drain rejected
 * SurfaceTexture frames so BufferQueue backpressure cannot deadlock a low FPS configuration.
 * Fresh scene generations always bypass the configured render cap.</p>
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
