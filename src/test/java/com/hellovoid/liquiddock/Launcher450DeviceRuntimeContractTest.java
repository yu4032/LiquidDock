package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source gates derived from Launcher 4.50 device logs, not just JADX structure. */
public class Launcher450DeviceRuntimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void workstationExitCannotSettleBeforeNativeGeometryIsReady() throws Exception {
        String settlement = Files.readString(
                MAIN.resolve("Launcher450DockTransitionSettlement.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue(settlement.contains("MiuixGlassHook.hasReadyNativeGeometry(background)"));
        assertTrue(settlement.contains("settleIfReady"));
        assertTrue(glass.contains("Launcher450DockTransitionSettlement.settleIfReady(dockBg)"));
    }

    @Test public void boundPrismalHostOwnsShadowInsteadOfVendorParent() throws Exception {
        String bridge = Files.readString(MAIN.resolve("DockNativeShadowBridge.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        String renderer = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));

        assertTrue(glass.contains("boundHostFor"));
        assertTrue(glass.contains("isCurrentHost"));
        assertTrue(bridge.contains("MiuixGlassHook.boundHostFor(target)"));
        assertTrue(bridge.contains("applyViewShadow"));
        assertTrue(bridge.contains("[ShadowOwner]"));
        assertTrue(renderer.contains("MiuixGlassHook.isCurrentHost(host)"));
        assertFalse(renderer.contains("if (isNativeHost(host)) return;\n\n        boolean enabled"));
    }
}
