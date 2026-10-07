package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contract for priming Dock force-refresh before the first resumed source frame arrives. */
public class DockFrameSyncResumeContractTest {
    private static final Path BRIDGE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurBridge.java");

    @Test
    public void dockResumePrimesExistingForceRefreshLease() throws Exception {
        String source = Files.readString(BRIDGE);
        int start = source.indexOf("static void resumeUpdates(Binding binding)");
        int end = source.indexOf("/** Workspace idle suspension", start);
        String resume = source.substring(start, end);
        assertTrue(resume.contains("binding.domain == PassBlurDomain.DOCK"));
        assertTrue(resume.contains("renewForceRefresh(binding);"));
        assertTrue(source.contains("FORCE_REFRESH_LEASE_MS = 250"));
        assertTrue(source.contains("FORCE_REFRESH_MIN_INTERVAL_MS = 50L"));
    }
}
