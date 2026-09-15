package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for the two residual opaque layers proven by the runtime visual-tree dump. */
public class GboardFloatingGlassResidualBackgroundContractTest {
    private static final Path AUTHORITY = Path.of(
            "src/main/java/com/hellovoid/liquiddock/GboardStockVisualAuthority.java");

    private static String authority() throws Exception {
        return Files.exists(AUTHORITY) ? Files.readString(AUTHORITY) : "";
    }

    @Test public void topEdgeQzwBackgroundIsSuppressedAndRestored() throws Exception {
        String authority = authority();

        assertTrue(authority.contains("0x7f0b064f"));
        assertTrue(authority.contains("topEdgeBackground"));
        assertTrue(authority.contains("topEdge.setBackground(null)"));
        assertTrue(authority.contains("topEdge.setBackground(claim.topEdgeBackground)"));
    }

    @Test public void mainKeyboardContentBackgroundTracksDynamicRebinds() throws Exception {
        String authority = authority();

        assertTrue(authority.contains("MAIN_KEYBOARD_VIEW_HOLDER_ID"));
        assertTrue(authority.contains("BY_DYNAMIC_HOLDER"));
        assertTrue(authority.contains("claimForDynamicHolder"));
        assertTrue(authority.contains("suppressBoundContentBackground"));
        assertTrue(authority.contains("boundContentBackgrounds"));
        assertTrue(authority.contains("content.setBackground(null)"));
        assertTrue(authority.contains("content.setBackground(saved)"));
    }
}
