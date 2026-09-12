package com.hellovoid.liquiddock;

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
        assertTrue(session.contains("livePresentation.beginStrictPresentation("));
        assertTrue(session.contains("livePresentation.invalidate()"));

        // The existing first-presentation authority must remain intact.
        assertTrue(session.contains("presentationBarrier.begin("));
        assertTrue(session.contains("armPresentation("));
    }

    @Test
    public void liveRendererReusesExistingPrismalAndOutputPath() throws Exception {
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));

        assertTrue("Session must expose a dedicated LIVE renderer",
                session.contains("private boolean renderLiveFrame("));
        assertTrue(session.contains("prismalRenderer.prepareBackdrop("));
        assertTrue(session.contains("prismalRenderer.drawGlass("));
        assertTrue(session.contains("presentTarget("));
        assertTrue(session.contains("live frame rendered generation="));
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
