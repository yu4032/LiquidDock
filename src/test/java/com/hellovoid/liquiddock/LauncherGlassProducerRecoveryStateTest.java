package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Pure endpoint/bind/fresh-frame identity for the shared Launcher producer. */
public class LauncherGlassProducerRecoveryStateTest {
    @Test
    public void requestIsAcceptedOncePerActiveRecovery() {
        LauncherGlassProducerRecoveryState state = new LauncherGlassProducerRecoveryState();

        assertEquals(
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                state.onRequest(7L));
        assertEquals(
                LauncherGlassProducerRecoveryState.Result.REJECTED,
                state.onRequest(8L));
        assertTrue(state.isActive(7L));
    }

    @Test
    public void acceptedRecoveryIsTerminalOnlyAfterEndpointBindAndFreshFrame() {
        LauncherGlassProducerRecoveryState state = new LauncherGlassProducerRecoveryState();
        state.onRequest(11L);

        assertTrue(state.onEndpointRecreated(11L, 3L));
        assertTrue(state.endpointRecreated());
        assertNull(state.onFreshFrame(11L, 3L));

        assertTrue(state.onBindSucceeded(11L, 3L));
        assertTrue(state.bindSucceeded());
        assertNull(state.terminalResult());

        assertEquals(
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                state.onFreshFrame(11L, 3L));
        assertTrue(state.freshFrameArrived());
        assertEquals(
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                state.terminalResult());
    }

    @Test
    public void staleSerialOrEndpointGenerationCannotCompleteCurrentRecovery() {
        LauncherGlassProducerRecoveryState state = new LauncherGlassProducerRecoveryState();
        state.onRequest(20L);
        state.onEndpointRecreated(20L, 9L);
        state.onBindSucceeded(20L, 9L);

        assertNull(state.onFreshFrame(19L, 9L));
        assertNull(state.onFreshFrame(20L, 8L));
        assertNull(state.terminalResult());
        assertTrue(state.isActive(20L));

        assertEquals(
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                state.onFreshFrame(20L, 9L));
    }

    @Test
    public void failedRecoveryIsTerminalAndNextEpisodeCanRetry() {
        LauncherGlassProducerRecoveryState state = new LauncherGlassProducerRecoveryState();
        state.onRequest(30L);

        assertEquals(
                LauncherGlassProducerRecoveryState.Result.FAILED,
                state.onFailure(30L));
        assertFalse(state.isActive(30L));
        assertEquals(
                LauncherGlassProducerRecoveryState.Result.FAILED,
                state.terminalResult());

        assertEquals(
                LauncherGlassProducerRecoveryState.Result.ACCEPTED,
                state.onRequest(31L));
        assertTrue(state.isActive(31L));
        assertFalse(state.endpointRecreated());
        assertFalse(state.bindSucceeded());
        assertFalse(state.freshFrameArrived());
        assertNull(state.terminalResult());
    }
}
