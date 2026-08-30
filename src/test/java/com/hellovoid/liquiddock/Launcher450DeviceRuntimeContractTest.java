package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source gates derived from the actual Launcher 4.50 background implementation. */
public class Launcher450DeviceRuntimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void workstationExitSettlesAtVendorRoundRectBoundary() throws Exception {
        String settlement = Files.readString(
                MAIN.resolve("Launcher450DockTransitionSettlement.java"));

        assertTrue(settlement.contains("updateRoundRect"));
        assertTrue(settlement.contains("int.class, int.class, float.class"));
        assertTrue(settlement.contains("MiuixGlassHook.hasReadyNativeGeometry(background)"));
        assertTrue(settlement.contains("boundary=updateRoundRect"));
        assertFalse(settlement.contains("mViewRadiusAnimator"));
        assertFalse(settlement.contains("AnimatorListenerAdapter"));
        assertFalse(settlement.contains("AnimatorSet"));
    }
}
