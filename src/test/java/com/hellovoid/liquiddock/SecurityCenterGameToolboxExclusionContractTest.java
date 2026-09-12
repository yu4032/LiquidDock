package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks Game Toolbox to the vendor material while peers keep custom Security Center glass. */
public class SecurityCenterGameToolboxExclusionContractTest {
    private static final Path COORDINATOR = Path.of(
            "src/main/java/com/hellovoid/liquiddock/SecurityCenterGlassCoordinator.java");

    @Test
    public void gameToolboxRestoresVendorBeforeCustomBindAndPeersRemainSupported()
            throws Exception {
        String source = Files.readString(COORDINATOR);

        int bindStart = source.indexOf("void bindAssistant(");
        int bindEnd = source.indexOf("void updateAllAppsLayout(", bindStart);
        assertTrue("bindAssistant source contract must exist",
                bindStart >= 0 && bindEnd > bindStart);
        String bind = source.substring(bindStart, bindEnd);

        int gameGate = bind.indexOf("if (type == ASSISTANT_GAME)");
        int release = bind.indexOf("releaseAll();", gameGate);
        int gameReturn = bind.indexOf("return;", release);
        int prepare = bind.indexOf("SecurityCenterMaterialModePolicy.prepareBind(");

        assertTrue("Game Toolbox must be excluded before custom material preparation",
                gameGate >= 0 && release > gameGate && gameReturn > release
                        && prepare > gameReturn);

        int supportedStart = source.indexOf("private static boolean supportedAssistant(");
        int supportedEnd = source.indexOf("\n    }", supportedStart);
        assertTrue("supportedAssistant source contract must exist",
                supportedStart >= 0 && supportedEnd > supportedStart);
        String supported = source.substring(supportedStart, supportedEnd);

        assertFalse("Game Toolbox must not remain a custom-glass supported assistant",
                supported.contains("ASSISTANT_GAME"));
        assertTrue("Video Toolbox must keep the custom Security Center glass path",
                supported.contains("ASSISTANT_VIDEO"));
        assertTrue("Global Dock must keep the custom Security Center glass path",
                supported.contains("ASSISTANT_GLOBAL_DOCK"));
    }
}
