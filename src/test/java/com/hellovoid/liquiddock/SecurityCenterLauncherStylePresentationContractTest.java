package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the active framework Dock path plus dormant shader-session recovery boundaries. */
public class SecurityCenterLauncherStylePresentationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void frameworkOwnershipUsesDockCarrierAndTurboPassWindowHost() throws Exception {
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        assertTrue("framework material must have a typed Dock claim",
                bridge.contains("claimFrameworkDockInternal"));
        assertTrue("Dock vendor material must be cleared before framework material is applied",
                bridge.contains("clearVendorTarget(dockLayout)"));
        assertFalse("Turbo/root is only the pass-window host and must not be cleared as a carrier",
                bridge.contains("clearVendorTarget(turboView)"));
        assertTrue("Security Center selects framework advanced material",
                policy.contains("return LiquidBlurMode.ADVANCED_MATERIAL;"));
        assertTrue("Security Center disables shader blur on its active path",
                policy.contains("static boolean useShaderBlur() {\n        return false;"));
        assertTrue("Turbo hosts pass-window blur and Dock is the framework carrier",
                policy.contains("MiBlurBridge.setPassWindowBlurEnabled(turbo, true)")
                        && policy.contains("MiBlurBridge.applyPassWindowBlur("));
        assertTrue("claimed Dock carrier clears its ordinary background",
                bridge.contains("target.setBackground(null)"));
    }

    @Test public void frameworkMaterialUsesTypedClaimWithoutStartingShaderSession() throws Exception {
        String early = Files.readString(MAIN.resolve("SecurityCenterEarlyPrepareHook.java"));
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        String policy = Files.readString(MAIN.resolve("SecurityCenterMaterialModePolicy.java"));
        String transition = Files.readString(
                MAIN.resolve("SecurityCenterGlassRuntimeTransitionPolicy.java"));

        assertFalse("framework Dock must not self-blur a TextureView sink",
                policy.contains("applyContentBlur(sink"));
        assertTrue("semantic readiness must claim the framework Dock directly",
                early.contains("SecurityCenterVendorMaterialBridge.claimFrameworkDock(turbo, dock)"));
        assertFalse("Video/Global readiness must not start the OES/Prismal session",
                early.contains("SecurityCenterGlassRuntimeState.bindAssistant(\n                    turbo, dock"));
        assertTrue("framework presentation must use the typed bridge claim",
                bridge.contains("static boolean claimFrameworkDock("));
        assertTrue("typed assistant policy must select the framework backend",
                transition.contains("AssistantBackend.FRAMEWORK_PASS_WINDOW"));
        assertTrue("the active material must be configured through framework pass-window APIs",
                bridge.contains("configureAdvancedMaterial(")
                        && policy.contains("MiBlurBridge.applyPassWindowBlur("));
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

    @Test public void securityCenterUsesPeerBoundSinkThatMirrorsVendorTransforms() throws Exception {
        Path sinkPath = MAIN.resolve("SecurityCenterGlassSinkView.java");
        assertTrue(Files.exists(sinkPath));
        String sink = Files.readString(sinkPath);
        assertTrue(sink.contains("syncFromMaterial()"));
        assertTrue(sink.contains("material.getX()"));
        assertTrue(sink.contains("material.getY()"));
        assertTrue(sink.contains("material.getPivotX()"));
        assertTrue(sink.contains("material.getPivotY()"));
        assertTrue(sink.contains("material.getScaleX()"));
        assertTrue(sink.contains("material.getScaleY()"));
        assertTrue(sink.contains("material.getRotation()"));
        assertTrue(sink.contains("material.getAlpha()"));
        assertTrue(sink.contains("material.getVisibility()"));
        assertFalse(sink.contains("getGlobalVisibleRect"));
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

    @Test public void perNodeOutputPreservesPrismalOuterEdgePixels() throws Exception {
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        String geometry = Files.readString(MAIN.resolve("SecurityCenterGlassGeometry.java"));
        assertTrue("Prismal edge shell reaches about 2.2 logical pixels outside the SDF",
                sink.contains("OPTICAL_OUTSET_PX = 3f"));
        assertTrue("Shape and presentation crop must be separable",
                geometry.contains("expandedBy("));
        assertTrue("Output surface must include both sides of the optical margin",
                sink.contains("+ Math.round(OPTICAL_OUTSET_PX * 2f)"));
        assertTrue("Crop must expand while preserving the original Prismal shape geometry",
                sink.contains(".expandedBy(OPTICAL_OUTSET_PX * visualScale)"));
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
        assertTrue("Normal hide must start from the vendor helper's public dismiss boundary",
                hook.contains("HookUtil.hook(allAppsMotion.dismiss()"));
        assertTrue("Point-target hide must start from the vendor helper's public dismiss boundary",
                hook.contains("HookUtil.hook(allAppsMotion.dismissToPoint()"));
        assertFalse("Private animation methods cannot remain lifecycle authority",
                hook.contains("findDeclared(candidate,"));
        assertFalse("Fixed-delay settle cannot remain animation authority",
                hook.contains("installSettleObserver("));
        assertFalse("Vendor timing booleans cannot remain animation authority",
                hook.contains("contract.transforming()"));

        assertTrue("Animated terminal release must use the semantic cleanup contract",
                hook.contains("resolveTerminalCleanup("));
        assertTrue("Terminal cleanup must release only after vendor cleanup proceeds",
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

    @Test public void windowVisibilityRestoreForcesProducerRecoveryWithoutRequiringOldBinding() throws Exception {
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
        assertTrue("coordinator must return to vendor fallback before awaiting a fresh unlock frame",
                coordinator.contains("window visibility restored; refreshing source generation")
                        && coordinator.contains("scene.onSourceAuthorityChanged(targetKind)"));
    }

    @Test public void allAppsSettleUsesMotionTargetAndMatchingPresentedComposition() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertFalse("TurboLayout toggle names must not define target state",
                hook.contains("contract.toggleAllApps()"));
        assertFalse("private page-presence fields must not define target state",
                hook.contains("contract.allAppsPresent()"));
        assertTrue("motion semantic must carry target state directly",
                hook.contains("boolean targetPresent"));
        assertTrue("motion target and generation must reach the existing settle gate",
                hook.contains("live.onAllAppsToggleTargetResolved(turbo, targetPresent, generation)"));
        assertTrue("Settlement must be retried from real TextureView presentation acknowledgement",
                coordinator.contains("trySettlePresentedAllAppsTransition("));
        assertTrue("A target can settle only when the presented frame has matching All Apps nodes",
                coordinator.contains("frame.appsGeometry() != null"));
        assertFalse("Vendor transforming/postDelayed timing must stay non-authoritative",
                hook.contains("contract.transforming()"));
    }
}
