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
    private static final Path OPTION_SPECS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SettingsOptionSpecs.kt");
    private static final Path ANIMATION_PAGES = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/AnimationSettingsPages.kt");
    private static final Path DOCK_WORKSTATION_PAGES = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/DockWorkstationSettingsPages.kt");
    private static final Path GRID_PAGES = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/GridSettingsPages.kt");
    private static final Path GLASS_PAGES = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/GlassSettingsPages.kt");
    private static final Path DOCK_DECORATION_PAGES = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/DockDecorationSettingsPages.kt");
    private static final Path UTILITY_PAGES = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SettingsUtilityPages.kt");
    private static final Path NAVIGATION_MODEL = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SettingsNavigationModel.kt");
    private static final Path CONTROLS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SettingsControls.kt");
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
    private static final Path SEARCHBOX_PAGE = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SearchboxSettingsPage.kt");
    private static final Path DIALOG_GLASS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/DialogGlassSettingsPage.kt");
    private static final Path SIDE_SLIDE = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SideSlideHoldSetting.kt");

    @Test
    public void sliderAndSwitchSpringVisualsOutliveTouchOnlyGlassSampling() throws Exception {
        String slider = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        String toggle = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalToggle.kt"));
        for (String source : new String[]{slider, toggle}) {
            assertTrue(source.contains("pressedScale = 1.5f"));
            assertTrue(source.contains("dampedDragAnimation.modifier"));
            assertTrue(source.contains("translationX ="));
            assertTrue(source.contains("layerBlock = {"));
            assertTrue(source.contains("GuiFrozenPrismalChrome("));
            assertTrue(source.contains("modifier = Modifier.matchParentSize().graphicsLayer {"));
            assertTrue(source.contains("scaleX = dampedDragAnimation.scaleX /"));
            assertTrue(source.contains("scaleY = dampedDragAnimation.scaleY *"));
            assertTrue(source.contains(".then(if (enabled) dampedDragAnimation.modifier else Modifier)"));
        }
        assertTrue(slider.contains("if (sampling) Modifier.drawPrismalGlass("));
        assertTrue(toggle.contains("if (samplingEnabled) Modifier.drawPrismalGlass("));
    }

    @Test
    public void numericSliderPreviewWritesDisplayedStepsButRetainsReleaseSpringAndGridGuard() throws Exception {
        // Static API wiring only; nearest-stop arithmetic is tested through
        // DiscreteSliderStepsTest rather than slicing source to infer behavior.
        String prismal = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        String ui = Files.readString(SURFACES);
        String activity = Files.readString(CONTROLS);
        assertTrue(prismal.contains("onValuePreview: (Float?) -> Unit = {}"));
        assertTrue(prismal.contains("previewState.value(preview)"));
        assertTrue(prismal.contains("commitState.value(nearest)"));
        assertTrue(prismal.contains("previewState.value(null)"));
        assertTrue(ui.contains("val shownValueText = previewValue?.let { valueTextForPreview?.invoke(it) } ?: valueText"));
        assertTrue(ui.contains("onValuePreview = stableSliderPreview"));
        assertTrue(ui.contains("onValueChangeFinished = {"));
        assertTrue(ui.contains("previewCallbackState.value(DiscreteSliderSteps.snap("));
        assertTrue(ui.contains("stableSliderPreview(quantizeState.value(next))"));
        assertTrue(ui.contains("lastLiveStep[0] != next"));
        assertTrue(ui.contains("stableSliderChange(released)"));
        assertTrue(activity.contains("previewLabel ?: displayValue"));
        assertTrue(activity.contains("onValuePreview = { preview ->"));
        assertTrue(activity.contains("save(preview)"));
        assertTrue(activity.contains("previewWriteAllowed?.invoke(preview) == true"));
        assertTrue(activity.contains("if (beforeSave != null) beforeSave(bounded, persist) else persist()"));
        String grid = Files.readString(GRID_PAGES);
        assertTrue(grid.contains("previewWriteAllowed = { proposed ->"));
        assertTrue(grid.contains("GridWidget4x2PreflightPolicy.needsCheck("));
        assertTrue(grid.contains("beforeSave = { proposed, commit ->"));
        String scoped = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ScopedGlassSettingsPage.kt"));
        for (String consumer : new String[]{
                scoped, Files.readString(GBOARD), Files.readString(DIALOG_GLASS),
                Files.readString(SIDE_SLIDE)}) {
            assertTrue(consumer.contains("valueTextForPreview ="));
        }
    }

    @Test
    public void dangerousGridEditsAreCheckedBeforePersistenceAndDisplayLargeWarning() throws Exception {
        String gui = Files.readString(CONTROLS);
        String grid = Files.readString(GRID_PAGES);
        String bridge = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherManualDiscoveryBridge.java"));
        String client = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/GridWidget4x2PreflightClient.java"));

        assertTrue(grid.contains("GridWidget4x2PreflightPolicy.needsCheck(current, target)"));
        assertTrue(grid.contains("GridWidget4x2PreflightClient.start("));
        assertTrue(grid.contains("GridWidget4x2PreflightClient.CLEAR ->"));
        assertTrue(grid.contains("检测到桌面存在 4×2 小组件。行数或列数不能降低到 4 以下"));
        assertFalse(grid.contains("旋转后可能为 2×4"));
        assertTrue(grid.contains("gridCheck[0]?.cancel()"));
        // Grid and common numeric controls snap ONLY on release: continuous
        // pointer movement never runs configuration persistence or preflight.
        assertTrue(grid.contains("steps = DiscreteSliderSteps.forIntegerRange(spec.min, spec.max(context))"));
        String prismalSlider = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        assertTrue(prismalSlider.contains("onDragStopped = {"));
        assertTrue(prismalSlider.contains("updateValue(nextValue)"));
        assertTrue(prismalSlider.contains("val nearest = DiscreteSliderSteps.snap("));
        assertTrue(prismalSlider.contains("snapshotFlow { motion.value }"));
        assertTrue(prismalSlider.contains("commitState.value(nearest)"));
        assertTrue(prismalSlider.contains("releaseJob[0]?.cancel()"));
        assertTrue(gui.contains("DiscreteSliderSteps.forStoragePrecision(spec.min, maxValue, decimalDp)"));
        assertTrue(gui.contains("snapIncrement = if (decimalDp) 0.1f else 1f"));
        String uiComponents = Files.readString(SURFACES);
        assertTrue(uiComponents.contains("snapIncrement = snapIncrement"));
        assertTrue(uiComponents.contains("onValueChangeFinished = {"));
        assertTrue(uiComponents.contains("nativeDraft = next"));
        assertTrue(uiComponents.contains("steps = 0, // Apply quantization only after release"));
        assertTrue(gui.contains("beforeSave: ((Float, () -> Unit) -> Unit)? = null"));
        assertTrue(gui.contains("if (beforeSave != null) beforeSave(bounded, persist) else persist()"));
        assertTrue(grid.contains("minWidth = 164.dp"));
        assertTrue(grid.contains("minHeight = 42.dp"));
        assertTrue(bridge.contains("new String[]{\"container\", \"spanX\", \"spanY\"}"));
        assertTrue(client.contains("private static final long TIMEOUT_MS = 4500L"));
    }

    @Test
    public void rootNavigationUsesFourLightweightDomainsAndRealBackStack() throws Exception {
        String source = Files.readString(UI);
        String routes = Files.readString(NAVIGATION_MODEL);
        assertTrue(routes.contains(
                "ROOT_PAGES = listOf(Page.Home, Page.LayoutHub, Page.GlassHub, Page.MoreHub)"));
        assertTrue(source.contains("var navigationStack by rememberSaveable"));
        assertTrue(source.contains("fun navigateTo(target: Page)"));
        assertTrue(source.contains("fun navigateBack()"));
        assertFalse(source.contains("private fun parentPage("));
    }

    @Test
    public void heavySettingsUseDedicatedHubAndPartitionStructures() throws Exception {
        String source = Files.readString(UI);
        String routes = Files.readString(NAVIGATION_MODEL);

        assertTrue(Files.readString(GRID_PAGES).contains("gridEntries.forEach"));
        assertTrue(Files.readString(DOCK_WORKSTATION_PAGES).contains("dockEntries.forEach"));
        assertTrue(Files.readString(GLASS_PAGES).contains("liquidEntries.forEach"));
        assertTrue(Files.readString(DOCK_WORKSTATION_PAGES).contains("workstationEntries.forEach"));
        assertTrue(routes.contains("animationEntries"));
        assertTrue(routes.contains("highlightEntries"));

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
        assertTrue(Files.readString(GLASS_PAGES).contains("internal fun GlassComponentsPage("));
        assertTrue(Files.readString(GLASS_PAGES).contains("internal fun LiquidSamplingPage("));
        assertTrue(Files.readString(GLASS_PAGES).contains("internal fun LiquidSpecPage("));
    }

    @Test
    public void topAndBottomKeepLivePrismalButBodyGlassesSampleOnlyDuringGestureAndRelease() throws Exception {
        String ui = Files.readString(SURFACES);
        String slider = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        String toggle = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalToggle.kt"));

        assertTrue(ui.contains("val surfaceBackdrop: PrismalBackdrop? = null"));
        assertTrue(ui.contains("LocalTouchPrismalBackdrop provides touchBackdrop"));
        assertTrue(ui.contains("LocalPrismalOverlayBackdrop provides activeOverlayBackdrop"));
        assertTrue(ui.contains("GuiPrismalFlatHeader("));
        assertTrue(ui.contains("PrismalGlassBottomTabs("));
        assertTrue(ui.contains("GuiOnTouchPrismalSlider("));
        assertTrue(ui.contains("GuiOnTouchPrismalToggle("));
        assertTrue(ui.contains("val backdrop = LocalPrismalSurfaceBackdrop.current"));
        assertTrue(slider.contains("if (sampling) Modifier.prismalGlassLayer(trackBackdrop) else Modifier"));
        assertTrue(toggle.contains("if (samplingEnabled) Modifier.prismalGlassLayer(trackBackdrop) else Modifier"));
        assertTrue(slider.contains("if (sampling) Modifier.drawPrismalGlass("));
        assertTrue(toggle.contains("if (samplingEnabled) Modifier.drawPrismalGlass("));
        assertTrue(slider.contains("onDragStarted = {"));
        assertTrue(toggle.contains("onDragStarted = { sampling = true }"));
        assertTrue(slider.contains("val sampling = enabled && (isDragging || isTrackPressed || isSettling)"));
        assertTrue(slider.contains("isSettling = false"));
        assertTrue(slider.contains("commitState.value(nearest)"));
        assertTrue(slider.contains("snapshotFlow { motion.value }"));
        assertTrue(toggle.contains("val samplingEnabled = enabled && sampling"));
        assertTrue(slider.contains("if (enabled) dampedDragAnimation.modifier else Modifier"));
        assertTrue(toggle.contains("if (enabled) dampedDragAnimation.modifier else Modifier"));
    }

    @Test
    public void staticGuiReplaysRealCachedPrismalUnderLiveSettingsContent() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String ui = Files.readString(UI);
        String frozen = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiFrozenPrismalChrome.kt"));
        assertTrue(surfaces.contains("LocalFrozenPrismalBackdrop provides frozenBackdrop"));
        assertTrue(surfaces.contains("GuiFrozenPrismalChrome("));
        assertTrue(surfaces.contains("refractionHeightPx = 16f,"));
        assertTrue(surfaces.contains("refractionAmountPx = 21f,"));
        assertTrue(surfaces.contains("chromaticAberration = 0.28f,"));
        assertTrue(frozen.contains("val layer = rememberGraphicsLayer()"));
        assertTrue(frozen.contains("layer.record(dimensions)"));
        assertTrue(frozen.contains("drawLayer(layer)"));
        assertTrue(frozen.contains("PrismalGlassSurface("));
        assertTrue(frozen.contains("onClick = null,"));
        assertTrue(surfaces.contains("GuiPrismalFlatHeader("));
        assertTrue(surfaces.contains("PrismalGlassBottomTabs("));
        assertTrue(Files.readString(GLASS_PAGES).contains("internal fun GlassIconsPage("));
    }

    @Test
    public void workstationDockGuiOmitsBrokenControlsButKeepsTheirConfigSchema() throws Exception {
        String ui = Files.readString(UI);
        String specs = Files.readString(OPTION_SPECS);
        assertTrue(specs.contains("工作台 Dock 图标垂直偏移"));
        assertFalse(ui.contains("工作台 Dock 图标上间距"));
        assertFalse(ui.contains("工作台 Dock 图标下间距"));
        assertFalse(ui.contains("工作台 Dock 长度偏移"));
        assertTrue(specs.contains("ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET,"));
        assertFalse(specs.contains("IntSpec(ConfigSchema.Workstation.DOCK_WIDTH_OFFSET,"));
        assertFalse(specs.contains("IntSpec(ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET,"));
    }

    @Test
    public void groupedPrismalCardsBoundOffscreenLayersWithoutNestedScroll() throws Exception {
        String ui = Files.readString(CONTROLS);
        assertTrue(ui.contains("internal fun LazyListScope.groupedIntSettings("));
        assertTrue(ui.contains("items(specs.chunked(3), key = { group ->"));
        assertTrue(ui.contains("group.forEachIndexed { index, spec ->"));
        assertTrue(Files.readString(GLASS_PAGES).contains("groupedIntSettings(specs, prefs, masterEnabled && liquidEnabled)"));
        assertTrue(Files.readString(DOCK_WORKSTATION_PAGES).contains("groupedIntSettings(dockSpecs, prefs, masterEnabled && dockEnabled)"));
        assertTrue(Files.readString(GLASS_PAGES).contains("item(key = \"icons-glass-toggles\")"));
        assertTrue(Files.readString(GLASS_PAGES).contains("item(key = \"icons-glass-geometry\")"));
        assertTrue(Files.readString(GLASS_PAGES).contains("item(key = \"icons-glass-highlights\")"));
        assertTrue(Files.readString(GLASS_PAGES).contains("internal fun GlassIconsPage("));
        assertTrue(Files.readString(GLASS_PAGES).contains("DenseSettingsList(\n        padding,\n        stringResource(R.string.page_glass_icons)"));
    }

    @Test
    public void denseSettingsScrollTheWholePageWithLazyMovingGlassCells() throws Exception {
        String ui = Files.readString(CONTROLS);
        String surfaces = Files.readString(SURFACES);

        assertTrue(ui.contains("internal fun DenseSettingsList("));
        assertTrue(ui.contains("content: LazyListScope.() -> Unit"));
        assertTrue(ui.contains("if (!summary.isNullOrBlank()) item(key = \"dense-page-summary\")"));
        assertTrue(ui.contains("contentPadding = PaddingValues("));
        assertTrue(ui.contains("verticalArrangement = Arrangement.spacedBy(10.dp)"));
        assertTrue(Files.readString(DOCK_WORKSTATION_PAGES).contains("items(specs, key = { it.key })"));
        assertTrue(Files.readString(DOCK_DECORATION_PAGES).contains("item(key = \"stroke-colors-title\")"));
        assertTrue(ui.contains("SettingsCard {"));
        assertTrue(ui.contains("internal fun SettingsList("));
        // Each child is a lazy item with its own Prismal glass card, not a
        // viewport-height static card enclosing an independently scrolling list.
        assertFalse(ui.contains(".weight(1f)\n                .padding(horizontal = 14.dp)"));
        assertTrue(surfaces.contains("GuiOnTouchPrismalSlider("));
        assertTrue(surfaces.contains("PrismalGlassStepper("));
    }

    @Test
    public void prismalGesturesReadLatestStateThroughStableBridges() throws Exception {
        String surfaces = Files.readString(SURFACES);
        assertTrue(surfaces.contains("val stableSelected = remember { { selectedState.value } }"));
        assertTrue(surfaces.contains("onSelect = stableToggleChange,"));
        assertTrue(surfaces.contains("val stableSliderChange: (Float) -> Unit = remember {"));
        assertTrue(surfaces.contains("onValueChange = stableSliderChange,"));
        assertTrue(surfaces.contains("val stableSelectedIndex = remember { { selected } }"));
        assertTrue(surfaces.contains("onTabSelected = stableTabChange,"));
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
    public void singlePreferenceObserverRefreshesOnlyChangedControls() throws Exception {
        String ui = Files.readString(UI);
        String controls = Files.readString(CONTROLS);
        assertTrue(ui.contains("LocalSettingsPreferenceRevisions"));
        assertTrue(ui.contains("preferences.registerOnSharedPreferenceChangeListener(listener)"));
        assertTrue(ui.contains("preferences.unregisterOnSharedPreferenceChangeListener(listener)"));
        assertTrue(controls.contains("val revision = LocalSettingsPreferenceRevisions.current[key]"));
        assertTrue(controls.contains("LaunchedEffect(spec.key, maxValue, storedRevision)"));
        assertTrue(controls.contains("var value by remember(spec.key, maxValue)"));
    }

    @Test
    public void scopedGlassCellsStaySeparatedAndResetActionsAreCenteredEqually() throws Exception {
        String scoped = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ScopedGlassSettingsPage.kt"));
        String ui = Files.readString(UI);
        assertTrue(scoped.contains("verticalArrangement = Arrangement.spacedBy(8.dp)"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("horizontalArrangement = Arrangement.spacedBy(12.dp)"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("modifier = Modifier.weight(1f)"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("minHeight = 42.dp"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("destructive = true"));
    }

    @Test
    public void defaultPresetRequiresConfirmationAndRecreatesSettingsState() throws Exception {
        String ui = Files.readString(UI);
        assertTrue(Files.readString(UTILITY_PAGES).contains("confirmDefaultReset = true"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("WindowDialog("));
        assertTrue(Files.readString(UTILITY_PAGES).contains("确认恢复默认配置"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("activity.recreate()"));
    }

    @Test
    public void widgetDirectoryDoesNotComposeAllGroupsInOneLazyItem() throws Exception {
        String catalog = Files.readString(WIDGET_COMPONENTS);
        assertTrue(catalog.contains("groups.chunked(6).forEach"));
        assertTrue(catalog.contains("item(key = \"widget-groups:"));
    }

    @Test
    public void separateApplicationPagesExposeScopedFullOpticalSettings() throws Exception {
        String shell = Files.readString(UI);
        String gboard = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GboardSettingsPages.kt"));
        String search = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/SearchboxSettingsActivity.kt"));
        String dialog = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/DialogGlassSettingsPage.kt"));
        String page = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ScopedGlassSettingsPage.kt"));
        assertTrue(shell.contains("Page.GboardAll -> ScopedGlassSettingsPage("));
        assertTrue(shell.contains("Page.DialogAll -> ScopedGlassSettingsPage("));
        assertTrue(gboard.contains("调整 Gboard 专属的完整 Prismal 光学参数"));
        assertTrue(Files.readString(SEARCHBOX_PAGE).contains("调整系统搜索专属的完整 Prismal 光学参数"));
        assertTrue(dialog.contains("调整桌面对话弹窗专属 Prismal 光学参数"));
        assertTrue(search.contains("ScopedGlassOptics.SEARCHBOX"));
        assertTrue(page.contains("ScopedGlassOptics.key(scope, d.config)"));
        assertTrue(page.contains("恢复全部参数继承"));
    }

    @Test
    public void parameterRecompositionDoesNotRereadStoredInitialState() throws Exception {
        String ui = Files.readString(CONTROLS);

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
        assertTrue(surfaces.contains("val fillColor = if (destructive) Color(0xFFD73333)"));
        assertTrue(surfaces.contains("else lerp(colors.surface, colors.onSurface,"));
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
        assertTrue(source.contains("GuiOnTouchPrismalToggle"));
        assertTrue(source.contains("GuiOnTouchPrismalSlider"));
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
    public void bothHeaderButtonsKeepShadowsBeyondMiuixContentBounds() throws Exception {
        String source = Files.readString(SURFACES);
        String bar = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiUnclippedSmallTopAppBar.kt"));

        assertTrue(source.contains("GuiUnclippedSmallTopAppBar("));
        assertFalse(source.contains("import top.yukonga.miuix.kmp.basic.SmallTopAppBar"));
        assertTrue(source.contains("navigationIcon = {"));
        assertTrue(source.contains("actions = actions,"));
        assertTrue(bar.contains("WindowInsets.systemBars.only(WindowInsetsSides.Top)"));
        assertTrue(bar.contains("WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)"));
        assertTrue(bar.contains("WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal)"));
        assertTrue(bar.contains("TopAppBarDefaults.SmallTopAppBarCenterHeight.roundToPx()"));
        assertTrue(bar.contains("TopAppBarDefaults.CollapsedHeight.roundToPx()"));
        assertTrue(bar.contains("actionIcons.placeRelative("));
        assertTrue(bar.contains("navigation.placeRelative("));
        assertFalse("The content-area clip amputates BOTH upper Prismal shadows",
                bar.contains(".clipToBounds()"));
        assertTrue(source.contains("TOP_BAR_ACTION_SHADOW_ROOM"));
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
        // The physical top edge is opaque and theme-adaptive without adding
        // another backdrop capture or moving the MIUIX toolbar.
        assertTrue(header.contains("WindowInsets.statusBars.asPaddingValues().calculateTopPadding()"));
        assertTrue(header.contains("statusBarHeight > 0.dp"));
        assertTrue(header.contains("statusBarHeight + 12.dp"));
        assertTrue(header.contains("0f to statusBarEdgeColor"));
        assertTrue(header.contains("1f to statusBarEdgeColor.copy(alpha = 0f)"));
        assertTrue(surfaces.contains("statusBarEdgeColor = if (background.luminance() < 0.5f) Color.Black else Color.White"));
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
        assertTrue(Files.readString(CONTROLS).contains("internal fun PageHeader("));
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
    public void numericStepperAndResetKeepNativePrismalWhileOtherCellsStayStatic() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String ui = Files.readString(CONTROLS);
        assertTrue(surfaces.contains("val surfaceBackdrop: PrismalBackdrop? = null"));
        assertTrue(surfaces.contains("LocalTouchPrismalBackdrop provides touchBackdrop"));
        assertTrue(surfaces.contains("val backdrop = if (prismalNumericAction) {"));
        assertTrue(surfaces.contains("prismalNumericAction: Boolean = false"));
        assertTrue(surfaces.contains("val backdrop = LocalTouchPrismalBackdrop.current\n    if (backdrop != null) {\n        PrismalGlassStepper("));
        assertTrue(surfaces.contains("PrismalGlassButton("));
        assertTrue(ui.contains("prismalNumericAction = true,"));
        assertTrue(ui.contains("ModernGlassStepper("));
    }

    @Test
    public void prismalSliderObservesExternalStepperAndResetUpdates() throws Exception {
        String surfaces = Files.readString(SURFACES);
        assertTrue(surfaces.contains("val currentValue by rememberUpdatedState(value)"));
        assertTrue(surfaces.contains("val stableSliderValue = remember { { currentValue } }"));
        assertTrue(surfaces.contains("value = stableSliderValue,"));
    }

    @Test
    public void appearanceAndMoreRootNavigationStayUserFacingAndFocused() throws Exception {
        String source = Files.readString(NAVIGATION_MODEL);
        String zh = Files.readString(STRINGS_ZH);

        assertTrue(zh.contains("<string name=\"tab_glass\">外观</string>"));
        assertTrue(source.contains(
                "internal val moreEntries = listOf(\n"
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

        assertTrue(Files.readString(OPTION_SPECS).contains("适用于当前实际网格列数"));
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
        String optionSpecs = Files.readString(OPTION_SPECS);
        String animationPages = Files.readString(ANIMATION_PAGES);
        String dockWorkstationPages = Files.readString(DOCK_WORKSTATION_PAGES);
        String gridPages = Files.readString(GRID_PAGES);
        String glassPages = Files.readString(GLASS_PAGES);
        String decorationPages = Files.readString(DOCK_DECORATION_PAGES);
        String utilityPages = Files.readString(UTILITY_PAGES);
        String controls = Files.readString(CONTROLS);
        String navigation = Files.readString(NAVIGATION_MODEL);
        String gboard = Files.readString(GBOARD);
        String search = Files.readString(SEARCHBOX);
        String searchPage = Files.readString(SEARCHBOX_PAGE);
        String widgetCatalog = Files.readString(WIDGET_COMPONENTS);
        String widgetDetail = Files.readString(WIDGET_DETAIL);
        String dialog = Files.readString(DIALOG_GLASS);
        String sideSlide = Files.readString(SIDE_SLIDE);
        String recent = Files.readString(RECENT_BLACKLIST);

        Set<String> configRefs = new HashSet<>();
        Matcher configMatcher = Pattern.compile("ConfigSchema(?:\\.[A-Za-z0-9_]+){2,}")
                .matcher(compose + "\n" + optionSpecs + "\n" + animationPages + "\n"
                        + dockWorkstationPages + "\n" + gridPages + "\n" + glassPages + "\n"
                        + decorationPages + "\n" + utilityPages + "\n"
                        + controls + "\n" + navigation + "\n"
                        + gboard + "\n" + search + "\n" + searchPage + "\n"
                        + widgetCatalog + "\n" + widgetDetail + "\n"
                        + dialog + "\n" + sideSlide + "\n" + recent);
        while (configMatcher.find()) configRefs.add(configMatcher.group());
        assertTrue("original GUI ConfigSchema coverage must not shrink: " + configRefs.size(),
                configRefs.size() >= 229);
        assertTrue(configRefs.contains("ConfigSchema.Debug.LOGGING"));
        assertTrue(configRefs.contains("ConfigSchema.Glass.PRISMAL_SHOW_NORMALS"));

        assertTrue(countDistinctRefs(gboard, "GboardGlassPreferences") >= 11);
        assertTrue(countDistinctRefs(searchPage, "MiuiSearchboxGlassPreferences") >= 7);
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
        assertTrue(Files.readString(GLASS_PAGES).contains("采样保护区由渲染器自动计算"));
        assertFalse(ui.contains("\"liquid_edge_band\" ->"));
        assertFalse(ui.contains("\"liquid_highlight_alpha\" ->"));
        assertFalse(ui.contains("\"liquid_recents_prearm_distance\" ->"));
        assertTrue(Files.readString(OPTION_SPECS).contains("ConfigSchema.Glass.PASSBLUR_CAPTURE_SCALE"));
        assertTrue(Files.readString(OPTION_SPECS).contains("ConfigSchema.Glass.PASSBLUR_RENDER_FPS"));
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
