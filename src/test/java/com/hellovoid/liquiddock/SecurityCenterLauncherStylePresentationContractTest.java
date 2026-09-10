package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks Launcher-style animation authority and full Security Center material ownership. */
public class SecurityCenterLauncherStylePresentationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void customOwnershipClearsTurboLayoutMaterialToo() throws Exception {
        String bridge = Files.readString(MAIN.resolve("SecurityCenterVendorMaterialBridge.java"));
        assertTrue(bridge.contains("View turboView = (View) turboLayout"));
        assertTrue(bridge.contains("resetVendorMaterial(turboView)"));
        assertTrue(bridge.contains("MiBlurBridge.clearPassWindowBlur(turboView)"));
        assertTrue(bridge.contains("turboView.setBackground(null)"));
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

    @Test public void timingUsesVendorMotionAndTexturePresentationAuthorities() throws Exception {
        String hook = Files.readString(MAIN.resolve("SecurityCenterGlassHook.java"));
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));

        assertTrue("All Apps attach must be observed before its Folme pre-draw",
                hook.contains("HookUtil.hook(motion.attach"));
        assertTrue("Show must start from the vendor helper's real Folme entry",
                hook.contains("HookUtil.hook(motion.show"));
        assertTrue("Hide must start from the vendor helper's real Folme entry",
                hook.contains("HookUtil.hook(motion.hide"));
        assertFalse("The 400/600ms transforming gate cannot remain animation authority",
                hook.contains("installSettleObserver("));
        assertFalse("The vendor timing boolean cannot remain animation authority",
                hook.contains("contract.transforming()"));

        assertTrue("A submitted EGL frame must wait for TextureView consumption",
                sink.contains("onSurfaceTextureUpdated"));
        assertTrue("Surface timestamp must guard presentation acknowledgement",
                sink.contains("texture.getTimestamp()"));
        assertTrue("Session must serialize in-flight presentation",
                session.contains("FrameRequest inFlight"));
        assertTrue("Reveal callback must be driven by presentation acknowledgement",
                session.contains("onOutputPresented("));
    }
}
