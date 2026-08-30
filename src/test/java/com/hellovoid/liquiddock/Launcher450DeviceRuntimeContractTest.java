package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Source gates derived from the actual Launcher 4.50 mode/layout implementation. */
public class Launcher450DeviceRuntimeContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void workstationExitUsesModeMessageAndVendorRoundRectBoundary() throws Exception {
        String settlement = Files.readString(
                MAIN.resolve("Launcher450DockTransitionSettlement.java"));
        String mainHook = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue(settlement.contains("LauncherModeChangedMessage"));
        assertTrue(settlement.contains("isEnterLaptopMode"));
        assertTrue(settlement.contains("isExitLaptopMode"));
        assertTrue(settlement.contains("updateRoundRect"));
        assertTrue(settlement.contains("MiuixGlassHook.hasReadyNativeGeometry(background)"));
        assertTrue(settlement.contains("boundary=updateRoundRect"));
        assertFalse(settlement.contains("mViewRadiusAnimator"));
        assertFalse(settlement.contains("AnimatorListenerAdapter"));

        int setMode = mainHook.indexOf("private static void setWorkstationMode(boolean enabled)");
        assertTrue(setMode >= 0);
        String logicalModeBody = mainHook.substring(setMode,
                Math.min(mainHook.length(), setMode + 900));
        assertFalse(logicalModeBody.contains("DockWorkstationVisualTransition.global().onModeChanged"));
        assertFalse(logicalModeBody.contains("syncAll(dockBg)"));
    }

    @Test public void onlyModeMessageHookOwnsVisualStrokeTransition() throws Exception {
        String renderer = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        String settlement = Files.readString(
                MAIN.resolve("Launcher450DockTransitionSettlement.java"));

        assertFalse(renderer.contains("LaptopStateManager"));
        assertTrue(settlement.contains("DockStrokeRenderer.onWorkstationModeChanged(true)"));
    }
}
