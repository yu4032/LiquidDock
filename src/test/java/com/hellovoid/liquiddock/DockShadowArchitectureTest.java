package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static API/ownership bans only; runtime shadow behavior belongs in DockShadowRuntimePolicyTest. */
public class DockShadowArchitectureTest {
    private static final Path MAIN =
            Path.of("src/main/java/com/hellovoid/liquiddock/MainHook.java");

    @Test
    public void dockShadowHasNoSecondViewOwnerOrHotSeatsAlphaMutationApi() throws Exception {
        String source = Files.readString(MAIN);

        assertFalse(source.contains("shadowViewRef"));
        assertFalse(source.contains("makeDockShadow("));
        assertFalse(source.contains("ensureShadowBelowBackground("));
        assertFalse(source.contains("overrideViewAlpha("));
        assertFalse(source.contains("nativeShadowInternalCall"));
        assertFalse(source.contains("captureVendorDockShadow"));
    }
}
