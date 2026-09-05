package com.hellovoid.liquiddock;

/**
 * Android-free terminal state for one shared Launcher PassBlur producer recovery episode.
 * Endpoint generation is deliberately independent from Launcher scene generation.
 */
final class LauncherGlassProducerRecoveryState {
    enum Result { ACCEPTED, REJECTED, FAILED }

    private long activeSerial = -1L;
    private long endpointGeneration = -1L;
    private boolean endpointRecreated;
    private boolean bindSucceeded;
    private boolean freshFrameArrived;
    private Result terminalResult;

    synchronized Result onRequest(long serial) {
        if (serial <= 0L || activeSerial >= 0L) return Result.REJECTED;
        activeSerial = serial;
        endpointGeneration = -1L;
        endpointRecreated = false;
        bindSucceeded = false;
        freshFrameArrived = false;
        terminalResult = null;
        return Result.ACCEPTED;
    }

    synchronized boolean onEndpointRecreated(long serial, long generation) {
        if (!matchesActive(serial) || generation <= 0L) return false;
        endpointGeneration = generation;
        endpointRecreated = true;
        bindSucceeded = false;
        freshFrameArrived = false;
        return true;
    }

    synchronized boolean onBindSucceeded(long serial, long generation) {
        if (!matchesEndpoint(serial, generation) || !endpointRecreated) return false;
        bindSucceeded = true;
        return true;
    }

    synchronized Result onFreshFrame(long serial, long generation) {
        if (!matchesEndpoint(serial, generation) || !endpointRecreated || !bindSucceeded) {
            return null;
        }
        freshFrameArrived = true;
        terminalResult = Result.ACCEPTED;
        activeSerial = -1L;
        return terminalResult;
    }

    synchronized Result onFailure(long serial) {
        if (!matchesActive(serial)) return null;
        terminalResult = Result.FAILED;
        activeSerial = -1L;
        return terminalResult;
    }

    synchronized Result onRejected(long serial) {
        if (!matchesActive(serial)) return null;
        terminalResult = Result.REJECTED;
        activeSerial = -1L;
        return terminalResult;
    }

    synchronized boolean isActive(long serial) {
        return matchesActive(serial);
    }

    synchronized long activeSerial() {
        return activeSerial;
    }

    synchronized long endpointGeneration() {
        return endpointGeneration;
    }

    synchronized boolean endpointRecreated() {
        return endpointRecreated;
    }

    synchronized boolean bindSucceeded() {
        return bindSucceeded;
    }

    synchronized boolean freshFrameArrived() {
        return freshFrameArrived;
    }

    synchronized Result terminalResult() {
        return terminalResult;
    }

    private boolean matchesActive(long serial) {
        return activeSerial >= 0L && activeSerial == serial;
    }

    private boolean matchesEndpoint(long serial, long generation) {
        return matchesActive(serial)
                && endpointGeneration > 0L
                && endpointGeneration == generation;
    }
}
