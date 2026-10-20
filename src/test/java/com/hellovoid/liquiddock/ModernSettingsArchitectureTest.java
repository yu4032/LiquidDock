package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;
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
    private static final Path BOTTOM_NAV = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SettingsBottomNavigation.kt");
    private static final Path RESTART_DIALOG = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/SettingsRestartScopesDialog.kt");
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
            assertTrue(source.contains(".guiIdleThumbChrome("));
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
    public void searchPullGestureBelongsOnlyToRootPagesAndUsesAnimatedTransitions() throws Exception {
        String source = Files.readString(UI);
        String search = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/SettingsSearch.kt"));
        assertTrue(source.contains("if (root && !searchOpen && !showRestartScopes)"));
        assertTrue(source.contains("Modifier.nestedScroll(rememberSettingsPullDownSearch(::openSearch))"));
        assertTrue(source.contains("searchTransition.AnimatedContent("));
        // Bottom navigation must share the same display-frame-driven Transition:
        // independent AnimatedVisibility/tween timelines can visually diverge.
        assertTrue(source.contains("searchTransition.AnimatedVisibility("));
        assertFalse(source.contains("visible = !searchOpen,"));
        assertFalse(source.contains("fadeIn(tween(210)"));
        assertFalse(source.contains("fadeOut(tween(160)"));
        assertTrue(source.contains("animationSpec = searchSlideSpec"));
        assertTrue(source.contains("fadeIn(searchFadeSpec)"));
        assertTrue(source.contains("fadeOut(searchFadeSpec)"));

        assertTrue(source.contains("slideInVertically("));
        assertTrue(source.contains("slideOutVertically("));
        assertTrue(source.contains("focusOnOpen = searchTransition.currentState && !searchTransition.isRunning"));
        assertTrue(search.contains("LaunchedEffect(focusOnOpen)"));
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
    public void topAndBottomKeepLivePrismalButBodyGlassesSampleOnlyDuringGestureAndRelease() throws Exception {
        String ui = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);
        String slider = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        String toggle = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalToggle.kt"));

        assertTrue(ui.contains("val surfaceBackdrop: PrismalBackdrop? = null"));
        assertTrue(ui.contains("LocalTouchPrismalBackdrop provides touchBackdrop"));
        assertTrue(ui.contains("LocalPrismalOverlayBackdrop provides activeOverlayBackdrop"));
        assertTrue(ui.contains("GuiPrismalFlatHeader("));
        assertTrue(bottom.contains("PrismalGlassBottomTabs("));
        assertTrue(ui.contains("GuiOnTouchPrismalSlider("));
        assertTrue(ui.contains("GuiOnTouchPrismalToggle("));
        assertTrue(ui.contains("val backdrop = LocalPrismalOverlayBackdrop.current ?: LocalPrismalSurfaceBackdrop.current"));
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
    public void idleThumbDrawableCannotCrashSettingsStartup() throws Exception {
        String chrome = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiSolidChrome.kt"));
        String slider = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        String toggle = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalToggle.kt"));
        assertFalse(chrome.contains("R.drawable.gui_depth_shadow_"));
        assertFalse(chrome.contains("guiBakedDepthShadow("));
        assertTrue(chrome.contains("loadGuiThumbOrSolidFallback("));
        assertTrue(chrome.contains("if (loaded != null) return loaded.mutate()"));
        assertTrue(chrome.contains("catch (error: RuntimeException)"));
        assertTrue(chrome.contains("return GradientDrawable().apply"));
        assertFalse(chrome.contains("requireNotNull(ContextCompat.getDrawable"));
        // ShapeDrawable intrinsic size is -1; measured bounds must be sufficient.
        assertTrue(chrome.contains("if (width > 0 && height > 0)"));
        assertTrue(chrome.contains("palette.thumbDrawable.setBounds(0, 0, width, height)"));
        assertTrue(chrome.contains("palette.thumbDrawable.draw(native)"));
        assertTrue(slider.contains(".guiIdleThumbChrome(presetChrome)"));
        assertTrue(toggle.contains(".guiIdleThumbChrome(presetChrome)"));
        String thumb = Files.readString(Path.of(
                "src/main/res/drawable/gui_material_thumb.xml"));
        assertTrue(thumb.contains("<solid android:color=\"#FFFFFFFF\"/>"));
    }









    @Test
    public void workstationDockGuiOmitsBrokenControlsButKeepsTheirConfigSchema() throws Exception {
        String ui = Files.readString(UI);
        String specs = Files.readString(OPTION_SPECS);
        assertTrue(specs.contains("整个 Dock 垂直平移"));
        assertTrue(specs.contains("横屏 · Dock 图标垂直偏移"));
        assertTrue(specs.contains("竖屏 · Dock 图标垂直偏移"));
        assertFalse(specs.contains("Dock 图标水平偏移"));
        assertFalse(ui.contains("工作台 Dock 图标上间距"));
        assertFalse(ui.contains("工作台 Dock 图标下间距"));
        assertFalse(ui.contains("工作台 Dock 长度偏移"));
        assertTrue(specs.contains("ConfigSchema.Workstation.DOCK_ICON_TOP_OFFSET,"));
        assertFalse(specs.contains("DOCK_WIDTH_OFFSET"));
        assertFalse(specs.contains("IntSpec(ConfigSchema.Workstation.DOCK_ICON_BOTTOM_OFFSET,"));
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
        assertTrue(surfaces.contains("GuiStaticRepeatStepButton("));
    }

    @Test
    public void prismalGesturesReadLatestStateThroughStableBridges() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);
        assertTrue(surfaces.contains("val stableSelected = remember { { selectedState.value } }"));
        assertTrue(surfaces.contains("onSelect = stableToggleChange,"));
        assertTrue(surfaces.contains("val stableSliderChange: (Float) -> Unit = remember {"));
        assertTrue(surfaces.contains("onValueChange = stableSliderChange,"));
        assertTrue(bottom.contains("val stableSelectedIndex = remember { { selected } }"));
        assertTrue(bottom.contains("onTabSelected = stableTabChange,"));
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
    public void defaultPresetRequiresConfirmationAndRecreatesSettingsState() throws Exception {
        String ui = Files.readString(UI);
        assertTrue(Files.readString(UTILITY_PAGES).contains("confirmDefaultReset = true"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("AnimatedSettingsWindowDialog("));
        assertTrue(Files.readString(UTILITY_PAGES).contains("确认恢复默认配置"));
        assertTrue(Files.readString(UTILITY_PAGES).contains("activity.recreate()"));
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
    public void modernShellRetainsPrismalControlsWithSolidHeader() throws Exception {
        String source = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);
        String build = Files.readString(BUILD);
        String header = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalFlatHeader.kt"));

        assertTrue(build.contains("com.github.styropyr0:PrismalAGSL:v1.0.4"));
        assertTrue(build.contains("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4"));
        assertFalse("no unused MIUIX blur implementation", build.contains("miuix-blur-android"));
        assertFalse(source.contains("PrismalGlassSurface("));
        assertTrue(source.contains("PrismalGlassButton"));
        assertTrue(bottom.contains("PrismalGlassBottomTabs"));
        assertTrue(bottom.contains("PrismalGlassBottomTab"));
        assertTrue(source.contains("GuiOnTouchPrismalToggle"));
        assertTrue(source.contains("GuiOnTouchPrismalSlider"));
        assertFalse(source.contains("PrismalGlassStepper("));
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
    public void bothBarsShareTheOriginalPrismalBackdropWithoutSecondRecording() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);
        String header = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalFlatHeader.kt"));

        assertTrue(surfaces.contains("rememberPrismalMergedSource(backgroundLayer, screenLayer)"));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(backgroundLayer)"));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(screenLayer)"));
        assertTrue(surfaces.contains("LocalPrismalOverlayBackdrop provides activeOverlayBackdrop"));
        assertTrue(bottom.contains("val backdrop = LocalPrismalOverlayBackdrop.current"));
        assertTrue(surfaces.contains("backdrop = activeOverlayBackdrop,"));
        // Static architecture audit; actual modifier capture order is validated
        // by real rendering rather than prohibited source-order inference.
        assertTrue(surfaces.contains("Modifier.background(background)"));
        assertTrue(bottom.contains("PrismalGlassBottomTabs("));
        assertTrue(header.contains("PrismalBackdrop"));
        assertFalse(surfaces.contains("rememberLayerBackdrop {"));
        assertFalse(surfaces.contains("Modifier.layerBackdrop(barBackdrop)"));
        assertFalse(surfaces.contains("GuiPrismalMiuixBackdrop"));
    }

    @Test
    public void bottomTabSelectionUsesNativeCapsuleClippedHitTargets() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);

        assertTrue(bottom.contains("PrismalGlassBottomTabs("));
        assertTrue(bottom.contains("PrismalGlassBottomTab("));
        assertTrue(bottom.contains("labels.indices.forEach { index ->"));
        assertTrue(bottom.contains("active = index == candidate"));
        assertTrue(bottom.contains("PrismalGlassBottomTab("));
        assertTrue(bottom.contains("LocalPrismalBottomTabHighlightedIndex.current"));
        assertTrue(bottom.contains("indication = null"));
        assertFalse(bottom.contains("Modifier.matchParentSize()\n                    .padding(4.dp)"));
    }





    @Test
    public void bottomGlyphsLiveInsideNativePrismalTabSampler() throws Exception {
        String source = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);
        assertTrue(bottom.contains("labels.indices.forEach { index ->"));
        assertTrue(bottom.contains("PrismalGlassBottomTab("));
        assertTrue(bottom.contains("ModernTabContents("));
        assertTrue(bottom.contains("active = index == candidate"));
        assertTrue(bottom.contains("LocalPrismalBottomTabHighlightedIndex"));
        assertTrue(bottom.contains("tintDropletContent = true"));
        assertTrue(bottom.contains("dropletContentTint = MiuixTheme.colorScheme.primary"));
    }









    @Test
    public void prismalSliderObservesExternalStepperAndResetUpdates() throws Exception {
        String surfaces = Files.readString(SURFACES);
        assertTrue(surfaces.contains("val currentValue by rememberUpdatedState(value)"));
        assertTrue(surfaces.contains("val stableSliderValue = remember { { currentValue } }"));
        assertTrue(surfaces.contains("value = stableSliderValue,"));
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
        assertTrue(surfaces.contains(".guiExactPrismalCornerChrome("));

        assertTrue(search.contains("SETTINGS_UI_GLASS_ENABLED"));
        assertTrue(search.contains("glassEnabled = glassEnabled"));
        assertTrue(widget.contains("SETTINGS_UI_GLASS_ENABLED"));
        assertTrue(widget.contains("glassEnabled = glassEnabled"));
        assertTrue(zh.contains("<string name=\"settings_glass_effect\">IOS玻璃效果</string>"));
    }



    @Test
    public void disabledGlassKeepsSolidCanvasAndUsesThemeAwareCellContrast() throws Exception {
        String surfaces = Files.readString(SURFACES);

        // Both GUI background modes now draw a single opaque theme color.
        // The header/footer still share the original Prismal source layers.
        assertTrue(surfaces.contains("if (glassEnabled) Modifier.background(background)"));
        assertFalse(surfaces.contains("Brush.verticalGradient("));
        assertTrue(surfaces.contains("Modifier.background(presetChrome.canvasWithoutGlass)"));
        assertTrue(surfaces.contains("if (glassEnabled) Modifier.prismalGlassLayer(backgroundLayer)"));
        assertTrue(surfaces.contains("if (backdrop == null)"));

        // Updated UI contract: dynamic dark-theme Cells intentionally have a
        // subtle fill/rim separation, without any drop shadow or shader fallback.
        assertTrue(surfaces.contains("val solidCardColor = if (darkTheme)"));
        assertTrue(surfaces.contains(".background(solidCardColor, cardShape)"));
        assertTrue(surfaces.contains(".border(1.dp, cardStroke, cardShape)"));
        assertTrue(surfaces.contains(".clip(cardShape)"));
        assertFalse(surfaces.contains("import androidx.compose.ui.draw.shadow"));
        assertFalse(surfaces.contains("PrismalGlassSurface("));
        assertTrue(Files.readString(BOTTOM_NAV).contains("PrismalGlassBottomTabs("));
    }

    @Test
    public void numericLabelsUseMiuixInputDialogWithoutChangingSliders() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String settings = Files.readString(CONTROLS);
        String gboard = Files.readString(GBOARD);
        String dialog = Files.readString(DIALOG_GLASS);
        String sideSlide = Files.readString(SIDE_SLIDE);

        assertTrue(surfaces.contains("internal fun NumericSettingInputDialog("));
        assertTrue(surfaces.contains("AnimatedSettingsWindowDialog("));
        assertTrue(surfaces.contains("top.yukonga.miuix.kmp.basic.TextField("));
        assertTrue(surfaces.contains("keyboardActions = KeyboardActions("));
        // Both slider paths must use the fixed-width, single-line readout and preserve tap-to-edit.
        assertTrue(surfaces.contains("private val SliderValueReadoutWidth = 92.dp"));
        assertTrue(surfaces.contains(".width(SliderValueReadoutWidth)"));
        assertTrue(surfaces.contains("maxLines = 1"));
        assertTrue(surfaces.contains("onClick = { editingValue = true }"));
        assertTrue(settings.contains("SliderValueReadout("));
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
        // The enforced OS4 edge replaces legacy specular/rim/plain highlight controls.
        // Keep the active GUI catalog size stable rather than counting retired keys.
        assertTrue("active GUI ConfigSchema coverage must not shrink: " + configRefs.size(),
                configRefs.size() >= 215);
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

    @Test
    public void glassSettingsDialogsUseNativeWindowMotionWithoutClippedChildLayers() throws Exception {
        String motion = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/SettingsDialogMotion.kt"));
        String reset = Files.readString(UTILITY_PAGES);
        String grid = Files.readString(GRID_PAGES);
        String numeric = Files.readString(SURFACES);
        String restart = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/SettingsRestartScopesDialog.kt"));

        assertTrue(reset.contains("AnimatedSettingsWindowDialog("));
        assertTrue(grid.contains("AnimatedSettingsWindowDialog("));
        assertTrue(numeric.contains("AnimatedSettingsWindowDialog("));
        assertTrue(motion.contains("WindowDialog("));
        // Upstream MIUIX WindowDialog already owns entry/exit spring and window
        // retention. A second fading/scaling child layer clips Prismal rims.
        assertTrue(motion.contains("show = show,"));
        assertTrue(motion.contains("Column(content = content)"));
        assertFalse(motion.contains("Modifier.graphicsLayer"));
        assertFalse(motion.contains("updateTransition("));
        assertTrue(restart.contains("AnimatedVisibility("));
        assertTrue(restart.contains("enter = fadeIn("));
        assertTrue(restart.contains("exit = fadeOut("));
        assertTrue(restart.contains("graphicsLayer { scaleX = panelScale; scaleY = panelScale }"));
        assertTrue(restart.contains("GuiOnTouchPrismalToggle("));
        assertFalse(motion.contains("rememberPrismalGlassLayer"));
        assertFalse(motion.contains("PixelCopy"));
    }

    @Test
    public void prismalBottomTabsSampleGlyphsWithoutChangingOpticalParameters() throws Exception {
        String bottom = Files.readString(BOTTOM_NAV);
        assertTrue(bottom.contains("PrismalGlassBottomTabs("));
        assertTrue(bottom.contains("PrismalGlassBottomTab("));
        assertTrue(bottom.contains("LocalPrismalBottomTabHighlightedIndex.current"));
        assertTrue(bottom.contains("val candidate = highlighted().coerceIn(0, labels.lastIndex)"));
        assertTrue(bottom.contains("ModernTabContents("));
        assertTrue(bottom.contains("active = index == candidate"));
        assertTrue(bottom.contains("draggingCandidate = candidate != selected"));
        assertTrue(bottom.contains("val contentColor by animateColorAsState("));
        assertTrue(bottom.contains("val contentScale by animateFloatAsState("));
        assertTrue(bottom.contains("tintDropletContent = true"));
        assertTrue(bottom.contains("dropletContentTint = MiuixTheme.colorScheme.primary"));
        assertTrue(bottom.contains("icon = icons[index]"));
        assertTrue(bottom.contains("label = labels[index]"));
        // The native PrismalGlassBottomTabs implementation records its tab
        // children into the existing hidden sample layer. Do not add a
        // parallel overlaid glyph Row, custom lens, or a second capture.
        assertTrue(bottom.contains("val candidate = highlighted().coerceIn(0, labels.lastIndex)\n                            ModernTabContents("));
        assertFalse(bottom.contains("// Draw labels and icons once above the droplet"));
        assertFalse(bottom.contains("SideEffect {"));
        assertFalse(bottom.contains("highlightedIndex.intValue"));
        assertFalse(bottom.contains("rememberPrismalGlassLayer("));
        assertFalse(bottom.contains("drawPrismalGlass("));
        assertFalse(bottom.contains("prismalLens("));
        assertFalse(bottom.contains("rememberPrismalWrappedSource("));
        assertFalse(Files.exists(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiSampledPrismalBottomTabs.kt")));
    }

}
