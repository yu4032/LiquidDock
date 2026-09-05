package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure terminal aggregation for all live shared Launcher sessions. */
public class WorkstationProducerRecoveryAggregateTest {
    @Test
    public void aggregateWaitsForEveryTerminalSessionResult() {
        WorkstationProducerRecoveryAggregate aggregate =
                new WorkstationProducerRecoveryAggregate(2);

        assertFalse(aggregate.record(LauncherGlassProducerRecoveryState.Result.ACCEPTED));
        assertFalse(aggregate.isComplete());

        assertTrue(aggregate.record(LauncherGlassProducerRecoveryState.Result.ACCEPTED));
        assertTrue(aggregate.isComplete());
        assertEquals(
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                aggregate.terminalResult());
        assertEquals(2, aggregate.acceptedCount());
    }

    @Test
    public void failedDominatesRejectedAndAccepted() {
        WorkstationProducerRecoveryAggregate aggregate =
                new WorkstationProducerRecoveryAggregate(3);

        aggregate.record(LauncherGlassProducerRecoveryState.Result.ACCEPTED);
        aggregate.record(LauncherGlassProducerRecoveryState.Result.REJECTED);
        assertTrue(aggregate.record(LauncherGlassProducerRecoveryState.Result.FAILED));

        assertEquals(
                LauncherGlassProducerRecoveryState.Result.FAILED,
                aggregate.terminalResult());
        assertEquals(1, aggregate.acceptedCount());
        assertEquals(1, aggregate.rejectedCount());
        assertEquals(1, aggregate.failedCount());
    }

    @Test
    public void rejectedDominatesAcceptedWithoutFailure() {
        WorkstationProducerRecoveryAggregate aggregate =
                new WorkstationProducerRecoveryAggregate(2);

        aggregate.record(LauncherGlassProducerRecoveryState.Result.ACCEPTED);
        assertTrue(aggregate.record(LauncherGlassProducerRecoveryState.Result.REJECTED));

        assertEquals(
                LauncherGlassProducerRecoveryState.Result.REJECTED,
                aggregate.terminalResult());
    }

    @Test
    public void zeroLiveSessionsAreVacuouslyAccepted() {
        WorkstationProducerRecoveryAggregate aggregate =
                new WorkstationProducerRecoveryAggregate(0);

        assertTrue(aggregate.isComplete());
        assertEquals(
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                aggregate.terminalResult());
        assertEquals(0, aggregate.expectedCount());
    }
}
