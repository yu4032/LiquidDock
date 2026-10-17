package com.hellovoid.liquiddock;

/** Android-free freshness/recovery state consumed by {@link RootPassBlurBackend}. */
final class RootPassBlurBackendState {
    private final ZeroCopyProducerRecoveryState producerRecovery =
            new ZeroCopyProducerRecoveryState();

    private long requestedGeneration = -1L;
    private long freshGeneration = -1L;
    private boolean shutdown;
    private volatile boolean capturedSourceRetired;

    /** Preserve the already-normalized GPU snapshot while permanently ending
     * the OPEN_FOLDER input producer's lifetime. Only a real fresh frame for
     * this generation can authorize retirement. Never allow an implicit rebind.
     */
    synchronized boolean retireCapturedSource(long generation) {
        if (capturedSourceRetired || !hasFreshFrame(generation)) return false;
        capturedSourceRetired = true;
        return true;
    }

    boolean isCapturedSourceRetired() {
        return capturedSourceRetired;
    }

    synchronized boolean requestFresh(long generation) {
        if (shutdown || capturedSourceRetired || generation < 0L
                || generation < requestedGeneration) return false;
        requestedGeneration = generation;
        freshGeneration = -1L;
        producerRecovery.onGeometryInvalidated();
        return true;
    }

    synchronized ZeroCopyProducerRecoveryState.Decision requestRebind() {
        if (shutdown || capturedSourceRetired) return ZeroCopyProducerRecoveryState.Decision.none();
        freshGeneration = -1L;
        return producerRecovery.onRebindRequested();
    }

    synchronized ZeroCopyProducerRecoveryState.Decision onProducerRecreated() {
        if (shutdown || capturedSourceRetired) return ZeroCopyProducerRecoveryState.Decision.none();
        return producerRecovery.onProducerRecreated();
    }

    synchronized void onBindSucceeded() {
        if (shutdown || capturedSourceRetired) return;
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
        if (shutdown || capturedSourceRetired) return;
        freshGeneration = -1L;
        producerRecovery.onGeometryInvalidated();
    }

    synchronized ZeroCopyProducerRecoveryState.Decision onQualityChanged() {
        if (shutdown || capturedSourceRetired) return ZeroCopyProducerRecoveryState.Decision.none();
        freshGeneration = -1L;
        return producerRecovery.onGeometryInvalidated();
    }

    synchronized boolean onFreshFrame(long generation) {
        if (shutdown || capturedSourceRetired || generation < 0L
                || generation != requestedGeneration) return false;
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
