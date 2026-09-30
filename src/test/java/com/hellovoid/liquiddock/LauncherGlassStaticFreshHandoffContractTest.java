package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the cold-start handshake where the source frame can beat the static TextureView Surface. */
public class LauncherGlassStaticFreshHandoffContractTest {
    private static final Path SESSION =
            Path.of("src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java");

    @Test
    public void freshFrameCanWaitForLateStaticOutputWithoutRecapture() throws Exception {
        String source = Files.readString(SESSION);

        assertTrue("fresh frame must be retained when static output is not ready",
                source.contains("pendingStaticFreshGeneration = renderedGeneration")
                        && source.contains("fresh backdrop awaiting static output"));

        assertTrue("static output attach must consume a retained fresh frame",
                source.contains("readyGeneration = pendingStaticFreshGeneration")
                        && source.contains("backdropPrepared && readyGeneration >= 0L")
                        && source.contains("renderStaticScene(prismalParams)")
                        && source.contains("postStaticFreshPresented("));

        assertTrue("fresh presentation authority must still reach the scene controller",
                source.contains("LauncherGlassSceneController.onFreshFrameRendered("));

        assertTrue("generation changes must invalidate a retained stale handoff",
                source.contains("if (generation != sceneGeneration) clearPendingStaticFresh()"));
    }
}
