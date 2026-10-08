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
        assertTrue(hook.contains("GboardGlassPreferences.realtimeBackgroundSampling(liveReader)"));
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

    @Test public void sinkIsFixedFullscreenWhileSceneUsesKeyboardGeometry() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String geometry = read(MAIN.resolve("GboardFloatingGlassGeometry.java"));
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        assertTrue(coordinator.contains("ViewGroup host = (ViewGroup) state.root"));
        assertTrue(coordinator.contains("directChildUnder(state.keyboardArea, host)"));
        assertTrue(coordinator.contains("ViewGroup.LayoutParams.MATCH_PARENT"));
        assertFalse(coordinator.contains("syncSinkBounds"));
        assertTrue(coordinator.contains(
                "state.root, state.sinkHost, state.structure, state.cornerRadiusPx"));
        assertTrue(geometry.contains("structure.stockBackground"));
        assertTrue(geometry.contains("structure.keyboardViewHolders"));
        assertTrue(geometry.contains("structure.bottomFrame"));
        assertTrue(geometry.contains("addVerticalAuthority"));
        assertTrue(session.contains("OutputMode.FULLSCREEN_REGION"));
        assertTrue(session.contains("presentRegion("));
        assertTrue(session.contains("GLES20.glScissor("));
        assertTrue(coordinator.contains("resolveCornerRadiusPx"));
        assertTrue(coordinator.contains("Outline"));
    }

    @Test public void floatingGlassStaysZeroCopyAndSupportsFrozenSampling() throws Exception {
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String request = read(MAIN.resolve("PassBlurBindRequest.java"));
        String domain = read(MAIN.resolve("PassBlurDomain.java"));
        String authority = read(MAIN.resolve("GboardPassBlurContinuousAuthority.java"));
        String bridge = read(MAIN.resolve("Miuix307PassBlurBridge.java"));
        assertTrue(domain.contains("GBOARD_FLOATING"));
        assertTrue(request.contains("static PassBlurBindRequest gboardFloating(View authoritativeRoot)"));
        assertTrue(session.contains("RootPassBlurBackend"));
        assertTrue(session.contains("PassBlurBindRequest.gboardFloating(root)"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("prepareBackdrop"));
        assertTrue(session.contains("realtimeBackgroundSampling"));
        assertTrue(session.contains("requestFrozenMotionCapture"));
        assertTrue(session.contains("freezeBackdropAfterSettle"));
        assertTrue(session.contains("sourceBackend.setUpdatesEnabled(false"));
        assertTrue(session.contains("renderQueueLock"));
        assertTrue(session.contains("scheduleRender(true)"));
        assertTrue(session.contains("scheduleRender(false)"));
        assertTrue(session.contains("drainScheduledRender(long ticket)"));
        assertTrue(authority.contains("updatesEnabled"));
        assertTrue(authority.contains("Boolean.valueOf(claim.updatesEnabled)"));
        assertTrue(bridge.contains("GboardPassBlurContinuousAuthority.setUpdatesEnabled"));
        assertTrue(bridge.contains("Integer.valueOf(0)"));
        assertTrue(bridge.contains("binding.domain == PassBlurDomain.GBOARD_FLOATING"));
        assertTrue(bridge.contains("domain == PassBlurDomain.GBOARD_FLOATING"));
        assertTrue(bridge.contains("setForceRefresh = transactionClass.getMethod"));
        String backend = read(MAIN.resolve("RootPassBlurBackend.java"));
        assertTrue(backend.contains("Miuix307PassBlurBridge.renewForceRefresh(binding);"));
        assertFalse(session.contains("ScreenCapture"));
        assertFalse(session.contains("PixelCopy"));
        assertFalse(session.contains("Bitmap.createBitmap"));
    }

    @Test public void movementUsesLatestGeometryWithoutQueueingHistoricalFrames()
            throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));

        assertTrue(coordinator.contains("GboardFrozenBackdropMotionState"));
        assertTrue(coordinator.contains("FROZEN_STARTUP_MIN_LIVE_MS = 360L"));
        assertTrue(coordinator.contains("FREEZE_AFTER_SETTLE"));
        assertTrue(coordinator.contains("freezeBackdropAfterSettle"));
        assertTrue(coordinator.contains("REFRESH_AT_MOTION_START"));
        assertTrue(coordinator.contains("requestFrozenMotionCapture"));
        assertTrue(session.contains("renderDirty"));
        assertTrue(session.contains("renderQueued"));
        assertTrue(session.contains("renderTicket"));
        assertTrue(session.contains("postUrgentToRenderThread"));
        assertTrue(session.contains("scheduleRender(true)"));
        assertTrue(session.contains("scheduleRender(false)"));
        assertFalse(session.contains("postToRenderThread(this::renderCurrent)"));
    }

    @Test public void keyboardMotionUsesDirtyRegionInsteadOfFullRootSceneClear()
            throws Exception {
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String geometry = read(MAIN.resolve("GboardFloatingGlassGeometry.java"));

        assertTrue(session.contains("beginKeyboardDirtyFrame"));
        assertTrue(session.contains("beginGlassFrameRegion"));
        assertTrue(session.contains("lastRenderedGeometry"));
        assertTrue(session.contains("Math.min(previous.left, current.left)"));
        assertTrue(session.contains("Math.max("));
        assertTrue(geometry.contains("boolean sharedRootHost = sinkHost == root"));
    }

    @Test public void pureKeyboardMotionDoesNotReconcileRootSurface() throws Exception {
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));

        assertTrue(session.contains("old.rootWidth != next.rootWidth"));
        assertTrue(session.contains("old.rootHeight != next.rootHeight"));
        assertTrue(session.contains("sourceBackend.reconcileRoot();"));
        assertTrue(session.contains("scheduleRender(true)"));
    }

    @Test public void geometryRenderCanPreemptBackdropWorkUnderLoad() throws Exception {
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String backend = read(MAIN.resolve("RootPassBlurBackend.java"));

        assertTrue(session.contains("scheduleRender(true)"));
        assertTrue(session.contains("scheduleRender(false)"));
        assertTrue(session.contains("postUrgentToRenderThread"));
        assertTrue(session.contains("renderTicket"));
        assertTrue(backend.contains("postUrgentToRenderThread"));
        assertTrue(backend.contains("postAtFrontOfQueue"));
    }

    @Test public void hierarchyMutationIsDeferredOutsideKeyboardLayout() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        assertTrue(coordinator.contains("scheduleAttach(state)"));
        assertTrue(coordinator.contains("state.popup.post(() ->"));
        assertTrue(coordinator.contains("sinkHost.post(() ->"));
        assertTrue(coordinator.contains(
                "state.popup.post(() -> GboardStockVisualAuthority.release(state.structure))"));
    }

    @Test public void stockHidesOnlyAfterTextureViewConsumesFirstSwap() throws Exception {
        String coordinator = read(MAIN.resolve("GboardFloatingGlassCoordinator.java"));
        String session = read(MAIN.resolve("GboardFloatingGlassSession.java"));
        String sink = read(MAIN.resolve("GboardFloatingGlassView.java"));
        assertTrue(session.contains("onOutputPresented"));
        assertTrue(sink.contains("onSurfaceTextureUpdated"));
        assertTrue(sink.contains("session.onOutputPresented()"));
        assertTrue(coordinator.contains("GboardStockVisualAuthority.claim(state.structure)"));
        assertTrue(coordinator.contains("GboardStockVisualAuthority.release(state.structure)"));
        assertFalse(coordinator.contains("backgroundFrame.setAlpha(0f)"));
        assertFalse(coordinator.contains("restoreStockBackground"));
        assertTrue(session.contains("swapBuffers"));
        assertTrue(session.contains("swapSucceeded = true"));
    }

    @Test public void gboardGuiLivesUnderGlassThirdPartyAppsAndHasIndependentAppearance() throws Exception {
        String settings = read(SETTINGS);
        String gboardSettings = read(GBOARD_SETTINGS);
        String preferences = read(MAIN.resolve("GboardGlassPreferences.java"));
        assertTrue(settings.contains("Page.ThirdPartyApps"));
        assertTrue(settings.contains("GboardSettingsPage"));
        assertTrue(settings.contains("HubEntry(Page.ThirdPartyApps"));
        assertTrue(settings.contains("openGboard = { navigateTo(Page.Gboard) }"));
        assertTrue(gboardSettings.contains("Gboard"));
        assertTrue(gboardSettings.contains("启用悬浮键盘液态玻璃"));
        assertTrue(gboardSettings.contains("GboardGlassPreferences.REALTIME_BACKGROUND_SAMPLING_KEY"));
        assertTrue(gboardSettings.contains("实时背景采样"));
        assertTrue(gboardSettings.contains("弹出动画期间保持实时"));
        assertTrue(gboardSettings.contains("动画稳定后冻结"));
        assertTrue(gboardSettings.contains("每次开始移动时刷新一帧"));
        assertTrue(preferences.contains("REALTIME_BACKGROUND_SAMPLING_DEFAULT = true"));
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
