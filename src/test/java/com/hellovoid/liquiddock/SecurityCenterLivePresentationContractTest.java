package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static wiring contracts for strict first presentation followed by live same-generation redraws. */
public class SecurityCenterLivePresentationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void sessionGraduatesStrictPresentationIntoSameGenerationLiveRendering()
            throws Exception {
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));

        assertTrue(session.contains("SecurityCenterLivePresentationState"));
        assertTrue(session.contains("livePresentation.isLive(frame.generation)"));
        assertTrue(session.contains("renderLiveFrame("));
        assertTrue(session.contains("livePresentation.onStrictPresentation("));
        assertTrue(session.contains("livePresentation.invalidate()"));

        // The existing first-presentation authority must remain intact.
        assertTrue(session.contains("presentationBarrier.begin("));
        assertTrue(session.contains("armPresentation("));
    }

    @Test
    public void liveRendererDoesNotReopenOwnershipOrTextureAckBarrier() throws Exception {
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        int start = session.indexOf("private boolean renderLiveFrame(");
        assertTrue("Session must expose a dedicated LIVE renderer", start >= 0);
        int end = session.indexOf("\n    private ", start + 1);
        String live = end > start ? session.substring(start, end) : session.substring(start);

        assertTrue(live.contains("prismalRenderer.prepareBackdrop("));
        assertTrue(live.contains("prismalRenderer.drawGlass("));
        assertTrue(live.contains("presentTarget("));
        assertFalse("LIVE redraw must not arm TextureView ownership acknowledgements",
                live.contains("armPresentation("));
        assertFalse("LIVE redraw must not call the ownership listener",
                live.contains("onFrameRendered("));
        assertFalse("LIVE redraw must not reopen the strict frame pipeline",
                live.contains("framePipeline.onFreshSource("));
    }

    @Test
    public void recoveryAndOutputMutationRevokeLiveBeforeStrictRetry() throws Exception {
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));

        assertTrue(session.contains("invalidateLivePresentation()"));
        assertTrue(session.contains("requestSourceRebind(String reason)"));
        assertTrue(session.contains("recoverSourceAfterWindowVisibilityRestored()"));
        assertTrue(session.contains("requestLatestFrameAfterOutputMutation()"));
        assertTrue(session.contains("livePresentation.invalidate()"));
    }
}
