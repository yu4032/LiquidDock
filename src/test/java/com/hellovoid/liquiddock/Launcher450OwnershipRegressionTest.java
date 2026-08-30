package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class Launcher450OwnershipRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void prismalDockAndVendorBackgroundNeverOwnStrokeAtTheSameTime() throws Exception {
        String renderer = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        assertTrue(renderer.contains("releaseNativeStrokeOwner"));
        assertTrue(renderer.contains("MiuixGlassHook.isBoundTo(background)"));
        assertTrue(renderer.contains("if (isNativeHost(parent)) releaseNativeStrokeOwner(parent);"));
        assertTrue(glass.contains("DockStrokeRenderer.configureReplacingForeground(\n                    host, config.dock, nativeRadius);"));
    }

    @Test
    public void workstationEntryCannotLeaveTheNormalModeNativeStrokeAttached() throws Exception {
        String renderer = Files.readString(MAIN.resolve("DockStrokeRenderer.java"));
        assertTrue(renderer.contains("installWorkstationTransitionHook(classLoader);"));
        assertTrue(renderer.contains("com.miui.home.launcher.laptop.LaptopStateManager"));
        assertTrue(renderer.contains("\"onLaptopModeChanged\""));
        assertTrue(renderer.contains("if (entering) onWorkstationModeChanged(true);"));
        assertTrue(renderer.contains("onWorkstationModeChanged(boolean enabled)"));
        assertTrue(renderer.contains("if (!enabled) return;"));
        assertFalse(renderer.contains("onWorkstationModeChanged(false)"));
    }

    @Test
    public void workstationExitUsesLauncher450AnimatorStateInsteadOfNonexistentIsAnimating()
            throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String settlement = Files.readString(
                MAIN.resolve("Launcher450DockTransitionSettlement.java"));
        String glass = Files.readString(MAIN.resolve("MiuixGlassHook.java"));

        assertTrue(main.contains("mViewRadiusAnimator"));
        assertTrue(main.contains("animatorSet"));
        assertFalse(main.contains("HookUtil.invoke(v, \"isAnimating\")"));
        assertTrue(settlement.contains("updateBackgroundSize"));
        assertTrue(settlement.contains("mViewRadiusAnimator"));
        assertTrue(settlement.contains("settleExit"));
        assertTrue(glass.contains("shouldCommitStrokeGeometry"));
    }

    @Test
    public void remoteViewsBackgroundOwnerMatchesLauncher450RecursiveWidgetFrameLookup()
            throws Exception {
        String suppressor = Files.readString(
                MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));
        assertTrue(suppressor.contains("group.getChildCount() != 1"));
        assertTrue(suppressor.contains("View content = group.getChildAt(0);"));
        assertTrue(suppressor.contains("content.findViewById(android.R.id.widget_frame)"));
        assertFalse(suppressor.contains("child.getTag(android.R.id.widget_frame)"));
    }
}
