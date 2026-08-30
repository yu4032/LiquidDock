package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Device follow-up needs observable ownership, not inferred foreground state. */
public class DockStrokeRuntimeOwnershipDiagnosticsContractTest {
    @Test public void rendererLogsEverySingleOwnerDecisionWithIdentityAndCount() throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/DockStrokeRenderer.java"));
        assertTrue(renderer.contains("[StrokeOwner]"));
        assertTrue(renderer.contains("activeOwnerCount"));
        assertTrue(renderer.contains("System.identityHashCode"));
        assertTrue(renderer.contains("getForeground()"));
    }
}
