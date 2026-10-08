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
    private static final Path SEARCHBOX = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SearchboxSettingsActivity.kt");
    private static final Path WIDGET_DETAIL = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentDetailActivity.kt");
    private static final Path BUILD = Path.of("build.gradle.kts");
    private static final Path STRINGS_ZH = Path.of("src/main/res/values-zh-rCN/strings.xml");

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
    public void heavySettingsUseDedicatedHubAndPartitionStructures() throws Exception {
        String source = Files.readString(UI);

        assertTrue(source.contains("gridEntries.forEach"));
        assertTrue(source.contains("dockEntries.forEach"));
        assertTrue(source.contains("liquidEntries.forEach"));
        assertTrue(source.contains("workstationEntries.forEach"));
        assertTrue(source.contains("animationEntries"));
        assertTrue(source.contains("highlightEntries"));

        assertFalse("old all-in-one liquid list must stay removed",
                source.contains("liquidSpecs.forEach"));
        assertFalse("old all-in-one workstation list must stay removed",
                source.contains("workstationSpecs.forEach"));
    }

    @Test
    public void liquidAndComponentControlsAreSplitIntoFocusedSubpages() throws Exception {
        String source = Files.readString(UI);
        String[] pages = {
                "LiquidMaterial", "LiquidRefraction", "LiquidColor", "LiquidLighting",
                "LiquidShadow", "LiquidSampling", "LiquidOs4",
                "GlassIcons", "GlassWidgets", "GlassFolders", "GlassMenus",
                "WorkstationDock", "WorkstationDesktop",
                "WorkstationAppsLandscape", "WorkstationAppsPortrait",
                "LauncherHighlightsCompact", "LauncherHighlightsLarge"
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
    public void modernShellUsesCompletePrismalComponentStack() throws Exception {
        String source = Files.readString(SURFACES);
        String build = Files.readString(BUILD);

        assertTrue(build.contains("com.github.styropyr0:PrismalAGSL:v1.0.4"));
        assertTrue(source.contains("PrismalGlassSurface"));
        assertTrue(source.contains("PrismalGlassButton"));
        assertTrue(source.contains("PrismalGlassBottomTabs"));
        assertTrue(source.contains("PrismalGlassBottomTab"));
        assertTrue(source.contains("PrismalGlassToggle"));
        assertTrue(source.contains("PrismalGlassSlider"));
        assertTrue(source.contains("PrismalGlassStepper"));
        assertTrue(source.contains("PrismalGlassMenu"));
        assertTrue(source.contains("PrismalGlassMenuItem"));
        assertTrue(source.contains("shape = { PrismalRoundedRectangle(0.dp) }"));
        assertTrue(source.contains("tintAlpha = 0.34f"));
        assertTrue(source.contains("SmallTopAppBar("));
        assertTrue(source.contains("title = \" \""));
        assertTrue(source.contains("imageVector = MiuixIcons.Back"));
        assertTrue(source.contains("rememberPrismalMergedSource"));
    }

    @Test
    public void bottomTabsHaveIconsAndNumericControlsExposeValueSliderAndStepper() throws Exception {
        String ui = Files.readString(UI);
        String surfaces = Files.readString(SURFACES);

        assertTrue(ui.contains("val rootIcons = listOf("));
        assertTrue(ui.contains("MiuixIcons.Home"));
        assertTrue(ui.contains("MiuixIcons.GridView"));
        assertTrue(ui.contains("MiuixIcons.Image"));
        assertTrue(ui.contains("MiuixIcons.Settings"));
        assertTrue(ui.contains("icons = rootIcons"));

        assertTrue(ui.contains("val displayText ="));
        assertTrue(ui.contains("ModernGlassSlider("));
        assertTrue(ui.contains("ModernGlassStepper("));
        assertTrue(surfaces.contains("internal fun ModernGlassSlider("));
        assertTrue(surfaces.contains("internal fun ModernGlassStepper("));
    }

    @Test
    public void appearanceAndMoreRootNavigationStayUserFacingAndFocused() throws Exception {
        String source = Files.readString(UI);
        String zh = Files.readString(STRINGS_ZH);

        assertTrue(zh.contains("<string name=\"tab_glass\">外观</string>"));
        assertTrue(source.contains(
                "private val moreEntries = listOf(\n"
                        + "    HubEntry(Page.Data, R.string.page_data, \"默认配置、导入与导出\"),\n"
                        + "    HubEntry(Page.About, R.string.page_about, \"第三方开源项目与许可\"),\n"
                        + ")"));
        assertFalse(source.contains("拆成独立子页"));
        assertFalse(source.contains("组合对应控件"));
        assertFalse(source.contains("轻量页"));
    }

    @Test
    public void secondarySettingsActivitiesUseTheSameModernShell() throws Exception {
        String search = Files.readString(SEARCHBOX);
        String widget = Files.readString(WIDGET_DETAIL);
        assertTrue(search.contains("ModernSettingsScaffold("));
        assertTrue(widget.contains("ModernSettingsScaffold("));
        assertFalse(search.contains("SmallTopAppBar("));
        assertFalse(widget.contains("SmallTopAppBar("));
    }

}
