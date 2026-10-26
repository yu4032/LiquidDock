package com.hellovoid.liquiddock;

/** Small JVM-testable generation fence for callbacks from stale listener registrations. */
final class Api102ListenerEpoch {
    private long generation;
    private boolean active;

    synchronized long activate() {
        generation++;
        active = true;
        return generation;
    }

    synchronized void invalidate() {
        generation++;
        active = false;
    }

    synchronized boolean isCurrent(long observedGeneration) {
        return active && observedGeneration == generation;
    }
}
