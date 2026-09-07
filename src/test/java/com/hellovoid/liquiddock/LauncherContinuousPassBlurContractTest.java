package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public class LauncherContinuousPassBlurContractTest {
    private static final Path SESSION = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java");

    @Test
    public void consumingLiveOesFrameDoesNotPauseWorkspaceProducer() throws Exception {
        String source = Files.readString(SESSION);
        String consumeBlock = between(
                source,
                "if (input != null && frameAvailable.getAndSet(false)) {",
                "if (consumedGeneration < 0L) return;");

        assertTrue(consumeBlock.contains("input.updateTexImage();"));
        assertFalse(consumeBlock.contains("Miuix307PassBlurBridge.pauseUpdates(binding);"));
    }

    @Test
    public void explicitWorkspaceSuspensionStillPausesCoveredProducer() throws Exception {
        String source = Files.readString(SESSION);
        String suspendBlock = between(
                source,
                "void suspendWorkspaceProducer() {",
                "void resumeWorkspaceProducer() {");

        assertTrue(suspendBlock.contains("shouldPauseSharedProducer("));
        assertTrue(suspendBlock.contains("Miuix307PassBlurBridge.pauseUpdates(binding);"));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("Expected source anchors missing: " + start + " -> " + end);
        }
        return source.substring(startIndex, endIndex);
    }
}
