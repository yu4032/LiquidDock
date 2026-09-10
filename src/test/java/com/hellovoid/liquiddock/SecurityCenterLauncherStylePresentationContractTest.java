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

        assertTrue("TurboLayout must be treated as a material View",
                bridge.contains("View turboView = (View) turboLayout"));
        assertTrue("TurboLayout MIUI material must be reset before custom reveal",
                bridge.contains("resetVendorMaterial(turboView)"));
        assertTrue("TurboLayout pass-window blur must be cleared",
                bridge.contains("MiBlurBridge.clearPassWindowBlur(turboView)"));
        assertTrue("TurboLayout drawable/background owner must be cleared",
                bridge.contains("turboView.setBackground(null)"));
    }

    @Test public void securityCenterUsesPeerBoundSinkThatMirrorsVendorTransforms() throws Exception {
        Path sinkPath = MAIN.resolve("SecurityCenterGlassSinkView.java");
        assertTrue("Security Center must use a peer-bound sink like Launcher",
                Files.exists(sinkPath));
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
        assertFalse("Animated screen bounds must not drive TextureView layout size",
                sink.contains("getGlobalVisibleRect"));
    }

    @Test public void dynamicAllAppsMaterialCanRecoverAfterRuntimeAttach() throws Exception {
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));

        assertTrue("Runtime-created All Apps nodes need an attach lifecycle observer",
                sink.contains("materialAttachListener"));
        assertTrue("A detached/reparented material must schedule parent recovery",
                sink.contains("scheduleParentRecovery("));
        assertTrue("Recovered sinks must be inserted beside the live material parent",
                sink.contains("recoverParentNow("));
    }

    @Test public void perNodeOutputKeepsTransparentOpticalMargin() throws Exception {
        String sink = Files.readString(MAIN.resolve("SecurityCenterGlassSinkView.java"));
        String geometry = Files.readString(MAIN.resolve("SecurityCenterGlassGeometry.java"));

        assertTrue("Per-node TextureView must reserve an optical outset instead of clipping at shape bounds",
                sink.contains("opticalOutsetPx"));
        assertTrue("The crop geometry must support an expanded presentation region",
                geometry.contains("expandedBy("));
        assertTrue("Optical margin must move the sink origin as well as enlarge its surface",
                sink.contains("material.getX() - opticalOutsetPx"));
        assertTrue(sink.contains("material.getY() - opticalOutsetPx"));
    }

    @Test public void sharedSessionHasMultipleSinkOutputsButOneProducer() throws Exception {
        String session = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        String coordinator = Files.readString(MAIN.resolve("SecurityCenterGlassCoordinator.java"));

        assertTrue(session.contains("Map<SecurityCenterGlassSinkView, OutputState>"));
        assertFalse(session.contains("private OutputState output;"));
        assertTrue(session.contains("new RootPassBlurBackend("));
        assertFalse("Coordinator must not retain the union output implementation",
                coordinator.contains("SecurityCenterGlassOutputView output"));
    }
}
