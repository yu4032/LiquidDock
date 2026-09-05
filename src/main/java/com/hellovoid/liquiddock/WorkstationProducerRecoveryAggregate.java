package com.hellovoid.liquiddock;

/** Terminal result aggregation for all live shared Launcher sessions in one recovery episode. */
final class WorkstationProducerRecoveryAggregate {
    private final int expected;
    private int recorded;
    private int accepted;
    private int rejected;
    private int failed;
    private LauncherGlassProducerRecoveryState.Result terminalResult;

    WorkstationProducerRecoveryAggregate(int expected) {
        this.expected = Math.max(0, expected);
        if (this.expected == 0) {
            terminalResult = LauncherGlassProducerRecoveryState.Result.ACCEPTED;
        }
    }

    synchronized boolean record(LauncherGlassProducerRecoveryState.Result result) {
        if (result == null || isComplete()) return false;
        switch (result) {
            case ACCEPTED: accepted++; break;
            case REJECTED: rejected++; break;
            case FAILED: failed++; break;
        }
        recorded++;
        if (recorded < expected) return false;
        terminalResult = failed > 0
                ? LauncherGlassProducerRecoveryState.Result.FAILED
                : rejected > 0
                        ? LauncherGlassProducerRecoveryState.Result.REJECTED
                        : LauncherGlassProducerRecoveryState.Result.ACCEPTED;
        return true;
    }

    synchronized boolean isComplete() {
        return terminalResult != null;
    }

    synchronized LauncherGlassProducerRecoveryState.Result terminalResult() {
        return terminalResult;
    }

    synchronized int acceptedCount() { return accepted; }
    synchronized int rejectedCount() { return rejected; }
    synchronized int failedCount() { return failed; }
    synchronized int expectedCount() { return expected; }
}
