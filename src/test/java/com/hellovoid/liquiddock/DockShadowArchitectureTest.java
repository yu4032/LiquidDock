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
    @Test
    public void mainHookDoesNotOwnFeatureRuntimeState() throws Exception {
        String source = Files.readString(MAIN);

        assertFalse(source.contains("nativeShadowConfig"));
        assertFalse(source.contains("hotSeatsShadowOwnerRef"));
        assertFalse(source.contains("oldBgRef"));
        assertFalse(source.contains("normalLayoutBackup"));
        assertFalse(source.contains("dockResizeAnimators"));
        assertFalse(source.contains("workstationModeHookConfirmed"));
        assertFalse(source.contains("private static volatile boolean workstationMode"));
        assertFalse(source.contains("HookUtil."));
    }

}
