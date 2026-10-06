package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static contract for version-independent Gboard floating-keyboard Prismal replacement. */
public class GboardFloatingGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");
    private static final Path GBOARD_SETTINGS = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/GboardSettingsPages.kt");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void gboardPackageKeepsStableHooksInstalledForLiveMasterSwitch() throws Exception {
        String scope = read(Path.of("src/main/resources/META-INF/xposed/scope.list"));
        String module = read(MAIN.resolve("ModuleMain.java"));
        String registry = read(MAIN.resolve("ThirdPartyGlassAdapterRegistry.java"));
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));
        assertTrue(scope.contains("com.google.android.inputmethod.latin"));
        assertTrue(module.contains("ThirdPartyGlassAdapterRegistry.handles(packageName)"));
        assertTrue(module.contains("ThirdPartyGlassAdapterRegistry.install(packageName, classLoader)"));
        assertTrue(registry.contains("com.google.android.inputmethod.latin"));
        assertTrue(registry.contains("GboardPassBlurContinuousAuthority.install()"));
        assertTrue(registry.contains("GboardFloatingGlassHook.install(classLoader)"));
        assertTrue(hook.contains("ConfigReader liveReader = ConfigReader.load()"));
        assertTrue(hook.contains("GboardGlassPreferences.resolve(liveReader"));
        assertFalse(module.contains("GboardGlassPreferences.resolve"));
    }

    @Test public void hookUsesStableKeyboardHolderLayoutNotPopupManagerImplementation() throws Exception {
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));
        String resolver = read(MAIN.resolve("GboardFloatingStructureResolver.java"));
        assertTrue(resolver.contains("com.google.android.libraries.inputmethod.widgets.KeyboardHolder"));
        assertTrue(hook.contains("GboardFloatingStructureResolver.KEYBOARD_HOLDER_CLASS"));
        assertTrue(hook.contains("getDeclaredMethod("));
        assertTrue(hook.contains("\"onLayout\""));
        assertTrue(hook.contains("GboardFloatingStructureResolver.resolveFromKeyboardHolder"));
        assertTrue(hook.contains("GboardFloatingGlassCoordinator.onShown"));
        assertTrue(hook.contains("GboardGlassPreferences.resolve(liveReader"));
        assertFalse(hook.contains("PopupWindow.class"));
        assertFalse(hook.contains("showAtLocation"));
        assertFalse(hook.contains("showAsDropDown"));
        assertFalse(hook.contains("\"pev\""));
        assertFalse(hook.contains("\"pef\""));
        assertFalse(hook.contains("\"qfs\""));
        assertFalse(hook.contains("\"qfy\""));
    }

    @Test public void structureResolverUsesHolderTopologyAndRuntimeFloatingGeometry() throws Exception {
        String resolver = read(MAIN.resolve("GboardFloatingStructureResolver.java"));
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        assertTrue(resolver.contains("KeyboardHolder"));
        assertTrue(resolver.contains("KeyboardViewHolder"));
        assertTrue(resolver.contains("resolveFromKeyboardHolder"));
        assertTrue(resolver.contains("getParent()"));
        assertTrue(resolver.contains("getChildCount()"));
        assertTrue(resolver.contains("indexOfChild"));
        assertTrue(resolver.contains("stockBackground"));
        assertTrue(resolver.contains("bottomFrame"));
        assertTrue(resolver.contains("isFloatingGeometry"));
        assertTrue(resolver.contains("getLocationInWindow"));
        assertTrue(resolver.contains("getRootView()"));
        assertFalse(resolver.contains("0x7f0b"));
        assertFalse(resolver.contains("0x7f07"));
        assertFalse(coordinator.contains("0x7f0b"));
        assertFalse(coordinator.contains("0x7f07"));
        assertFalse(coordinator.contains("findViewById"));
    }

    @Test public void coordinatorTracksMovementWithPredrawInsteadOfDelay() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        assertTrue(coordinator.contains("ViewTreeObserver.OnPreDrawListener"));
        assertTrue(coordinator.contains("addOnPreDrawListener"));
        assertTrue(coordinator.contains("removeOnPreDrawListener"));
        assertTrue(coordinator.contains("syncGeometry(state)"));
        assertFalse(coordinator.contains("postDelayed"));
    }

    @Test public void stockAuthorityInterceptsStableWritesWithoutPredrawReassertion() throws Exception {
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));
        assertTrue(authority.contains("\"setBackground\""));
        assertTrue(authority.contains("\"setBackgroundDrawable\""));
        assertTrue(authority.contains("\"setAlpha\""));
        assertTrue(authority.contains("\"setElevation\""));
        assertTrue(authority.contains("\"addView\""));
        assertTrue(authority.contains("addOnLayoutChangeListener"));
        assertTrue(authority.contains("recordVendorWriteLocked"));
        assertTrue(authority.contains("GboardVendorIntentState"));
        assertFalse(authority.contains("OnPreDrawListener"));
        assertFalse(authority.contains("addOnPreDrawListener"));
        assertFalse(authority.contains("removeOnPreDrawListener"));
        assertFalse(authority.contains("loadClass(\"pef\")"));
        assertFalse(authority.contains("loadClass(\"pew\")"));
        assertFalse(authority.contains("getDeclaredMethod(\"j\""));
        assertFalse(authority.contains("getDeclaredMethod(\"e\""));
        assertFalse(authority.contains("HookUtil.getField"));
        assertFalse(authority.contains("0x7f0b"));
    }

    @Test public void sinkUsesStructuralWidthAndVisualContentHeight() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String geometry = read(MAIN.resolve("GboardFloatingGlassGeometry.java"));
        assertTrue(coordinator.contains("structure.contentColumn"));
        assertTrue(coordinator.contains("indexOfChild(state.structure.contentColumn)"));
        assertTrue(coordinator.contains("new ViewGroup.LayoutParams(1, 1)"));
        assertFalse(coordinator.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(coordinator.contains(
                "state.root, state.sinkHost, state.structure, state.cornerRadiusPx"));
        assertTrue(coordinator.contains("geometry.sinkWidthPx()"));
        assertTrue(coordinator.contains("geometry.sinkHeightPx()"));
        assertTrue(geometry.contains("structure.stockBackground"));
        assertTrue(geometry.contains("structure.keyboardViewHolders"));
        assertTrue(geometry.contains("structure.bottomFrame"));
        assertTrue(geometry.contains("addVerticalAuthority"));
        assertTrue(coordinator.contains("resolveCornerRadiusPx"));
        assertTrue(coordinator.contains("Outline"));
    }

    @Test public void floatingGlassStaysZeroCopyContinuousAndFeedbackSafe() throws Exception {
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String request = read(MAIN.resolve("PassBlurBindRequest.java"));
        String domain = read(MAIN.resolve("PassBlurDomain.java"));
        assertTrue(domain.contains("GBOARD_FLOATING"));
        assertTrue(request.contains("static PassBlurBindRequest gboardFloating(View authoritativeRoot)"));
        assertTrue(session.contains("RootPassBlurBackend"));
        assertTrue(session.contains("PassBlurBindRequest.gboardFloating(root)"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("prepareBackdrop"));
        assertFalse(session.contains("ScreenCapture"));
        assertFalse(session.contains("PixelCopy"));
        assertFalse(session.contains("Bitmap.createBitmap"));
    }

    @Test public void stockHidesOnlyAfterTextureViewConsumesFirstSwap() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String sink = read(MAIN.resolve("GboardFloatingGlassView.java"));
        assertTrue(session.contains("onOutputPresented"));
        assertTrue(sink.contains("onSurfaceTextureUpdated"));
        assertTrue(sink.contains("session.onOutputPresented()"));
        assertTrue(coordinator.contains("GboardStockVisualAuthority.claim("));
        assertTrue(coordinator.contains("state.structure, state.softKeyGlassEnabled"));
        assertTrue(coordinator.contains("GboardStockVisualAuthority.release(state.structure)"));
        assertFalse(coordinator.contains("backgroundFrame.setAlpha(0f)"));
        assertFalse(coordinator.contains("restoreStockBackground"));
        assertTrue(session.contains("swapBuffers"));
        assertTrue(session.contains("swapSucceeded = true"));
    }

    @Test public void floatingSoftKeysShareTheExistingPrismalFrame() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String scene = read(MAIN.resolve("GboardSoftKeyGlassScene.java"));
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));

        assertTrue(scene.contains(
                "com.google.android.libraries.inputmethod.widgets.SoftKeyView"));
        assertTrue(scene.contains("getSuperclass()"));
        assertTrue(scene.contains("GboardFloatingGlassGeometry.captureTargetRect"));
        assertTrue(scene.contains("outline.getRect(rect)"));
        assertTrue(scene.contains("fallbackBounds(key)"));
        assertTrue(scene.contains("ShapeTemplate"));
        assertTrue(scene.contains("SOFT_KEYBOARD_VIEW_CLASS"));
        assertTrue(scene.contains("collectKeyboardSoftKeys"));
        assertTrue(scene.contains("drawable.getPadding(padding)"));
        assertTrue(scene.contains("PrismalInteractionState"));
        assertTrue(scene.contains("isPressed()"));
        assertTrue(scene.contains("isCustomRadiusExempt"));
        assertTrue(scene.contains("switch_to_symbol"));
        assertTrue(scene.contains("ime_action"));
        assertTrue(coordinator.contains("GboardSoftKeyGlassScene.capture"));
        assertTrue(coordinator.contains("GboardStockVisualAuthority.refreshPreparedSoftKeys"));
        assertTrue(session.contains("for (GboardSoftKeyGlassScene.Node node"));
        assertTrue(session.contains("prismalRenderer.drawGlass("));
        assertTrue(authority.contains("claimPreparedKeyboardSoftKeys"));
        assertTrue(authority.contains("claimPreparedSoftKeysInsideKeyboard"));
        assertTrue(authority.contains("GboardSoftKeyGlassScene.isPrepared"));
        assertTrue(authority.contains("captureSuppressedDrawable"));
        assertTrue(authority.contains("ensureSuppressedDrawableHidden"));
        assertFalse(authority.contains("new ColorDrawable(Color.TRANSPARENT)"));
        assertFalse(scene.contains("findViewById"));
        assertFalse(scene.contains("0x7f0b"));
        assertFalse(scene.contains("TextureView"));
        assertFalse(scene.contains("RootPassBlurBackend"));
    }

    @Test public void gboardGuiLivesUnderLiquidThirdPartyAppsAndHasIndependentAppearance() throws Exception {
        String settings = read(SETTINGS);
        String gboardSettings = read(GBOARD_SETTINGS);
        String preferences = read(MAIN.resolve("GboardGlassPreferences.java"));
        assertTrue(settings.contains("ThirdPartyApps"));
        assertTrue(settings.contains("GboardSettingsPage"));
        assertTrue(settings.contains("openThirdPartyApps"));
        assertTrue(settings.contains("第三方应用适配"));
        assertTrue(gboardSettings.contains("Gboard"));
        assertTrue(gboardSettings.contains("启用悬浮键盘液态玻璃"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.SOFT_KEY_GLASS_ENABLED_KEY"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.SOFT_KEY_CORNER_RADIUS_DP_KEY"));
        assertTrue(gboardSettings.contains("按键背景液态玻璃"));
        assertTrue(gboardSettings.contains("按键圆角"));
        assertTrue(gboardSettings.contains("?123 与回车键保留 Gboard 原生圆角"));
        assertTrue(preferences.contains("SOFT_KEY_GLASS_ENABLED_DEFAULT = true"));
        assertTrue(preferences.contains("SOFT_KEY_CORNER_RADIUS_DP_DEFAULT = 8"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.BLUR_KEY"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.TINT_RED_KEY"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.TINT_GREEN_KEY"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.TINT_BLUE_KEY"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.TINT_ALPHA_KEY"));
        assertTrue(gboardSettings.contains("恢复继承全局外观"));
        assertTrue(gboardSettings.contains("appearanceGeneration.let"));
        assertTrue(gboardSettings.contains("hasAppearanceOverride"));
        assertFalse(gboardSettings.contains("appearanceGeneration < 0"));
        assertTrue(preferences.contains("reader.has(BLUR_KEY)"));
        assertTrue(preferences.contains("reader.has(TINT_RED_KEY)"));
    }
}
