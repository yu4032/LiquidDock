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
                "LiquidShadow", "LiquidSampling",
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
        String glassPages = Files.readString(GLASS_PAGES);
        String navModel = Files.readString(NAVIGATION_MODEL);
        String options = Files.readString(OPTION_SPECS);
        assertTrue(glassPages.contains("internal fun LiquidLightingPage("));
        assertTrue(glassPages.contains("groupedIntSettings(liquidLightingSpecs, prefs, enabled)"));
        assertTrue(glassPages.contains("groupedIntSettings(liquidOs4Specs, prefs, enabled)"));
        assertTrue(source.contains("Page.LiquidLighting -> LiquidLightingPage("));
        assertFalse(source.contains("Page.LiquidOs4"));
        assertFalse(navModel.contains("Page.LiquidOs4"));
        assertFalse(navModel.contains("LiquidOs4("));
        assertTrue(options.contains("internal val liquidOs4Specs = liquidSpecsFor("));
        assertTrue(options.contains("ConfigSchema.Glass.OS4_REFLECTION_STRENGTH"));
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
    public void staticGuiUsesFixedPaletteAndSinglePrismalOutlineWithoutBakedImages()
            throws Exception {
        String surfaces = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);
        String chrome = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiSolidChrome.kt"));
        String slider = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        String toggle = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalToggle.kt"));

        assertTrue(surfaces.contains("LocalStaticGlassChrome provides glassEnabled"));
        assertTrue(surfaces.contains("LocalGuiPresetChromePalette provides presetChrome"));
        assertTrue(surfaces.contains("Modifier.guiExactPrismalCornerChrome(GuiSolidChromeStyle.CARD, presetChrome, cardShape)"));
        assertTrue(surfaces.contains("val cardShape = PrismalRoundedRectangle(24.dp)"));
        assertTrue(surfaces.contains("val buttonShape = PrismalRoundedRectangle(minHeight / 2)"));
        assertTrue(chrome.contains("internal fun Modifier.guiExactPrismalCornerChrome("));
        assertTrue(chrome.contains("this.background(fill, shape).guiStaticSpecularRim(shape, palette)"));
        assertTrue(chrome.contains("val cardFill = colorResource(R.color.gui_preset_card_fill)"));
        assertTrue(chrome.contains("val pillFill = colorResource(R.color.gui_preset_pill_fill)"));
        assertTrue(chrome.contains("GuiSolidChromeStyle.THUMB -> palette.thumbFill"));
        assertTrue(chrome.contains("Brush.sweepGradient("));
        assertTrue(chrome.contains("R.drawable.gui_material_thumb"));
        assertFalse(chrome.contains("R.drawable.gui_material_card"));
        assertFalse(chrome.contains("R.drawable.gui_material_pill"));
        assertFalse(chrome.contains("drawBakedNineSlice("));
        assertFalse(chrome.contains("BitmapFactory"));
        assertFalse(chrome.contains("ScreenCapture"));
        assertFalse(chrome.contains("PixelCopy"));
        assertFalse(chrome.contains("PrismalGlassSurface("));
        assertFalse(surfaces.contains("GuiFrozenPrismalChrome("));
        assertFalse(surfaces.contains("PrismalGlassSurface("));

        for (String source : new String[]{slider, toggle}) {
            assertTrue(source.contains(".guiIdleThumbChrome(presetChrome)"));
            assertTrue(source.contains("PrismalSpecular.Ambient.copy("));
            assertTrue(source.contains("alpha = progress"));
            assertFalse(source.contains("Modifier.guiThumbHighlight("));
        }
        String light = Files.readString(Path.of(
                "src/main/res/values/gui_preset_material_colors.xml"));
        String dark = Files.readString(Path.of(
                "src/main/res/values-night/gui_preset_material_colors.xml"));
        assertTrue(light.contains("<color name=\"gui_preset_canvas\">#F0F1F6</color>"));
        assertTrue(dark.contains("<color name=\"gui_preset_canvas\">#1B2028</color>"));
        assertTrue(light.contains("<color name=\"gui_preset_card_fill\">#F4F4FC</color>"));
        assertTrue(light.contains("<color name=\"gui_preset_pill_fill\">#F2F2FA</color>"));
        assertTrue(light.contains("<color name=\"gui_preset_thumb_fill\">#FFFFFF</color>"));
        assertTrue(dark.contains("<color name=\"gui_preset_thumb_fill\">#FFFFFF</color>"));
        assertFalse(Files.exists(Path.of("src/main/res/drawable-nodpi/gui_material_card.png")));
        assertFalse(Files.exists(Path.of("src/main/res/drawable-nodpi/gui_material_pill.png")));
        assertTrue(surfaces.contains("GuiPrismalFlatHeader("));
        assertTrue(bottom.contains("PrismalGlassBottomTabs("));
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
    public void staticGuiRestoresNativePrismalDepthShadowWithoutGlassCapture() throws Exception {
        String chrome = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiSolidChrome.kt"));
        String nativeShadow = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalNativeDepthShadow.kt"));
        String surfaces = Files.readString(SURFACES);
        String stepper = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticRepeatStepButton.kt"));
        String slider = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalSlider.kt"));
        String toggle = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalToggle.kt"));

        // Keep the original single Prismal-outline specular result unchanged.
        assertTrue(chrome.contains("internal fun Modifier.guiStaticSpecularRim("));
        assertTrue(chrome.contains("this.background(fill, shape).guiStaticSpecularRim(shape, palette)"));
        assertFalse(chrome.contains("guiStaticSoftShadow("));
        assertFalse(chrome.contains("val shadowColors = opacities.map"));
        assertFalse(chrome.contains("R.drawable.gui_depth_shadow_"));
        assertFalse(chrome.contains("requireNotNull(ContextCompat.getDrawable"));

        // Clone only the upstream shadow draw modifier (internal to upstream),
        // not the entire glass surface, blur sampling, or AGSL optical effects.
        assertTrue(nativeShadow.contains("internal fun Modifier.guiNativePrismalDepthShadow("));
        assertTrue(nativeShadow.contains("PrismalDepthShadow.Default"));
        assertTrue(nativeShadow.contains("CompositingStrategy.Offscreen"));
        assertTrue(nativeShadow.contains("BlurMaskFilter(radius, BlurMaskFilter.Blur.NORMAL)"));
        assertTrue(nativeShadow.contains("blendMode = BlendMode.Clear"));
        assertTrue(nativeShadow.contains("shadowLayer.record(shadowSize)"));
        assertTrue(nativeShadow.contains("drawLayer(shadowLayer)"));
        assertTrue(nativeShadow.contains("canvas.drawOutline(outline, GuiPrismalClearMask)"));
        assertTrue(nativeShadow.contains("graphicsContext") || nativeShadow.contains("requireGraphicsContext()"));
        assertTrue(nativeShadow.contains("releaseGraphicsLayer"));
        assertTrue(nativeShadow.contains("if (needsOutline || recordKey != recordedShadow)"));
        assertFalse(nativeShadow.contains("drawPrismalGlass("));
        assertFalse(nativeShadow.contains("PrismalBackdrop"));
        assertFalse(nativeShadow.contains("prismalLens("));
        assertFalse(nativeShadow.contains("PixelCopy"));
        assertFalse(nativeShadow.contains("ScreenCapture"));

        assertTrue(surfaces.contains("Modifier.guiNativePrismalDepthShadow(cardShape)"));
        assertTrue(surfaces.contains("Modifier.guiNativePrismalDepthShadow(buttonShape)"));
        assertTrue(surfaces.contains(".clip(cardShape)"));
        assertTrue(surfaces.contains(".clip(buttonShape)"));
        assertTrue(stepper.contains("Modifier.guiNativePrismalDepthShadow(shape)"));

        for (String source : new String[]{slider, toggle}) {
            assertTrue(source.contains("val idleThumbShape = remember { PrismalCapsule() }"));
            assertTrue(source.contains(
                    ".guiNativePrismalDepthShadow(idleThumbShape, idleThumbShadow)"));
            assertTrue(source.contains("PrismalDepthShadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f))"));
            assertTrue(source.contains("PrismalSpecular.Ambient.copy("));
            assertTrue(source.contains("depthShadow = {"));
            assertFalse(source.contains("guiStaticSoftShadow("));
            assertFalse(source.contains("guiThumbHighlight("));
        }
    }

    @Test
    public void staticCardAndPillCornersUseActualPrismalOutlineNotBakedRasterRadius()
            throws Exception {
        String ui = Files.readString(SURFACES);
        String step = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticRepeatStepButton.kt"));
        String chrome = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiSolidChrome.kt"));

        // Upstream Continuous curvature extends beyond the nominal radius.
        // The former 80x64dp superellipse PNG with 24dp corners was too
        // small once the erroneous additional outlines were removed.
        assertTrue(ui.contains("val cardShape = PrismalRoundedRectangle(24.dp)"));
        assertTrue(ui.contains("val buttonShape = PrismalRoundedRectangle(minHeight / 2)"));
        assertTrue(ui.contains("guiExactPrismalCornerChrome(GuiSolidChromeStyle.CARD, presetChrome, cardShape)"));
        assertTrue(ui.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));
        assertTrue(step.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));

        assertTrue(chrome.contains("internal fun Modifier.guiExactPrismalCornerChrome("));
        assertTrue(chrome.contains("this.background(fill, shape).guiStaticSpecularRim(shape, palette)"));
        assertFalse(ui.contains("Modifier.guiSolidChrome(GuiSolidChromeStyle.CARD"));
        assertFalse(ui.contains("Modifier.guiSolidChrome(\n                        if (destructive)"));
        assertFalse(step.contains("Modifier.guiSolidChrome(GuiSolidChromeStyle.PILL"));

        // One external shadow, one resource fill, one cached outline.
        assertTrue(ui.contains("Modifier.guiNativePrismalDepthShadow(cardShape)"));
        assertTrue(ui.contains(".clip(cardShape)"));
        assertTrue(ui.contains(".clip(buttonShape)"));
        assertFalse(ui.contains(".guiStaticSpecularRim(cardShape, presetChrome)"));
        assertFalse(ui.contains(".guiStaticSpecularRim(buttonShape, presetChrome)"));
    }

    @Test
    public void buttonHighlightIsBakedBeforeBuildAndHasNoRuntimeAgslOrSpecularLayer()
            throws Exception {
        String renderer = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiPrismalButtonSpecular.kt"));
        String chrome = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiSolidChrome.kt"));
        String generator = Files.readString(Path.of(
                "scripts/generate_gui_button_specular.mjs"));
        String ui = Files.readString(SURFACES);
        String step = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticRepeatStepButton.kt"));

        assertTrue(renderer.contains("internal fun Modifier.guiBakedPrismalButtonSpecular("));
        assertTrue(renderer.contains("atlas: ImageBitmap?"));
        assertTrue(renderer.contains("drawWithCache"));
        assertTrue(renderer.contains("drawImage("));
        assertTrue(renderer.contains("blendMode = BlendMode.Plus"));
        assertTrue(renderer.contains("FilterQuality.Medium"));
        assertTrue(renderer.contains("srcOffset = IntOffset(SIDE_DP * SCALE, rowY)"));
        assertTrue(renderer.contains("val middleWidth = drawWidth - 2 * sideDst"));
        assertTrue(renderer.contains("private const val SIDE_DP = 34"));
        assertFalse(renderer.contains("PrismalShader("));
        assertFalse(renderer.contains("createGraphicsLayer("));
        assertFalse(renderer.contains("BlurMaskFilter("));
        assertFalse(renderer.contains("RenderEffect.create"));
        assertFalse(renderer.contains("drawPrismalGlass("));
        assertFalse(renderer.contains("Brush.sweepGradient"));
        assertFalse(renderer.contains("BitmapFactory"));

        // Atlas decode is shared by the Scaffold and is optional, not an
        // unchecked resource prerequisite (regression for prior startup crash).
        assertTrue(chrome.contains("val buttonSpecularAtlas = remember(context)"));
        assertTrue(chrome.contains("ImageBitmap.imageResource(context.resources, R.drawable.gui_button_specular_atlas)"));
        assertTrue(chrome.contains("catch (error: RuntimeException)"));
        assertTrue(chrome.contains("val buttonSpecularAtlas: ImageBitmap?"));

        // Original continuous Bezier shape and normal lighting are calculated
        // by a reproducible OFFLINE Node script, not on the Android device.
        assertTrue(generator.contains("function cornerBezier("));
        assertTrue(generator.contains("function normalIntensity("));
        assertTrue(generator.contains("function sampleContour("));
        assertTrue(generator.contains("function paintRow("));
        assertTrue(generator.contains("SCALE=3,MIN_DP=36,MAX_DP=72,HEIGHT_DP=36"));
        assertTrue(generator.contains("writeFileSync("));
        assertFalse(generator.contains("readback"));

        byte[] png = Files.readAllBytes(Path.of(
                "src/main/res/drawable-nodpi/gui_button_specular_atlas.png"));
        assertTrue(png.length > 1000);
        assertEquals(137, png[0] & 255);
        assertEquals(80, png[1] & 255);
        assertEquals(78, png[2] & 255);
        assertEquals(71, png[3] & 255);
        java.nio.ByteBuffer ihdr = java.nio.ByteBuffer.wrap(png, 16, 13)
                .order(java.nio.ByteOrder.BIG_ENDIAN);
        assertEquals(216, ihdr.getInt());
        assertEquals(3996, ihdr.getInt());
        assertEquals(8, ihdr.get() & 255); // indexed 8-bit color
        assertEquals(3, ihdr.get() & 255);
        java.io.ByteArrayOutputStream idat = new java.io.ByteArrayOutputStream();
        boolean foundAlpha = false;
        for (int offset = 8; offset + 12 <= png.length;) {
            java.nio.ByteBuffer chunk = java.nio.ByteBuffer.wrap(png, offset, 8)
                    .order(java.nio.ByteOrder.BIG_ENDIAN);
            int length = chunk.getInt();
            int type = chunk.getInt();
            assertTrue(length >= 0 && offset + length + 12 <= png.length);
            if (type == 0x49444154) idat.write(png, offset + 8, length);
            if (type == 0x74524e53) {
                foundAlpha = true;
                assertEquals(256, length);
                assertEquals(0, png[offset + 8] & 255);
                assertEquals(255, png[offset + 8 + 255] & 255);
            }
            offset += 12 + length;
        }
        assertTrue(foundAlpha);
        byte[] indices;
        try (java.util.zip.InflaterInputStream inflater =
                new java.util.zip.InflaterInputStream(
                        new java.io.ByteArrayInputStream(idat.toByteArray()))) {
            indices = inflater.readAllBytes();
        }
        assertEquals(3996 * (216 + 1), indices.length);
        int firstRowMaximum = 0, lastRowMaximum = 0;
        for (int y = 0; y < 108; y++) {
            assertEquals(0, indices[y * 217] & 255); // no PNG filter
            for (int x = 0; x < 108; x++)
                firstRowMaximum = Math.max(firstRowMaximum, indices[y * 217 + 1 + x] & 255);
            for (int x = 0; x < 216; x++)
                lastRowMaximum = Math.max(lastRowMaximum,
                        indices[(3996 - 108 + y) * 217 + 1 + x] & 255);
        }
        assertTrue("36dp button must have genuine reflected corner alpha", firstRowMaximum > 75);
        assertTrue("72dp button must have specular alpha", lastRowMaximum > 75);

        assertTrue(ui.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));
        assertTrue(step.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));
        assertFalse(ui.contains("guiExactPrismalCornerChrome(GuiSolidChromeStyle.PILL"));
        assertFalse(step.contains("guiExactPrismalCornerChrome(GuiSolidChromeStyle.PILL"));
        assertTrue(ui.contains("guiExactPrismalCornerChrome(GuiSolidChromeStyle.CARD"));
        assertTrue(ui.contains("Modifier.guiNativePrismalDepthShadow(buttonShape)"));
        assertTrue(step.contains("Modifier.guiNativePrismalDepthShadow(shape)"));
        assertTrue(step.contains("val shape = remember { PrismalRoundedRectangle(18.dp) }"));
    }

    @Test
    public void disabledResetAndStepperNeverClipNativeShadowToRectangle() throws Exception {
        String ui = Files.readString(SURFACES);
        String step = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticRepeatStepButton.kt"));
        String controls = Files.readString(CONTROLS);

        // The reset action is DISABLED at its default value; alpha on the
        // outside of the native shadow turns the soft curved halo into a
        // hard-cropped rectangular offscreen layer.
        assertTrue(controls.contains(
                "enabled = enabled && kotlin.math.abs(value - resetValue) > 0.0001f"));
        assertTrue(ui.contains("if (staticChrome && enabled) Modifier.guiNativePrismalDepthShadow(buttonShape)"));
        assertTrue(step.contains("if (staticChrome && enabled) Modifier.guiNativePrismalDepthShadow(shape)"));
        assertTrue(ui.contains(".alpha(if (enabled) 1f else 0.66f)"));
        assertTrue(step.contains(".alpha(if (enabled) 1f else 0.45f)"));

        // The accepted, independently cached Prismal shadow and offline
        // button highlighter still exist and do not stack a second rim.
        assertTrue(ui.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));
        assertTrue(step.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));
        assertFalse(ui.contains(".alpha(if (enabled) 1f else 0.66f)\n            .then(if (staticChrome) Modifier.guiNativePrismalDepthShadow"));
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
    public void pageEntryCellsUseStaticResourcesAndDoNotCaptureExtraDepthShadows() throws Exception {
        String surfaces = Files.readString(SURFACES);
        assertTrue(surfaces.contains("internal fun ModernFeatureCard("));
        assertTrue(surfaces.contains(".guiExactPrismalCornerChrome("));
        assertTrue(surfaces.contains("GuiSolidChromeStyle.CARD"));
        assertTrue(surfaces.contains("if (onClick != null) Modifier.clickable(onClick = onClick)"));
        assertFalse(surfaces.contains("GuiStaticPressPrismalSurface("));
        assertFalse(surfaces.contains("PrismalGlassSurface("));
        assertTrue(surfaces.contains("Modifier.prismalGlassLayer(screenLayer)"));
        assertTrue(Files.readString(BOTTOM_NAV).contains("PrismalGlassBottomTabs("));
    }

    @Test
    public void chevronPageCellsHaveStaticChromeAndNativeClickFeedbackWithoutGlassOptics() throws Exception {
        String surfaces = Files.readString(SURFACES);
        assertTrue(surfaces.contains("internal fun ModernFeatureCard("));
        assertTrue(surfaces.contains("onClick = onClick,"));
        assertTrue(surfaces.contains("imageVector = MiuixIcons.Basic.ArrowRight"));
        assertTrue(surfaces.contains(".guiExactPrismalCornerChrome("));
        assertTrue(surfaces.contains("Modifier.clickable(onClick = onClick)"));
        assertFalse(surfaces.contains("GuiStaticPressPrismalSurface("));
        assertFalse(surfaces.contains("PrismalGlassSurface("));
        assertFalse(surfaces.contains("PrismalPressRipple("));
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
        assertTrue(Files.readString(UTILITY_PAGES).contains("AnimatedSettingsWindowDialog("));
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
    public void staticActionsAndCellsHavePureThemeColorsAndOneDangerFill() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String chrome = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiSolidChrome.kt"));
        assertTrue(surfaces.contains("val solidCardColor = if (darkTheme)"));
        assertTrue(surfaces.contains(".background(solidCardColor, cardShape)"));
        assertTrue(surfaces.contains("val buttonShape = PrismalRoundedRectangle(minHeight / 2)"));
        assertTrue(surfaces.contains("destructive: Boolean = false"));
        assertTrue(surfaces.contains("if (destructive) presetChrome.dangerFill else presetChrome.pillFill"));
        assertTrue(surfaces.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));
        assertTrue(chrome.contains("GuiSolidChromeStyle.CARD ->"));
        assertTrue(chrome.contains("GuiSolidChromeStyle.PILL ->"));
        assertTrue(chrome.contains("GuiSolidChromeStyle.DANGER -> palette.dangerFill"));
        assertFalse(surfaces.contains("PrismalGlassSurface("));
        assertFalse(surfaces.contains("PrismalGlassStepper("));
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
    public void bottomGlyphsLiveInsideNativePrismalTabSampler() throws Exception {
        String source = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);
        assertTrue(bottom.contains("labels.indices.forEach { index ->"));
        assertTrue(bottom.contains("PrismalGlassBottomTab("));
        assertTrue(bottom.contains("ModernTabContents("));
        assertTrue(bottom.contains("active = index == candidate"));
        assertTrue(bottom.contains("LocalPrismalBottomTabHighlightedIndex"));
        assertTrue(bottom.contains("tintDropletContent = false"));
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
        String controls = Files.readString(CONTROLS);

        assertTrue(ui.contains("val rootIcons = listOf("));
        assertTrue(ui.contains("MiuixIcons.Home"));
        assertTrue(ui.contains("MiuixIcons.GridView"));
        assertTrue(ui.contains("MiuixIcons.Image"));
        assertTrue(ui.contains("MiuixIcons.Settings"));
        assertTrue(ui.contains("icons = rootIcons"));

        assertTrue(controls.contains("val displayText ="));
        assertTrue(controls.contains("ModernGlassSlider("));
        assertTrue(controls.contains("ModernGlassStepper("));
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

        assertTrue(surfaces.contains("GuiStaticRepeatStepButton("));
        assertFalse(surfaces.contains("PrismalGlassStepper("));
    }

    @Test
    public void numericStepperAndResetUseResourcesWithRepeatOnHoldInsteadOfPrismal()
            throws Exception {
        String surfaces = Files.readString(SURFACES);
        String controls = Files.readString(CONTROLS);
        String step = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/GuiStaticRepeatStepButton.kt"));
        String dialog = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/SettingsRestartScopesDialog.kt"));
        assertTrue(surfaces.contains("LocalTouchPrismalBackdrop provides touchBackdrop"));
        assertTrue(surfaces.contains("GuiStaticRepeatStepButton("));
        assertTrue(surfaces.contains("staticChrome = staticChrome"));
        assertFalse(surfaces.contains("PrismalGlassStepper("));
        assertFalse(surfaces.contains("prismalNumericAction"));
        assertFalse(controls.contains("prismalNumericAction"));
        assertTrue(controls.contains("ModernGlassStepper("));
        assertTrue(step.contains("delay(430L)"));
        assertTrue(step.contains("while (isActive)"));
        assertTrue(step.contains("tryAwaitRelease()"));
        assertTrue(step.contains("if (released && !repeated) callback.value()"));
        assertTrue(step.contains(".guiBakedPrismalButtonSpecular(presetChrome.buttonSpecularAtlas)"));
        assertTrue(dialog.contains("destructive = true,"));
        assertFalse(dialog.contains("PrismalGlassButton("));
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
    public void topBarUsesOnlyBottomStrokeAndKeepsActionShadowRoom() throws Exception {
        String surfaces = Files.readString(SURFACES);
        String bottom = Files.readString(BOTTOM_NAV);

        assertTrue(bottom.contains("val fallbackShape = RoundedCornerShape(30.dp)"));
        assertTrue(bottom.contains(".border("));
        assertTrue(bottom.contains("width = 1.dp"));
        assertTrue(bottom.contains("alpha = 0.14f"));

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
    public void glassSettingsDialogsUseMeasuredSpringMotionWithoutExtraSampling() throws Exception {
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
        assertTrue(motion.contains("show || transition.currentState || transition.isRunning"));
        assertTrue(motion.contains("Spring.DampingRatioNoBouncy"));
        assertTrue(motion.contains("scaleX = panelScale"));
        assertTrue(motion.contains("translationY = panelOffset"));
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
        assertTrue(bottom.contains("tintDropletContent = false"));
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
