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
    public void denseSettingsUseViewportBoundedLazyItemsWithoutReplacingPrismal() throws Exception {
        String ui = Files.readString(UI);
        String surfaces = Files.readString(SURFACES);

        // Static architecture gate, not a frame-rate or runtime behavior assertion.
        assertTrue(ui.contains("private fun DenseSettingsList("));
        assertTrue(ui.contains("content: LazyListScope.() -> Unit"));
        assertTrue(ui.contains("items(specs, key = { it.key })"));
        assertTrue(ui.contains("item(key = \"stroke-colors-title\")"));
        assertTrue(ui.contains(".weight(1f)"));
        assertTrue(ui.contains("contentPadding = PaddingValues(vertical = 4.dp)"));
        assertTrue(ui.contains("private fun SettingsList("));

        assertTrue(surfaces.contains("PrismalGlassSlider("));
        assertTrue(surfaces.contains("PrismalGlassStepper("));
    }

    @Test
    public void pageEntryZoomDoesNotRegressAndCellsAvoidRecapturedDepthShadows() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String cards = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticPressPrismalSurface.kt"));

        // PR #293: a chevron Cell always takes the static-geometry Prismal
        // renderer even while its AGSL click ripple and navigation remain.
        assertTrue(surfaces.contains("internal fun ModernFeatureCard("));
        assertTrue(surfaces.contains("staticPress = true,"));
        assertTrue(surfaces.contains("if (onClick == null || staticPress) {"));
        assertTrue(surfaces.contains("GuiStaticPressPrismalSurface("));
        assertTrue(cards.contains("PrismalPressRipple("));
        assertTrue(cards.contains(".then(pressRipple?.modifier ?: Modifier)"));
        assertTrue(cards.contains(".then(pressRipple?.gestureModifier ?: Modifier)"));
        assertFalse(cards.contains("layerBlock ="));
        assertFalse(cards.contains("scaleX ="));
        assertFalse(cards.contains("translationX ="));

        // The bottom bar still records normal page text and glass optics.
        // Avoid capturing default PrismalDepthShadow twice on Settings Cells,
        // which produces broad gradient bands behind the refractive capsule.
        assertTrue(cards.contains("depthShadow = null,"));
        assertTrue(cards.contains("specular"));
        assertTrue(cards.contains("applyPrismalGlassEffects("));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(screenLayer)"));
        assertTrue(surfaces.contains("PrismalGlassBottomTabs("));
    }

    @Test
    public void chevronPageCellsRetainPrismalRippleWithoutGeometryMotion() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String navigationSurface = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticPressPrismalSurface.kt"));

        // Static architecture/API contract, not an inference about device frame times.
        assertTrue(surfaces.contains("internal fun ModernFeatureCard("));
        assertTrue(surfaces.contains("staticPress = true,"));
        assertTrue(surfaces.contains("GuiStaticPressPrismalSurface("));
        assertTrue(surfaces.contains("onClick = onClick,"));
        assertTrue(surfaces.contains("PrismalGlassSurface("));
        assertTrue(surfaces.contains("internal fun ArrowPreference("));
        assertTrue(surfaces.contains("imageVector = MiuixIcons.Basic.ArrowRight"));

        assertTrue("original press highlight/ripple must be preserved",
                navigationSurface.contains("PrismalPressRipple("));
        assertTrue(navigationSurface.contains(".then(pressRipple?.modifier ?: Modifier)"));
        assertTrue(navigationSurface.contains(".then(pressRipple?.gestureModifier ?: Modifier)"));
        assertTrue(navigationSurface.contains("if (onClick != null) PrismalPressRipple("));
        assertTrue(navigationSurface.contains("drawPrismalGlass("));
        assertTrue(navigationSurface.contains("drawPrismalGlassTint("));
        assertTrue(navigationSurface.contains("applyPrismalGlassEffects("));
        assertTrue(navigationSurface.contains("role = Role.Button"));
        assertFalse("navigation press may not transform the whole glass Cell",
                navigationSurface.contains("layerBlock ="));
        assertFalse(navigationSurface.contains("translationX ="));
        assertFalse(navigationSurface.contains("scaleX ="));
    }

    @Test
    public void parameterRecompositionDoesNotRereadStoredInitialState() throws Exception {
        String ui = Files.readString(UI);

        assertTrue(ui.contains("val resetValue = remember(spec.key, maxValue) {"));
        assertTrue(ui.contains("var value by remember(spec.key, maxValue) {"));
        assertTrue(ui.contains("val initial = (if (decimalDp && prefs.contains("));
        assertTrue(ui.contains("val displayValue = remember(value, decimalDp) {"));
        assertTrue(ui.contains("if (bounded == value) return"));
    }

    @Test
    public void solidActionsAndDarkCellsHaveDistinctLayeringWithoutReplacingPrismal() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String navSurface = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticPressPrismalSurface.kt"));

        // Static material and architecture guards, not device-level contrast metrics.
        assertTrue(surfaces.contains("val solidCardColor = if (darkTheme)"));
        assertTrue(surfaces.contains("lerp(colors.surface, colors.onSurface, 0.08f)"));
        assertTrue(surfaces.contains(".background(solidCardColor)"));
        assertTrue(surfaces.contains(".border(1.dp, cardStroke, cardShape)"));
        assertTrue(surfaces.contains("val glassCardModifier = modifier"));
        assertTrue(surfaces.contains("darkGlassWash"));
        assertTrue(surfaces.contains("surfaceColor = darkGlassWash"));

        // +/- and reset share Button's fallback and must have their own outline.
        assertTrue(surfaces.contains("val buttonShape = RoundedCornerShape(minHeight / 2)"));
        assertTrue(surfaces.contains("val fillColor = lerp(colors.surface, colors.onSurface,"));
        assertTrue(surfaces.contains(".border(1.dp, outlineColor, buttonShape)"));
        assertTrue(surfaces.contains("else if (backdrop == null) 0.66f"));

        // Glass still uses the original renderer and the navigational ripple.
        assertTrue(surfaces.contains("PrismalGlassSurface("));
        assertTrue(navSurface.contains("PrismalPressRipple("));
        assertTrue(navSurface.contains("drawPrismalGlassTint(tint, tintAlpha)"));
        assertTrue(navSurface.contains("surfaceColor.isSpecified"));
        assertFalse(navSurface.contains("scaleX ="));
        assertFalse(navSurface.contains("translationX ="));
    }

    @Test
    public void modernShellRetainsPrismalControlsWithSolidHeader() throws Exception {
        String source = Files.readString(SURFACES);
        String build = Files.readString(BUILD);
        String header = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalFlatHeader.kt"));

        assertTrue(build.contains("com.github.styropyr0:PrismalAGSL:v1.0.4"));
        assertTrue(build.contains("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4"));
        assertFalse("no unused MIUIX blur implementation", build.contains("miuix-blur-android"));
        assertTrue(source.contains("PrismalGlassSurface"));
        assertTrue(source.contains("PrismalGlassButton"));
        assertTrue(source.contains("PrismalGlassBottomTabs"));
        assertTrue(source.contains("PrismalGlassBottomTab"));
        assertTrue(source.contains("PrismalGlassToggle"));
        assertTrue(source.contains("PrismalGlassSlider"));
        assertTrue(source.contains("PrismalGlassStepper"));
        assertTrue(source.contains("TOP_BAR_BLUR_RADIUS = 14f"));
        assertTrue(source.contains("TOP_BAR_GLASS_TINT_ALPHA = 0.34f"));
        assertTrue(source.contains("blurRadius = TOP_BAR_BLUR_RADIUS.dp"));
        assertTrue(source.contains("GuiPrismalFlatHeader("));
        assertFalse(source.contains(".textureBlur("));
        assertFalse(source.contains("rememberLayerBackdrop("));
        assertFalse(source.contains("GuiPrismalMiuixBackdrop"));
        assertTrue(source.contains("SmallTopAppBar("));
        assertTrue(source.contains("title = title"));
        assertTrue(source.contains("imageVector = MiuixIcons.Back"));
        assertTrue(source.contains("rememberPrismalMergedSource(backgroundLayer, screenLayer)"));
        assertTrue(header.contains("drawPrismalGlass("));
        assertTrue(header.contains("applyPrismalGlassEffects("));
    }

    @Test
    public void uniformGlassHeaderHasNoRefractiveRimOrGradient() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String header = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalFlatHeader.kt"));

        assertTrue(surfaces.contains("GuiPrismalFlatHeader("));
        assertTrue(header.contains("shape = { RectangleShape }"));
        assertTrue("Preserve Prismal optics instead of weakening upper-status-bar material",
                header.contains("specular = { PrismalSpecular.Default }"));
        assertTrue(header.contains("depthShadow = { PrismalDepthShadow.Default }"));
        assertTrue("Only the glass slab extends past the viewport", header.contains("val overscanPx = 24.dp.roundToPx()"));
        assertTrue(header.contains("foreground.width + overscanPx * 2"));
        assertTrue(header.contains("foreground.height + overscanPx"));
        assertTrue(header.contains("glass.place(-overscanPx, -overscanPx)"));
        assertTrue(header.contains("foreground.place(0, 0)"));
        assertTrue(header.contains("refractionHeightPx = 0f"));
        assertTrue(header.contains("refractionAmountPx = 0f"));
        assertTrue(header.contains("depthEffect = false"));
        assertTrue(header.contains("chromaticAberration = 0f"));
        assertTrue(header.contains("blurRadiusPx = with(density) { blurRadius.toPx() }"));
        assertTrue(surfaces.contains("TOP_BAR_BOTTOM_STROKE_ALPHA = 0.10f"));
        assertFalse(surfaces.contains(".textureBlur("));
        assertFalse(surfaces.contains(".progressiveTextureBlur("));
    }

    @Test
    public void headerGlassPreservesBackdropHueWithoutPrismalHueTint() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String header = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalFlatHeader.kt"));

        assertTrue(surfaces.contains("val headerNeutralColor = if (surface.luminance() < 0.5f) Color.Black else Color.White"));
        assertTrue(surfaces.contains("overlayColor = headerNeutralColor.copy(alpha = TOP_BAR_GLASS_TINT_ALPHA)"));
        assertTrue(header.contains("useVibrancy = false"));
        assertTrue(header.contains("saturation = 1.35f"));
        assertTrue(header.contains("drawRect(overlayColor)"));
        assertTrue(surfaces.contains("tint = Color.Unspecified"));
        assertTrue(surfaces.contains("surfaceColor = headerNeutralColor.copy(alpha = 0.20f)"));
    }

    @Test
    public void bothBarsShareTheOriginalPrismalBackdropWithoutSecondRecording() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String header = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalFlatHeader.kt"));

        assertTrue(surfaces.contains("rememberPrismalMergedSource(backgroundLayer, screenLayer)"));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(backgroundLayer)"));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(screenLayer)"));
        assertTrue(surfaces.contains("LocalPrismalOverlayBackdrop provides activeOverlayBackdrop"));
        assertTrue(surfaces.contains("val backdrop = LocalPrismalOverlayBackdrop.current"));
        assertTrue(surfaces.contains("backdrop = activeOverlayBackdrop,"));
        // Static architecture audit; actual modifier capture order is validated
        // by real rendering rather than prohibited source-order inference.
        assertTrue(surfaces.contains("lerp(background, primary, 0.07f)"));
        assertTrue(surfaces.contains("PrismalGlassBottomTabs("));
        assertTrue(header.contains("PrismalBackdrop"));
        assertFalse(surfaces.contains("rememberLayerBackdrop {"));
        assertFalse(surfaces.contains("Modifier.layerBackdrop(barBackdrop)"));
        assertFalse(surfaces.contains("GuiPrismalMiuixBackdrop"));
    }

    @Test
    public void bottomTabSelectionUsesNativeCapsuleClippedHitTargets() throws Exception {
        String surfaces = Files.readString(SURFACES);

        assertTrue(surfaces.contains("PrismalGlassBottomTabs("));
        assertTrue(surfaces.contains("PrismalGlassBottomTab("));
        assertTrue(surfaces.contains("labels.indices.forEach { index ->"));
        assertTrue(surfaces.contains("ModernTabContents(label, icons[index], index == selected)"));
        assertTrue(surfaces.contains("PrismalGlassBottomTab("));
        assertFalse(surfaces.contains("LocalPrismalBottomTabHighlightedIndex.current"));
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
        // Other Prismal settings widgets may still use their own material tint.
        // Only the app-bar buttons are required to be hue-neutral.
    }

    @Test
    public void headerSamplesSharedPrismalScreenLayerAndKeepsUniformBlur() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String pages = Files.readString(UI);
        String header = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalFlatHeader.kt"));

        assertTrue(surfaces.contains("rememberPrismalMergedSource(backgroundLayer, screenLayer)"));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(screenLayer)"));
        assertTrue(surfaces.contains("GuiPrismalFlatHeader("));
        assertTrue(surfaces.contains("content = headerContent"));
        assertTrue(surfaces.contains(".zIndex(0f)"));
        assertFalse(surfaces.contains(".textureBlur("));
        assertFalse(surfaces.contains("Modifier.layerBackdrop(barBackdrop)"));
        assertFalse(surfaces.contains("CompositingStrategy.Offscreen"));
        assertTrue(header.contains("drawPrismalGlass("));

        // Regular Compose labels are descendants of the captured scaffold body,
        // not separate Prismal widgets. Changing their composable type would
        // not fix a backdrop coordinate or nested layer capture issue.
        assertTrue(pages.contains("LazyColumn("));
        assertTrue(pages.contains("ModernSectionLabel(\"状态\")"));
        assertFalse("home subtitle was intentionally removed", pages.contains("桌面布局与液态玻璃个性化设置"));
        assertTrue(pages.contains("private fun HomePage("));
        assertTrue(pages.contains("internal fun PageHeader("));
    }

    @Test
    public void bottomLabelsAreSinglePassAbovePrismalDragDroplet() throws Exception {
        String source = Files.readString(SURFACES);
        assertTrue(source.contains("labels.indices.forEach { index ->"));
        assertTrue(source.contains("PrismalGlassBottomTab("));
        assertTrue(source.contains(") {}"));
        assertTrue(source.contains("ModernTabContents(label, icons[index], index == selected)"));
        assertFalse(source.contains("LocalPrismalBottomTabHighlightedIndex"));
        assertTrue(source.contains("tintDropletContent = false"));
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
    public void disabledGlassKeepsSolidCanvasAndUsesThemeAwareCellContrast() throws Exception {
        String surfaces = Files.readString(SURFACES);

        // The desktop still uses one static opaque background in solid mode,
        // and glass-on retains the original wallpaper gradient/source layers.
        assertTrue(surfaces.contains("if (glassEnabled) {"));
        assertTrue(surfaces.contains("Brush.verticalGradient("));
        assertTrue(surfaces.contains("Modifier.background(lerp(background, Color.Black, 0.06f))"));
        assertTrue(surfaces.contains("if (glassEnabled) Modifier.prismalGlassLayer(backgroundLayer)"));
        assertTrue(surfaces.contains("if (backdrop == null)"));

        // Updated UI contract: dynamic dark-theme Cells intentionally have a
        // subtle fill/rim separation, without any drop shadow or shader fallback.
        assertTrue(surfaces.contains("val solidCardColor = if (darkTheme)"));
        assertTrue(surfaces.contains(".background(solidCardColor)"));
        assertTrue(surfaces.contains(".border(1.dp, cardStroke, cardShape)"));
        assertTrue(surfaces.contains(".clip(cardShape)"));
        assertFalse(surfaces.contains("import androidx.compose.ui.draw.shadow"));
        assertTrue(surfaces.contains("PrismalGlassSurface("));
        assertTrue(surfaces.contains("PrismalGlassBottomTabs("));
    }

    @Test
    public void numericLabelsUseMiuixInputDialogWithoutChangingSliders() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String settings = Files.readString(UI);
        String gboard = Files.readString(GBOARD);
        String dialog = Files.readString(DIALOG_GLASS);
        String sideSlide = Files.readString(SIDE_SLIDE);

        assertTrue(surfaces.contains("internal fun NumericSettingInputDialog("));
        assertTrue(surfaces.contains("WindowDialog("));
        assertTrue(surfaces.contains("top.yukonga.miuix.kmp.basic.TextField("));
        assertTrue(surfaces.contains("keyboardActions = KeyboardActions("));
        assertTrue(surfaces.contains(".clickable(enabled = enabled) { editingValue = true }"));
        assertTrue(surfaces.contains("onConfirm = { next ->"));
        assertTrue(settings.contains("NumericSettingInputDialog("));
        assertTrue(settings.contains("save(next)"));
        assertTrue(settings.contains("ModernGlassSlider("));
        assertTrue(settings.contains("ModernGlassStepper("));
        assertTrue(gboard.contains("valueText = \"$rounded"));
        assertTrue(dialog.contains("valueText = \"$rounded"));
        assertTrue(sideSlide.contains("valueText = \"$secondStageDistancePx px\""));
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
                configRefs.size() >= 229);
        assertTrue(configRefs.contains("ConfigSchema.Debug.LOGGING"));
        assertTrue(configRefs.contains("ConfigSchema.Glass.PRISMAL_SHOW_NORMALS"));

        assertTrue(countDistinctRefs(gboard, "GboardGlassPreferences") >= 11);
        assertTrue(countDistinctRefs(gboard, "MiuiSearchboxGlassPreferences") >= 7);
        assertTrue(countDistinctRefs(widgetCatalog + "\n" + widgetDetail, "WidgetComponentStore") >= 15);
    }

    @Test
    public void manualPassBlurSafetyControlsHiddenWhileAutoGuardRemainsActive() throws Exception {
        String ui = Files.readString(UI);
        String runtimeConfig = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LiquidDockConfig.java"));
        String passBlurView = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"));
        for (String edge : new String[] {"TOP", "BOTTOM", "LEFT", "RIGHT"}) {
            assertFalse(ui.contains("ConfigSchema.Glass.SAMPLING_EXTRA_" + edge));
            assertFalse(runtimeConfig.contains("ConfigSchema.Glass.SAMPLING_EXTRA_" + edge));
        }
        assertFalse(passBlurView.contains("combineAutoGuardAndUserExtra("));
        assertFalse(passBlurView.contains("topSamplingExtraPx"));
        assertTrue(passBlurView.contains("PrismalSampling.requiredGuardPx("));
        assertTrue(ui.contains("采样保护区由渲染器自动计算"));
        assertFalse(ui.contains("\"liquid_edge_band\" ->"));
        assertFalse(ui.contains("\"liquid_highlight_alpha\" ->"));
        assertFalse(ui.contains("\"liquid_recents_prearm_distance\" ->"));
        assertTrue(ui.contains("ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE"));
        assertTrue(ui.contains("ConfigSchema.Glass.PASSBLUR_RENDER_FPS"));
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
