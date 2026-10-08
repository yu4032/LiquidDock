package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Path RECENT_BLACKLIST = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/DockRecentBlacklistPage.kt");
    private static final Path WIDGET_COMPONENTS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/WidgetComponentsPage.kt");
    private static final Path GBOARD = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/GboardSettingsPages.kt");
    private static final Path DIALOG_GLASS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/DialogGlassSettingsPage.kt");
    private static final Path SIDE_SLIDE = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SideSlideHoldSetting.kt");

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
    public void modernShellRetainsPrismalControlsWithSolidHeader() throws Exception {
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

        assertTrue(source.contains("TOP_BAR_GLASS_BLUR = 14.dp"));
        assertTrue(source.contains("TOP_BAR_GLASS_TINT_ALPHA = 0.34f"));
        assertTrue(source.contains("blurRadius = TOP_BAR_GLASS_BLUR"));
        assertTrue(source.contains("backdrop = overlayBackdrop"));
        assertFalse(source.contains(".progressiveTextureBlur("));
        assertFalse(source.contains("ProgressiveBlur.Top.copy("));
        assertFalse(source.contains("rememberLayerBackdrop("));
        assertFalse(source.contains("Modifier.layerBackdrop(barBackdrop)"));
        assertFalse(source.contains("drawPlainPrismalGlass("));

        assertTrue(source.contains("SmallTopAppBar("));
        assertTrue(source.contains("title = title"));
        assertTrue(source.contains("imageVector = MiuixIcons.Back"));
        assertTrue(source.contains("rememberPrismalMergedSource"));
    }

    @Test
    public void uniformGlassHeaderHasNoRefractiveRimOrGradient() throws Exception {
        String surfaces = Files.readString(SURFACES);

        assertTrue(surfaces.contains("modifier = Modifier.matchParentSize()"));
        assertTrue(surfaces.contains("shape = { PrismalRoundedRectangle(0.dp) }"));
        assertTrue(surfaces.contains("refractionHeightPx = 0f"));
        assertTrue(surfaces.contains("refractionAmountPx = 0f"));
        assertTrue(surfaces.contains("chromaticAberration = 0f"));
        assertTrue(surfaces.contains("depthEffect = false"));
        assertTrue(surfaces.contains("TOP_BAR_BOTTOM_STROKE_ALPHA = 0.10f"));
        assertFalse(surfaces.contains("TOP_BAR_PROGRESSIVE_BLUR"));
    }

    @Test
    public void headerGlassPreservesBackdropHueWithoutPrismalHueTint() throws Exception {
        String surfaces = Files.readString(SURFACES);

        assertTrue(surfaces.contains("val headerNeutralColor = if (surface.luminance() < 0.5f) Color.Black else Color.White"));
        assertTrue(surfaces.contains("tint = Color.Unspecified"));
        assertTrue(surfaces.contains("surfaceColor = headerNeutralColor.copy(alpha = TOP_BAR_GLASS_TINT_ALPHA)"));
        assertTrue(surfaces.contains("blurRadius = TOP_BAR_GLASS_BLUR"));
        assertTrue(surfaces.contains("refractionHeightPx = 0f"));
        assertTrue(surfaces.contains("refractionAmountPx = 0f"));
    }

    @Test
    public void bottomTabSelectionUsesNativeCapsuleClippedHitTargets() throws Exception {
        String surfaces = Files.readString(SURFACES);

        assertTrue(surfaces.contains("PrismalGlassBottomTabs("));
        assertTrue(surfaces.contains("PrismalGlassBottomTab("));
        assertTrue(surfaces.contains("LocalPrismalBottomTabHighlightedIndex.current"));
        assertTrue(surfaces.contains("ModernTabContents(label, icons[index], active)"));
        assertTrue(surfaces.contains("indication = null"));
        assertFalse(surfaces.contains("Modifier.matchParentSize()\n                    .padding(4.dp)"));
    }

    @Test
    public void appBarActionsDoNotApplyHueTintToBackdrop() throws Exception {
        String surfaces = Files.readString(SURFACES);

        assertTrue(surfaces.contains("surfaceColor = headerNeutralColor.copy(alpha = 0.20f)"));
        assertTrue(surfaces.contains("surfaceColor = neutralActionTint.copy(alpha = 0.20f)"));
        assertTrue(surfaces.contains("tint = Color.Unspecified"));
        assertTrue(surfaces.contains("useVibrancy = false"));
        assertTrue(surfaces.contains("saturation = 1f"));
        assertFalse(surfaces.contains("tint = MiuixTheme.colorScheme.surface"));
    }

    @Test
    public void headerSamplesEntireScrolledContentLayerBehindGlass() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String pages = Files.readString(UI);

        assertTrue(surfaces.contains("rememberPrismalMergedSource(backgroundLayer, screenLayer)"));
        assertTrue(surfaces.contains("backdrop = overlayBackdrop"));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(screenLayer)"));
        assertTrue(surfaces.contains("Box(modifier = Modifier.fillMaxWidth().zIndex(1f))"));
        assertTrue(surfaces.contains(".zIndex(0f)"));
        assertTrue(pages.contains("LazyColumn("));
        assertTrue(pages.contains("contentPadding = PaddingValues("));
        assertTrue(pages.contains("ModernSectionLabel(\"状态\")"));
        assertTrue(pages.contains("桌面布局与液态玻璃个性化设置"));
    }

    @Test
    public void pageNameLivesInTopBarInsteadOfContentHeader() throws Exception {
        String ui = Files.readString(UI);
        String surfaces = Files.readString(SURFACES);

        assertTrue(surfaces.contains("SmallTopAppBar("));
        assertTrue(surfaces.contains("title = title"));
        assertTrue(surfaces.contains("imageVector = MiuixIcons.Back"));
        assertFalse(ui.contains("fontSize = 28.sp"));
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
    public void longListsUseGroupedContinuousRowsAndEmbeddedButtonsDoNotCastOuterShadows() throws Exception {
        String recent = Files.readString(RECENT_BLACKLIST);
        String widgetCatalog = Files.readString(WIDGET_COMPONENTS);
        String widgetDetail = Files.readString(WIDGET_DETAIL);
        String surfaces = Files.readString(SURFACES);

        assertTrue(recent.contains("availableCandidates.chunked(12)"));
        assertTrue(recent.contains("blockedPackages.chunked(12)"));
        assertTrue(recent.contains("ModernListDivider()"));
        assertFalse(recent.contains("items(availableCandidates"));
        assertFalse(recent.contains("items(blockedPackages"));

        assertTrue(widgetCatalog.contains("ModernListDivider()"));
        assertTrue(widgetDetail.contains("rankedComponents.chunked(10)"));
        assertTrue(widgetDetail.contains("ModernListDivider()"));

        assertTrue(surfaces.contains("pressLift = 0.dp"));
        assertTrue(surfaces.contains("depthShadow = null"));
    }

    @Test
    public void prismalSliderObservesExternalStepperAndResetUpdates() throws Exception {
        String surfaces = Files.readString(SURFACES);
        assertTrue(surfaces.contains("val currentValue by rememberUpdatedState(value)"));
        assertTrue(surfaces.contains("value = { currentValue }"));
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
    public void userFacingDescriptionsTrackCurrentWorkstationAndRestartBehavior() throws Exception {
        String ui = Files.readString(UI);
        String en = Files.readString(Path.of("src/main/res/values/strings.xml"));
        String zh = Files.readString(STRINGS_ZH);

        assertFalse(ui.contains("工作台 8 列"));
        assertFalse(zh.contains("8 列水平距离"));
        assertFalse(en.contains("8-column horizontal spacing"));

        assertTrue(ui.contains("适用于当前实际网格列数"));
        assertTrue(zh.contains("适配当前实际网格列数"));
        assertTrue(en.contains("current grid column count"));

        assertTrue(zh.contains("右上角“重启作用域”"));
        assertTrue(en.contains("use Restart scopes"));

        assertFalse(ui.contains("旧版 Bitmap 捕获"));
        assertFalse(ui.contains("旧兼容路径"));
        assertFalse(en.contains("name=\"pref_debug_log\""));
        assertFalse(zh.contains("name=\"pref_debug_log\""));
    }

    @Test
    public void settingsGlassCanBeDisabledWithoutDisablingSettingsControls() throws Exception {
        String ui = Files.readString(UI);
        String surfaces = Files.readString(SURFACES);
        String search = Files.readString(SEARCHBOX);
        String widget = Files.readString(WIDGET_DETAIL);
        String zh = Files.readString(STRINGS_ZH);

        assertTrue(ui.contains("SETTINGS_UI_GLASS_ENABLED"));
        assertTrue(ui.contains("private fun MoreHubPage("));
        assertTrue(ui.contains("R.string.settings_glass_effect"));
        assertTrue(ui.contains("glassEnabled = uiGlassEnabled"));

        assertTrue(surfaces.contains("glassEnabled: Boolean = true"));
        assertTrue(surfaces.contains("LocalPrismalSurfaceBackdrop provides surfaceBackdrop"));
        assertTrue(surfaces.contains("if (glassEnabled) Modifier.prismalGlassLayer"));
        assertTrue(surfaces.contains("top.yukonga.miuix.kmp.basic.Switch("));
        assertTrue(surfaces.contains("top.yukonga.miuix.kmp.basic.Slider("));
        assertTrue(surfaces.contains("if (backdrop != null)"));
        assertTrue(surfaces.contains("depthShadow = null"));

        assertTrue(search.contains("SETTINGS_UI_GLASS_ENABLED"));
        assertTrue(search.contains("glassEnabled = glassEnabled"));
        assertTrue(widget.contains("SETTINGS_UI_GLASS_ENABLED"));
        assertTrue(widget.contains("glassEnabled = glassEnabled"));
        assertTrue(zh.contains("<string name=\"settings_glass_effect\">设置界面玻璃效果</string>"));
    }

    @Test
    public void topBarUsesOnlyBottomStrokeAndKeepsActionShadowRoom() throws Exception {
        String surfaces = Files.readString(SURFACES);

        assertTrue(surfaces.contains("val fallbackShape = RoundedCornerShape(30.dp)"));
        assertTrue(surfaces.contains(".border("));
        assertTrue(surfaces.contains("width = 1.dp"));
        assertTrue(surfaces.contains("alpha = 0.14f"));

        assertTrue(surfaces.contains("TOP_BAR_ACTION_SHADOW_ROOM = 10.dp"));
        assertTrue(surfaces.contains("TOP_BAR_BOTTOM_STROKE_ALPHA = 0.10f"));
        assertTrue(surfaces.contains("bottomContent = {"));
        assertTrue(surfaces.contains("Spacer(Modifier.height(TOP_BAR_ACTION_SHADOW_ROOM))"));
        assertTrue(surfaces.contains(".align(Alignment.BottomCenter)"));
        assertFalse(surfaces.contains("drawLine("));
    }

    @Test
    public void redesignedGuiRetainsTheOriginalUserFacingPreferenceReferences() throws Exception {
        String compose = Files.readString(UI);
        String gboard = Files.readString(GBOARD);
        String search = Files.readString(SEARCHBOX);
        String widgetCatalog = Files.readString(WIDGET_COMPONENTS);
        String widgetDetail = Files.readString(WIDGET_DETAIL);
        String dialog = Files.readString(DIALOG_GLASS);
        String sideSlide = Files.readString(SIDE_SLIDE);
        String recent = Files.readString(RECENT_BLACKLIST);

        Set<String> configRefs = new HashSet<>();
        Matcher configMatcher = Pattern.compile("ConfigSchema(?:\\.[A-Za-z0-9_]+){2,}")
                .matcher(compose + "\n" + gboard + "\n" + search + "\n"
                        + widgetCatalog + "\n" + widgetDetail + "\n"
                        + dialog + "\n" + sideSlide + "\n" + recent);
        while (configMatcher.find()) configRefs.add(configMatcher.group());
        assertTrue("original GUI ConfigSchema coverage must not shrink: " + configRefs.size(),
                configRefs.size() >= 233);
        assertTrue(configRefs.contains("ConfigSchema.Debug.LOGGING"));
        assertTrue(configRefs.contains("ConfigSchema.Glass.PRISMAL_SHOW_NORMALS"));

        assertTrue(countDistinctRefs(gboard, "GboardGlassPreferences") >= 11);
        assertTrue(countDistinctRefs(gboard, "MiuiSearchboxGlassPreferences") >= 7);
        assertTrue(countDistinctRefs(widgetCatalog + "\n" + widgetDetail, "WidgetComponentStore") >= 15);
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

    private static int countDistinctRefs(String source, String owner) {
        Set<String> refs = new HashSet<>();
        Matcher matcher = Pattern.compile(Pattern.quote(owner) + "\\.[A-Za-z0-9_]+")
                .matcher(source);
        while (matcher.find()) refs.add(matcher.group());
        return refs.size();
    }

}
