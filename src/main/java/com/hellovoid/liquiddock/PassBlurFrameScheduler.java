package com.hellovoid.liquiddock;

/**
 * Session-local gate for source-driven PassBlur frames.
 *
 * <p>The gate never creates timers or producer pulses. It only decides whether a real source
 * callback should enqueue consumer work. Fresh scene generations always bypass the frame cap.</p>
 */
final class PassBlurFrameScheduler {
    private PassBlurFrameRateLimiter limiter;

    PassBlurFrameScheduler() {
        reset();
    }

    void reset() {
        limiter = new PassBlurFrameRateLimiter(PassBlurQualityRuntime.workspaceRenderFps());
    }

    boolean shouldSchedule(long nowNanos, long consumedGeneration, long sceneGeneration) {
        return limiter.shouldSchedule(
                nowNanos,
                PassBlurQualityPolicy.requiresFreshConsumerFrame(
                        consumedGeneration, sceneGeneration));
    }
}
