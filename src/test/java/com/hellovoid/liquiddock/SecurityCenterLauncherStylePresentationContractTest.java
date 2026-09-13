package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks verified Security Center Dock/toolbox/All Apps material ownership and remaining shader-session boundaries. */
public class SecurityCenterLauncherStylePresentationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void customOwnershipClearsDockToolboxAndAllAppsButKeepsTurboAsSourceHost() throws Exception {
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        assertTrue("Dock, upper toolbox and All Apps are the claimed carriers",
                bridge.contains("dockLayout, boxMaterialView, allAppsLayout"));
        assertTrue("Dock vendor material must be cleared before module material is applied",
                bridge.contains("clearVendorTarget(dockLayout)"));
        assertTrue("the upper toolbox carrier must be cleared before module material is applied",
                bridge.contains("clearVendorTarget(boxMaterialView)"));
        assertTrue("All Apps vendor material must be cleared before module material is applied",
                bridge.contains("clearVendorTarget(allAppsLayout)"));
        assertFalse("Turbo/root is only the pass-window host and must not be cleared as a carrier",
                bridge.contains("clearVendorTarget(turboView)"));
        assertTrue("Security Center selects LiquidDock's shader material",
                policy.contains("return LiquidBlurMode.SHADER;"));
        assertTrue("Security Center enables shader blur in its Prismal parameters",
                policy.contains("static boolean useShaderBlur() {\n        return true;"));
        assertTrue("claimed Dock carrier clears its ordinary background",
                bridge.contains("target.setBackground(null)"));
    }

    @Test public void customShaderUsesSharedPassBlurSourceAndTypedVendorClaim() throws Exception {
        String early = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));

        assertFalse("custom glass must not self-blur the TextureView sink",
                policy.contains("applyContentBlur(sink"));
        assertTrue("semantic readiness must bind the custom glass session",
                early.contains("SecurityCenterGlassRuntimeState.bindAssistant("));
        assertTrue("custom presentation must use the typed vendor claim",
                bridge.contains("void claimCustom("));
        assertTrue("custom presentation must keep the shared pass-blur producer",
                session.contains("new RootPassBlurBackend("));
        assertFalse("the old framework-material configuration must not remain",
                bridge.contains("configureAdvancedMaterial("));
    }

    @Test public void stableViewApiMirrorReplacesPrivateMaterialRestoreAuthorities() throws Exception {
        Path statePath = MAIN.resolve("SecurityCenterVendorMaterialState.java");
        assertTrue(Files.exists(statePath));
        String state = Files.readString(statePath);
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String resolver = Files.readString(MAIN.resolve("SecurityCenterSemanticContractResolver.java"));

        assertTrue(state.contains("setPassWindowBlurEnabled"));
        assertTrue(state.contains("setMiBackgroundBlurRadius"));
        assertTrue(state.contains("setMiBackgroundBlendColors"));
        assertTrue(state.contains("setBackground"));
        assertTrue("claimed carriers must suppress later vendor writes while retaining intent",
                state.contains("successfulSuppressionResult(method)"));
        assertTrue("full release must replay the latest vendor snapshot",
                bridge.contains("SecurityCenterVendorMaterialState.restoreOwner(owner)"));
        assertFalse("private game material restore must not be part of semantic contract",
                resolver.contains("gameMaterialRestore"));
        assertFalse("private video material restore must not be part of semantic contract",
                resolver.contains("videoMaterialRestore"));
        assertFalse("private final-background restore must not be part of semantic contract",
                resolver.contains("finalBackground"));
    }

    @Test public void securityCenterUsesOverlayBoundSinkMappedFromVendorMaterial() throws Exception {
        Path sinkPath = MAIN.resolve("SecurityCenterGlassSinkView.java");
        assertTrue(Files.exists(sinkPath));
        String sink = Files.readString(sinkPath);
        assertTrue(sink.contains("syncFromMaterial()"));
        assertTrue("sink must resolve an outer overlay host instead of joining vendor measurement",
                sink.contains("resolveOverlayHost(material)"));
        assertTrue("material animation geometry must be mapped through the real transform chain",
                sink.contains("material.transformMatrixToGlobal(materialToGlobal)"));
        assertTrue("overlay-local placement must invert the host transform",
                sink.contains("target.transformMatrixToGlobal(targetToGlobal)"));
        assertTrue("visual alpha remains inherited without controlling readiness",
                sink.contains("effectiveMaterialAlpha(material, host.parent)"));
        assertTrue("vendor visibility remains authoritative",
                sink.contains("isStructurallyVisible(material, host.parent)"));
        assertFalse("direct material scale mirroring would reintroduce Surface/layout coupling",
                sink.contains("setScaleX(material.getScaleX())"));
        assertFalse("global-visible-rect heuristics must not replace transform mapping",
                sink.contains("getGlobalVisibleRect"));
    }

    @Test public void dockShapeUsesLiveOutlineInsteadOfBackgroundHeuristic() throws Exception {
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        assertTrue(coordinator.contains("dock.getClipToOutline()"));
        assertTrue(coordinator.contains("dock.getOutlineProvider()"));
        assertTrue(coordinator.contains("outline.getRadius()"));
        assertTrue(coordinator.contains("outline.getRect(bounds)"));
        assertFalse(coordinator.contains("MiuixGlassHook.readNativeOpticsRadius"));
        assertTrue("sink consumes normalized presentation radius instead of discovering vendor shape",
                sink.contains("captureGeometry(View root, float cornerRadiusPx)"));
    }

    @Test public void dynamicAllAppsMaterialCanRecoverAfterRuntimeAttach() throws Exception {
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        assertTrue(sink.contains("materialAttachListener"));
        assertTrue(sink.contains("scheduleParentRecovery("));
        assertTrue(sink.contains("recoverParentNow("));
    }

    @Test public void perNodeOutputPreservesPrismalOuterEdgePixelsWithoutResizingRootSpaceOutput() throws Exception {
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        String geometry = Files.readString(MAIN.resolve("SecurityCenterGlassGeometry.java"));
        assertTrue("Prismal edge shell reaches about 2.2 logical pixels outside the SDF",
                sink.contains("OPTICAL_OUTSET_PX = 3f"));
        assertTrue("Shape and presentation crop must be separable",
                geometry.contains("expandedBy("));
        assertTrue("animated All Apps must have a full-root crop without changing its shape",
                geometry.contains("withRootCrop()"));
        assertTrue("node-space output retains both sides of the optical margin",
                sink.contains("outset * 2f"));
        assertTrue("node-space crop expands while preserving original Prismal geometry",
                sink.contains("shape.expandedBy(OPTICAL_OUTSET_PX * visualScale)"));
        assertTrue("root-space output must not resize its Surface with the animated node",
                sink.contains("rootSpaceOutput ? material.getRootView() : material"));
        assertTrue("only root-space output uses the full-root crop",
                sink.contains("rootSpaceOutput\n                    ? shape.withRootCrop()"));
    }

    @Test public void sharedSessionHasMultipleSinkOutputsButOneProducer() throws Exception {
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));
        assertTrue(session.contains("Map<SecurityCenterGlassSinkView, OutputState>"));
        assertFalse(session.contains("private OutputState output;"));
        assertTrue(session.contains("new RootPassBlurBackend("));
        assertFalse(coordinator.contains("SecurityCenterGlassOutputView output"));
    }

    @Test public void earlyPrepareWaitsForPostConfigureViewReadinessWithoutTimers() throws Exception {
        String early = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        assertTrue("configure-time discovery must survive DockLayout creation that occurs later",
                early.contains("ViewTreeObserver.OnPreDrawListener"));
        assertTrue("a not-yet-attached TurboLayout must arm readiness when it attaches",
                early.contains("addOnAttachStateChangeListener"));
        assertTrue("readiness must be retried through one semantic bind gate",
                early.contains("tryBindWhenReady("));
        assertFalse("Security Center readiness must not use fixed-delay recovery",
                early.contains("postDelayed("));
    }

    @Test public void timingUsesVendorMotionAndTexturePresentationAuthorities() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));

        assertTrue("All Apps attach must be observed at the helper's public attach boundary",
                hook.contains("HookUtil.hook(allAppsMotion.attach()"));
        assertTrue("Normal hide must be observed at the helper's public dismiss boundary",
                hook.contains("HookUtil.hook(allAppsMotion.dismiss()"));
        assertTrue("Point-target hide must be observed at the helper's public dismiss boundary",
                hook.contains("HookUtil.hook(allAppsMotion.dismissToPoint()"));
        assertFalse("Private animation methods cannot remain lifecycle authority",
                hook.contains("findDeclared(candidate,"));
        assertFalse("Fixed-delay settle cannot remain animation authority",
                hook.contains("installSettleObserver("));
        assertFalse("Vendor timing booleans cannot remain animation authority",
                hook.contains("contract.transforming()"));

        assertTrue("terminal methods may be resolved for compatibility observation",
                hook.contains("resolveTerminalCleanup("));
        assertFalse("synthetic terminal cleanup must not drive material teardown",
                hook.contains("notifyVendorPanelTerminal(chain.getArgs(), contract)"));

        assertTrue("A submitted EGL frame must wait for TextureView consumption",
                sink.contains("onSurfaceTextureUpdated"));
        assertTrue("Surface update sequence must guard presentation acknowledgement",
                sink.contains("updateSequence <= armedSurfaceUpdateSequence"));
        assertTrue("Session must serialize in-flight presentation",
                session.contains("FrameRequest inFlight"));
        assertTrue("Reveal callback must be driven by presentation acknowledgement",
                session.contains("onOutputPresented("));
    }

    @Test public void windowVisibilityRestoreUsesDirectPresentationFreshnessAuthority() throws Exception {
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue("a surviving TextureView must observe real window visibility restoration",
                sink.contains("onWindowVisibilityChanged(int visibility)"));
        assertTrue("hidden-to-visible restoration must notify the live session",
                sink.contains("session.onOutputWindowVisibilityRestored(this)"));
        assertTrue("session recovery must rebuild the producer even when the old binding is invalid",
                session.contains("requestRebind(\"security-center-window-visible\")"));
        assertFalse("unlock recovery must not require the stale binding to still be valid",
                session.contains("if (shuttingDown || !sourceBackend.hasBinding()) return false;"));
        assertTrue("window restoration must invalidate presentation freshness directly",
                coordinator.contains("advancePresentationGeneration(\"window visibility restored\")"));
        assertTrue("window restoration must rebuild the source before custom ownership returns",
                coordinator.contains("recoverSourceAfterWindowVisibilityRestored()"));
        assertFalse("page-scene state must not own source freshness",
                coordinator.contains("scene.onSourceAuthorityChanged"));
    }

    @Test public void allAppsLifetimeUsesMaterialCarrierInsteadOfSyntheticSettleState() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertFalse("TurboLayout toggle names must not define target state",
                hook.contains("contract.toggleAllApps()"));
        assertFalse("private page-presence fields must not define target state",
                hook.contains("contract.allAppsPresent()"));
        assertTrue("All Apps carrier attach must advance the material epoch",
                coordinator.contains("materialEpoch.attachAllApps("));
        assertTrue("All Apps carrier removal must retire the material epoch",
                coordinator.contains("materialEpoch.detachAllApps("));
        assertFalse("a synthetic All Apps settle state must not remain lifecycle authority",
                coordinator.contains("SecurityCenterAllAppsSettleState"));
        assertFalse("presented-frame matching must not synthesize a second page lifecycle",
                coordinator.contains("trySettlePresentedAllAppsTransition("));
        assertFalse("cached page target must not shadow live carrier presence",
                coordinator.contains("targetKind"));
        assertFalse("Vendor transforming/postDelayed timing must stay non-authoritative",
                hook.contains("contract.transforming()"));
    }
}
