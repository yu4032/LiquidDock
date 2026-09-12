package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static settlement wiring for the already-presented All Apps composition. */
public class SecurityCenterAllAppsPresentedSettlementContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void matchedPresentedCompositionSettlesWithoutSecondGeometryCapture() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("The TextureView-ACKed composition must be the settlement authority",
                coordinator.contains("applyPresentedAllAppsSettlement(settled, frame)"));
        assertFalse("A consumed settle decision must not re-enter the recapture path",
                coordinator.contains(
                        "onAllAppsToggleSettled(turbo, settled.allAppsPresent, settled.generation)"));
    }
}
