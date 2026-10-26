package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Typed lifecycle/freshness contract consumed by the production root PassBlur backend. */
public class RootPassBlurBackendStateTest {
    @Test
    public void bindSuccessNeverCountsAsFreshContent() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(7L));
        state.onBindSucceeded();

        assertFalse(state.hasFreshFrame(7L));
        assertTrue(state.onFreshFrame(7L));
        assertTrue(state.hasFreshFrame(7L));
    }

    @Test
    public void rebindInvalidatesFreshnessUntilRealFrameForCurrentGeneration() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(10L));
        assertTrue(state.onFreshFrame(10L));
        assertTrue(state.hasFreshFrame(10L));

        ZeroCopyProducerRecoveryState.Decision rebind = state.requestRebind();
        assertTrue(rebind.accepted);
        assertTrue(rebind.recreateProducer);
        assertFalse(state.hasFreshFrame(10L));

        ZeroCopyProducerRecoveryState.Decision recreated = state.onProducerRecreated();
        assertTrue(recreated.accepted);
        assertTrue(recreated.requestBind);
        state.onBindSucceeded();
        assertFalse(state.hasFreshFrame(10L));

        assertTrue(state.onFreshFrame(10L));
        assertTrue(state.hasFreshFrame(10L));
    }

    @Test
    public void invalidOldRootCannotTrapWorkspaceRecoveryPendingForever() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(11L));
        state.onBindSucceeded();
        assertTrue(state.onFreshFrame(11L));
        assertTrue(state.requestRebind().accepted);
        assertTrue(state.isRebindPending());
        // A second request is refused by the recovery state. The pending owner must
        // instead isolate the dead ViewRoot's producer when a new generation appears.
        assertFalse(state.requestRebind().accepted);
        assertTrue(WorkspacePassBlurRolloverPolicy.isolate(true, false, true, false));
        assertFalse(state.hasFreshFrame(11L));
        assertTrue(state.onProducerRecreated().requestBind);
        state.onBindSucceeded();
        assertFalse(state.isRebindPending());
        assertTrue(state.onFreshFrame(11L));
        assertTrue(state.hasFreshFrame(11L));
    }

    @Test
    public void oldEpochCannotAbortReplacementRecovery() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestRebind().accepted);
        state.onRebindScheduled(8L);
        assertFalse(state.onRebindAborted(7L));
        assertTrue(state.isRebindPending());
        assertTrue(state.transferRebindOwner(8L, 9L));
        assertFalse(state.onRebindAborted(8L));
        assertTrue(state.isRebindPending());
        assertTrue(state.onProducerRecreated().requestBind);
        state.onBindSucceeded();
        assertFalse(state.isRebindPending());
    }

    @Test
    public void abandonedOwnerCanClearPendingForLaterRequest() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestRebind().accepted);
        state.onRebindScheduled(44L);
        assertTrue(state.onRebindAborted(44L));
        assertFalse(state.isRebindPending());
        assertFalse(state.onRebindAborted(44L));
        assertTrue(state.requestRebind().accepted);
    }

    @Test
    public void staleGenerationCannotAuthorizeFreshOutput() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(20L));
        assertFalse(state.onFreshFrame(19L));
        assertFalse(state.hasFreshFrame(20L));
        assertTrue(state.onFreshFrame(20L));
        assertTrue(state.hasFreshFrame(20L));

        assertTrue(state.requestFresh(21L));
        assertFalse(state.hasFreshFrame(20L));
        assertFalse(state.onFreshFrame(20L));
        assertTrue(state.onFreshFrame(21L));
        assertTrue(state.hasFreshFrame(21L));
    }

    @Test
    public void qualityChangeInvalidatesFreshnessWithoutRecreatingNativeEndpoint() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(25L));
        assertTrue(state.onFreshFrame(25L));

        ZeroCopyProducerRecoveryState.Decision quality = state.onQualityChanged();

        assertTrue(quality.accepted);
        assertTrue(quality.clearFrameAvailable);
        assertFalse(quality.recreateProducer);
        assertFalse(quality.requestBind);
        assertFalse(state.isRebindPending());
        assertFalse(state.hasFreshFrame(25L));
    }

    @Test
    public void frozenFolderProducerCanRetireOnlyAfterAFreshCurrentFrame() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertFalse(state.retireCapturedSource(7L));
        assertTrue(state.requestFresh(7L));
        state.onBindSucceeded();
        assertFalse(state.retireCapturedSource(7L));
        assertFalse(state.retireCapturedSource(6L));
        assertTrue(state.onFreshFrame(7L));
        assertTrue(state.retireCapturedSource(7L));
        assertTrue(state.isCapturedSourceRetired());
        assertTrue("the already-rendered frame remains valid",
                state.hasFreshFrame(7L));
        assertFalse("no second implicit capture", state.requestFresh(8L));
        assertFalse("no producer rollover after retirement",
                state.requestRebind().accepted);
        assertFalse("do not revalidate already-frozen input",
                state.onFreshFrame(7L));
        assertFalse("no automatic recreate",
                state.onProducerRecreated().accepted);
        assertFalse("no quality reallocation after freezing",
                state.onQualityChanged().accepted);
        state.onGeometryInvalidated();
        assertTrue("frozen GPU snapshot must survive later output geometry",
                state.hasFreshFrame(7L));
        state.onShutdown();
        assertTrue(state.isShutdown());
        assertFalse(state.hasFreshFrame(7L));
    }

    @Test
    public void workspacePauseSurvivesLateBindAndQueuedRefreshUntilNewDemand() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        state.setCaptureUpdatesAllowed(false);
        state.onBindSucceeded();
        assertFalse("late bind cannot implicitly override folder pause", state.captureUpdatesAllowed());

        assertTrue(state.requestFresh(30L));
        assertTrue("explicit Recents or HOME demand resumes capture", state.captureUpdatesAllowed());
        state.setCaptureUpdatesAllowed(false);
        assertFalse("hidden consumer can override a queued refresh", state.captureUpdatesAllowed());
        assertTrue(state.requestFresh(31L));
        assertTrue("new HOME generation resumes capture after native folder close",
                state.captureUpdatesAllowed());
    }

    @Test
    public void terminalFailureAndShutdownFailClosed() {
        RootPassBlurBackendState state = new RootPassBlurBackendState();
        assertTrue(state.requestFresh(30L));
        state.onTerminalFailure();
        assertFalse(state.hasFreshFrame(30L));
        assertTrue(state.isActivationExhausted());

        state.onShutdown();
        assertTrue(state.isShutdown());
        assertFalse(state.requestFresh(31L));
        assertFalse(state.onFreshFrame(31L));
    }
}
