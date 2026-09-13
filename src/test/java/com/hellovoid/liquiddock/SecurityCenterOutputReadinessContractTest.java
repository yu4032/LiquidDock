package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the physical TextureView/EGL readiness edge that makes a current scene renderable. */
public class SecurityCenterOutputReadinessContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void eglOutputReadinessRecapturesCurrentSceneWithoutPredrawOrDelayDependency()
            throws Exception {
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("readiness must be emitted only after the EGL window surface exists",
                session.contains("next.eglSurface = sourceBackend.createWindowSurface(surface);\n"
                        + "                outputs.put(sink, next);\n"
                        + "                notifyOutputReady(sink, next);"));
        assertTrue("the live session must notify its coordinator at physical output readiness",
                session.contains("currentListener.onOutputReady(this, sink);"));
        int readinessStart = session.indexOf("private void notifyOutputReady(");
        int retry = session.indexOf("retryPendingSourceAfterOutputReady();", readinessStart);
        int callback = session.indexOf("currentListener.onOutputReady(this, sink);", readinessStart);
        assertTrue("an already-pending source must be retried before recapture creates a new logical "
                        + "request for the same generation",
                readinessStart >= 0 && retry > readinessStart && callback > retry);
        assertTrue("the coordinator must expose the output-readiness listener boundary",
                coordinator.contains("public void onOutputReady("));
        assertTrue("a newly renderable output must force the current generation to be re-offered",
                coordinator.contains("requestedGeneration = -1L;\n        refreshCurrentFrame(true);"));
        assertTrue("late readiness from a replaced sink must pass identity authority first",
                coordinator.contains("policy.acceptsOutputReady("));
        assertFalse("output readiness recovery must not use a fixed delay",
                session.contains("postDelayed("));
    }
}
