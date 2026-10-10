package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Keeps glass settings dialog motion and single-pass Prismal tab glyphs intentional. */
public class SettingsDialogMotionContractTest {
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    private static String source(String filename) throws Exception {
        return Files.readString(KOTLIN.resolve(filename));
    }

    @Test
    public void allFourSettingsModalFamiliesHaveEnterAndExitMotion() throws Exception {
        String motion = source("SettingsDialogMotion.kt");
        String data = source("SettingsUtilityPages.kt");
        String grid = source("GridSettingsPages.kt");
        String numeric = source("ModernSettingsUi.kt");
        String restart = source("SettingsRestartScopesDialog.kt");

        assertTrue(data.contains("AnimatedSettingsWindowDialog("));
        assertTrue(grid.contains("AnimatedSettingsWindowDialog("));
        assertTrue(numeric.contains("AnimatedSettingsWindowDialog("));
        assertTrue(motion.contains("WindowDialog("));
        assertTrue(motion.contains("show || transition.currentState || transition.isRunning"));
        assertTrue(motion.contains("Spring.DampingRatioNoBouncy"));
        assertTrue(motion.contains("scaleX = panelScale"));
        assertTrue(motion.contains("translationY = panelOffset"));
        assertTrue(restart.contains("AnimatedVisibility("));
        assertTrue(restart.contains("enter = fadeIn("));
        assertTrue(restart.contains("exit = fadeOut("));
        assertTrue(restart.contains("graphicsLayer { scaleX = panelScale; scaleY = panelScale }"));
        // Do not replace the original Prismal/MIUIX dialog controls or opt into
        // a second full-screen recorder for modal animation.
        assertTrue(restart.contains("GuiOnTouchPrismalToggle("));
        assertFalse(motion.contains("rememberPrismalGlassLayer"));
        assertFalse(motion.contains("ScreenCapture"));
        assertFalse(motion.contains("PixelCopy"));
    }

    @Test
    public void nativeDragHighlightReachesOnlyOneVisibleGlyphLayer() throws Exception {
        String tabs = source("SettingsBottomNavigation.kt");
        assertTrue(tabs.contains("PrismalGlassBottomTabs("));
        assertTrue(tabs.contains("PrismalGlassBottomTab("));
        assertTrue(tabs.contains("LocalPrismalBottomTabHighlightedIndex.current"));
        assertTrue(tabs.contains("SideEffect {"));
        assertTrue(tabs.contains("if (index == 0) {"));
        assertTrue(tabs.contains("active = index == highlightedIndex.intValue"));
        assertTrue(tabs.contains("draggingCandidate = highlightedIndex.intValue != selected"));
        assertTrue(tabs.contains("val contentColor by animateColorAsState("));
        assertTrue(tabs.contains("val contentScale by animateFloatAsState("));
        assertTrue(tabs.contains("tintDropletContent = false"));
        assertFalse(tabs.contains("rememberPrismalGlassLayer("));
        assertFalse(tabs.contains("drawPrismalGlass("));
        assertFalse(tabs.contains("PrismalGlassBottomTab(" +
                "onClick = { }, content = { ModernTabContents"));
    }
}
