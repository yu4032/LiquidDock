package com.hellovoid.liquiddock;

import android.graphics.SurfaceTexture;
import android.os.SystemClock;

import java.util.Locale;

/**
 * Aggregated producer/consumer timing statistics for the Dock PassBlur glass session.
 *
 * <p>Producer frames arrive on the PassBlur render thread ({@link #producerFrame}); the same
 * thread reports {@link #consumedFrame} right after updateTexImage and {@link #presentedFrame}
 * after eglSwapBuffers. Aggregation emits at most one window line per second-scale interval
 * plus single lines for frames whose producer-to-present latency exceeds {@link #SLOW_FRAME_MS}.
 * All output is routed through MainHook.log, so it only appears while the debug switch is on.
 *
 * <p>No timers are used: windows close when the next frame event passes the interval, so an
 * idle session stays silent.
 */
final class DockFrameSyncTrace {
    private static final String TAG = "[DC][DockFrameSync]";
    private static final long WINDOW_MS = 1000L;
    private static final long SLOW_FRAME_MS = 25L;
    private static final long SLOW_LOG_MIN_INTERVAL_MS = 500L;

    // Render-thread window counters (single consumer thread).
    private static long windowStartMs = SystemClock.uptimeMillis();
    private static long producerFrames;
    private static long consumedFrames;
    private static long presentedFrames;
    private static long lastProducerMs = -1L;
    private static long maxProducerGapMs;
    private static long latencyCount;
    private static long latencySumMs;
    private static long maxLatencyMs;
    private static long lastSlowLogMs;

    // Latest producer frame not yet consumed by updateTexImage.
    private static long pendingSeq = -1L;
    private static long pendingProducerMs = -1L;
    // Consumed frame awaiting eglSwapBuffers presentation.
    private static long consumedSeq = -1L;
    private static long consumedProducerMs = -1L;
    private static long consumedBufferTsNs = -1L;
    private static String producerThread = "?";

    // Session state labels (main thread may flip; render thread reads opportunistically).
    private static volatile boolean dockAnimating;
    private static volatile boolean updatesEnabled = true;
    private static volatile String freezeReason = "";

    private DockFrameSyncTrace() {}

    /** Render thread: a new producer buffer became available (SurfaceTexture listener). */
    static void producerFrame(long seq, SurfaceTexture input) {
        long now = SystemClock.uptimeMillis();
        if (lastProducerMs >= 0L) {
            long gap = now - lastProducerMs;
            if (gap > maxProducerGapMs) maxProducerGapMs = gap;
        }
        lastProducerMs = now;
        pendingSeq = seq;
        pendingProducerMs = now;
        producerFrames++;
        if ("?".equals(producerThread)) {
            producerThread = Thread.currentThread().getName();
        }
        closeWindowIfDue(now);
    }

    /** Render thread: updateTexImage consumed the pending buffer. */
    static void consumedFrame(SurfaceTexture input) {
        consumedFrames++;
        consumedSeq = pendingSeq;
        consumedProducerMs = pendingProducerMs;
        pendingProducerMs = -1L;
        try {
            consumedBufferTsNs = input.getTimestamp();
        } catch (Throwable ignored) {
            consumedBufferTsNs = -1L;
        }
    }

    /** Render thread: a frame was presented via eglSwapBuffers. */
    static void presentedFrame() {
        long now = SystemClock.uptimeMillis();
        presentedFrames++;
        if (consumedProducerMs >= 0L) {
            long latency = now - consumedProducerMs;
            latencyCount++;
            latencySumMs += latency;
            if (latency > maxLatencyMs) maxLatencyMs = latency;
            if (latency > SLOW_FRAME_MS && now - lastSlowLogMs >= SLOW_LOG_MIN_INTERVAL_MS) {
                lastSlowLogMs = now;
                long bufferAgeMs = consumedBufferTsNs > 0L
                        ? (System.nanoTime() - consumedBufferTsNs) / 1_000_000L : -1L;
                MainHook.log(TAG + " slow seq=" + consumedSeq
                        + " lat=" + latency + "ms"
                        + " bufAge=" + bufferAgeMs + "ms"
                        + " gapMax=" + maxProducerGapMs + "ms"
                        + " dockAnim=" + dockAnimating
                        + " updates=" + updatesEnabled
                        + " th=" + Thread.currentThread().getName());
            }
            consumedProducerMs = -1L;
            consumedBufferTsNs = -1L;
        }
        closeWindowIfDue(now);
    }

    /** Dock icon animation pump activity (Dock moving / resizing). */
    static void dockAnimation(boolean active) {
        if (dockAnimating == active) return;
        dockAnimating = active;
        MainHook.log(TAG + " state dockAnim=" + active);
    }

    /** PassBlur producer update contract flips (freeze/unfreeze of the glass stream). */
    static void updatesEnabled(boolean enabled, String reason) {
        if (updatesEnabled == enabled && freezeReason.equals(enabled ? "" : String.valueOf(reason))) {
            return;
        }
        updatesEnabled = enabled;
        freezeReason = enabled ? "" : String.valueOf(reason);
        MainHook.log(TAG + " state updates=" + enabled + " reason=" + reason);
    }

    /** Session lifecycle markers (TextureView attach/detach, producer recreation). */
    static void session(String event) {
        MainHook.log(TAG + " session " + event);
    }

    private static void closeWindowIfDue(long now) {
        long elapsed = now - windowStartMs;
        if (elapsed < WINDOW_MS) return;
        if (producerFrames == 0L && presentedFrames == 0L) {
            windowStartMs = now;
            return;
        }
        float seconds = Math.max(0.001f, elapsed / 1000f);
        long coalesced = Math.max(0L, producerFrames - consumedFrames);
        MainHook.log(TAG + " win pFps=" + fmt(producerFrames / seconds)
                + " cFps=" + fmt(presentedFrames / seconds)
                + " coalesced=" + coalesced
                + " latAvg=" + (latencyCount > 0 ? (latencySumMs / latencyCount) : -1L) + "ms"
                + " latMax=" + maxLatencyMs + "ms"
                + " gapMax=" + maxProducerGapMs + "ms"
                + " dockAnim=" + dockAnimating
                + " updates=" + updatesEnabled
                + (freezeReason.isEmpty() ? "" : " freeze=" + freezeReason)
                + " thr=" + producerThread);
        windowStartMs = now;
        producerFrames = 0L;
        consumedFrames = 0L;
        presentedFrames = 0L;
        maxProducerGapMs = 0L;
        latencyCount = 0L;
        latencySumMs = 0L;
        maxLatencyMs = 0L;
    }

    private static String fmt(float value) {
        return String.format(Locale.US, "%.1f", value);
    }
}
