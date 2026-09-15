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
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));

        assertTrue(scope.contains("com.google.android.inputmethod.latin"));
        assertTrue(module.contains("GBOARD_PACKAGE = \"com.google.android.inputmethod.latin\""));
        assertTrue(module.contains("GboardGlassPreferences.resolve"));
        assertTrue(module.contains("initially disabled; stable lifecycle hooks remain installed"));
        assertTrue(module.contains("GboardFloatingGlassHook.install(classLoader, runtimeConfig)"));
        assertFalse(module.contains("disabled by configuration; no hooks installed"));
        assertFalse(hook.contains("|| !runtimeConfig.enabled || !runtimeConfig.glass.enabled"));
    }

    @Test public void hookUsesStableKeyboardHolderLayoutNotPopupManagerImplementation() throws Exception {
        String hook = read(MAIN.resolve("GboardFloatingGlassHook.java"));
        String resolver = read(MAIN.resolve("GboardFloatingStructureResolver.java"));

        assertTrue(resolver.contains("com.google.android.libraries.inputmethod.widgets.KeyboardHolder"));
        assertTrue(hook.contains("GboardFloatingStructureResolver.KEYBOARD_HOLDER_CLASS"));
        assertTrue(hook.contains("getDeclaredMethod(\"onLayout\""));
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

    @Test public void stockAuthorityUsesPredrawAndNoObfuscatedManagerMembers() throws Exception {
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));

        assertTrue(authority.contains("OnPreDrawListener"));
        assertTrue(authority.contains("addOnPreDrawListener"));
        assertTrue(authority.contains("removeOnPreDrawListener"));
        assertTrue(authority.contains("suppressCurrentContentBackgrounds"));
        assertTrue(authority.contains("suppressBackground(claim, structure.keyboardArea)"));
        assertTrue(authority.contains("suppressBackground(claim, structure.bottomFrame)"));
        assertTrue(authority.contains("suppressBackground(claim, topEdge)"));
        assertTrue(authority.contains("if (view.getBackground() != null) view.setBackground(null)"));
        assertFalse(authority.contains("loadClass(\"pef\")"));
        assertFalse(authority.contains("loadClass(\"pew\")"));
        assertFalse(authority.contains("getDeclaredMethod(\"j\""));
        assertFalse(authority.contains("getDeclaredMethod(\"e\""));
        assertFalse(authority.contains("HookUtil.getField"));
        assertFalse(authority.contains("0x7f0b"));
    }

    @Test public void sinkUsesStructuralContentBoundaryAndRealKeyboardBounds() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));

        assertTrue(coordinator.contains("structure.contentColumn"));
        assertTrue(coordinator.contains("indexOfChild(state.structure.contentColumn)"));
        assertTrue(coordinator.contains("new ViewGroup.LayoutParams(1, 1)"));
        assertFalse(coordinator.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
        assertTrue(coordinator.contains("state.keyboardArea.getWidth()"));
        assertTrue(coordinator.contains("state.keyboardArea.getHeight()"));
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
        assertTrue(coordinator.contains("backgroundFrame.setAlpha(0f)"));
        assertTrue(coordinator.contains("restoreStockBackground"));
        assertTrue(session.contains("swapBuffers"));
        assertTrue(session.contains("waiting for TextureView update"));
        assertFalse(session.contains("mainHandler.post(() -> {\n                    if (!shuttingDown && listener != null) listener.onPresented();"));
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

    @Test public void temporaryVisualTreeDiagnosticsAreRemoved() throws Exception {
        String authority = read(MAIN.resolve("GboardStockVisualAuthority.java"));
        assertFalse(authority.contains("GboardVisualTreeDiagnostics.dump"));
    }
}
