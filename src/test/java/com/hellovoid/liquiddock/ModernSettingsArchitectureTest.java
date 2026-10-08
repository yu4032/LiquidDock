package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Keeps the redesigned settings UI shallow and avoids rebuilding all controls on one page. */
public class ModernSettingsArchitectureTest {
    private static final Path UI = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
    private static final Path SURFACES = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ModernSettingsUi.kt");

    @Test
    public void rootNavigationUsesFourLightweightDomainsAndRealBackStack() throws Exception {
        String source = Files.readString(UI);
        assertTrue(source.contains(
                "ROOT_PAGES = listOf(Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub)"));
        assertTrue(source.contains("var navigationStack by rememberSaveable"));
        assertTrue(source.contains("fun navigateTo(target: Page)"));
        assertTrue(source.contains("fun navigateBack()"));
        assertFalse(source.contains("private fun parentPage("));
    }

    @Test
    public void heavySettingsAreHubsInsteadOfSingleLongControlLists() throws Exception {
        String source = Files.readString(UI);

        String gridHub = functionBody(source, "GridPage", "GridBasicsPage");
        assertTrue(gridHub.contains("gridEntries.forEach"));
        assertFalse(gridHub.contains("gridSpecs.filter"));

        String dockHub = functionBody(source, "DockPage", "DockBehaviorPage");
        assertTrue(dockHub.contains("dockEntries.forEach"));
        assertFalse(dockHub.contains("dockSpecs.forEach"));

        String liquidHub = functionBody(source, "LiquidPage", "LiquidSpecPage");
        assertTrue(liquidHub.contains("liquidEntries.forEach"));
        assertFalse(liquidHub.contains("liquidSpecs.forEach"));

        String animationHub = functionBody(source, "AnimationPage", "AnimationWorkspacePage");
        assertTrue(animationHub.contains("animationEntries"));
        assertFalse(animationHub.contains("IntSetting("));
    }

    @Test
    public void liquidAndComponentControlsAreSplitIntoFocusedSubpages() throws Exception {
        String source = Files.readString(UI);
        String[] pages = {
                "LiquidMaterial", "LiquidRefraction", "LiquidColor", "LiquidLighting",
                "LiquidShadow", "LiquidSampling", "LiquidOs4",
                "GlassIcons", "GlassWidgets", "GlassFolders", "GlassMenus"
        };
        for (String page : pages) {
            assertTrue(page + " must remain a dedicated page",
                    source.contains("Page." + page));
        }
        assertTrue(source.contains("private fun GlassComponentsPage("));
        assertTrue(source.contains("private fun LiquidSamplingPage("));
        assertTrue(source.contains("private fun LiquidSpecPage("));
    }

    @Test
    public void modernShellUsesRoundedSurfacesAndPillBottomNavigation() throws Exception {
        String source = Files.readString(SURFACES);
        assertTrue(source.contains("ModernBottomNavigation"));
        assertTrue(source.contains("RoundedCornerShape(28.dp)"));
        assertTrue(source.contains("RoundedCornerShape(24.dp)"));
        assertTrue(source.contains("ModernFeatureCard"));
        assertTrue(source.contains("ModernTopActionButton"));
    }

    private static String functionBody(String source, String name, String nextName) {
        int start = source.indexOf("private fun " + name + "(");
        int end = source.indexOf("private fun " + nextName + "(", start);
        assertTrue(name + " must exist", start >= 0);
        assertTrue(nextName + " must follow " + name, end > start);
        return source.substring(start, end);
    }
}
