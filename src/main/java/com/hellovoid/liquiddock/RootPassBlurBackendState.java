package com.hellovoid.liquiddock;

/** Android-free freshness/recovery state consumed by {@link RootPassBlurBackend}. */
final class RootPassBlurBackendState {
    private final ZeroCopyProducerRecoveryState producerRecovery =
            new ZeroCopyProducerRecoveryState();

    private long requestedGeneration = -1L;
    private long freshGeneration = -1L;
    private boolean shutdown;

    synchronized boolean requestFresh(long generation) {
        if (shutdown || generation < 0L || generation < requestedGeneration) return false;
        requestedGeneration = generation;
        freshGeneration = -1L;
        producerRecovery.onGeometryInvalidated();
        return true;
    }

    synchronized ZeroCopyProducerRecoveryState.Decision requestRebind() {
        if (shutdown) return ZeroCopyProducerRecoveryState.Decision.none();
        freshGeneration = -1L;
        return producerRecovery.onRebindRequested();
    }

    synchronized ZeroCopyProducerRecoveryState.Decision onProducerRecreated() {
        if (shutdown) return ZeroCopyProducerRecoveryState.Decision.none();
        return producerRecovery.onProducerRecreated();
    }

    synchronized void onBindSucceeded() {
        if (shutdown) return;
        producerRecovery.onBindSucceeded();
    }

    synchronized void onBindExhausted() {
        if (shutdown) return;
        freshGeneration = -1L;
        producerRecovery.onBindExhausted();
    }

    synchronized void onRecreateFailed() {
        if (shutdown) return;
        freshGeneration = -1L;
        producerRecovery.onRecreateFailed();
    }

    synchronized void onGeometryInvalidated() {
        if (shutdown) return;
        freshGeneration = -1L;
        producerRecovery.onGeometryInvalidated();
    }

    synchronized ZeroCopyProducerRecoveryState.Decision onQualityChanged() {
        if (shutdown) return ZeroCopyProducerRecoveryState.Decision.none();
        freshGeneration = -1L;
        return producerRecovery.onGeometryInvalidated();
    }

    synchronized boolean onFreshFrame(long generation) {
        if (shutdown || generation < 0L || generation != requestedGeneration) return false;
        producerRecovery.onFreshFrameConsumed();
        freshGeneration = generation;
        return true;
    }

    synchronized void onTerminalFailure() {
        if (shutdown) return;
        freshGeneration = -1L;
        producerRecovery.onTerminalFailure();
    }

    synchronized void onShutdown() {
        if (shutdown) return;
        shutdown = true;
        freshGeneration = -1L;
        producerRecovery.onShutdown();
    }

    synchronized boolean hasFreshFrame(long generation) {
        return !shutdown
                && generation >= 0L
                && generation == requestedGeneration
                && generation == freshGeneration
                && producerRecovery.hasFreshFrame();
    }

    synchronized long requestedGeneration() {
        return requestedGeneration;
    }

    synchronized boolean isRebindPending() {
        return producerRecovery.isRebindPending();
    }

    synchronized boolean isActivationExhausted() {
        return producerRecovery.isActivationExhausted();
    }

    synchronized boolean isShutdown() {
        return shutdown;
    }
}
