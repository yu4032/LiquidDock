package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for a stable Launcher DecorView whose ViewRoot/SurfaceControl is replaced. */
public class LauncherGlassSurfaceGenerationRecoveryTest {
    private static String sessionSource() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java"));
    }

    private static String method(String source, String signature, String nextSignature) {
        int start = source.indexOf(signature);
        if (start < 0) throw new AssertionError("missing method: " + signature);
        int end = source.indexOf(nextSignature, start + signature.length());
        if (end < 0) throw new AssertionError("missing next method: " + nextSignature);
        return source.substring(start, end);
    }

    @Test public void freshBackdropRevalidatesCurrentViewRootBeforeProducerPulse()
            throws Exception {
        String source = sessionSource();
        String fresh = method(source,
                "void requestFreshBackdrop(long generation)",
                "void requestSceneRedraw()");
        assertTrue(fresh.contains("recoverFreshBackdropOnUi"));
        assertTrue(fresh.contains("mainHandler.post"));
        assertFalse(fresh.contains("requestFrame(true)"));
    }

    @Test public void freshRecoveryReinstallsObserverAndWaitsForValidCurrentSurface()
            throws Exception {
        String source = sessionSource();
        assertTrue(source.contains("recoverFreshBackdropOnUi(long generation, int attempt)"));
        String recovery = method(source,
                "recoverFreshBackdropOnUi(long generation, int attempt)",
                "retryFreshBackdropRecovery(long generation, int attempt)");
        assertTrue(recovery.contains("installRootObserver()"));
        assertTrue(recovery.contains("readSurfaceGeometry(root)"));
        assertTrue(recovery.contains("geometry.rootSurface.isValid()"));
        assertTrue(recovery.contains("retryFreshBackdropRecovery(generation, attempt)"));
    }

    @Test public void sameSizeSurfaceIdentityReplacementInvalidatesGenerationBeforeRebind()
            throws Exception {
        String source = sessionSource();
        String refresh = method(source,
                "private boolean refreshProducerGeometryOnUi(View root)",
                "private boolean postRender(");
        assertTrue(refresh.contains("surfaceChanged"));
        assertTrue(refresh.contains("isSameSurface"));
        assertTrue(refresh.contains("invalidateForProducerChange(root)"));
        assertTrue(refresh.indexOf("invalidateForProducerChange(root)")
                < refresh.indexOf("rebindProducer()"));
    }

    @Test public void deadBindingIsNotPulsedWhileSurfaceRebindIsPending() throws Exception {
        String source = sessionSource();
        String recovery = method(source,
                "recoverFreshBackdropOnUi(long generation, int attempt)",
                "retryFreshBackdropRecovery(long generation, int attempt)");
        assertTrue(recovery.contains("Miuix307PassBlurBridge.Binding current = binding"));
        assertTrue(recovery.contains("current.rootSurface.isValid()"));
        assertTrue(recovery.contains("isSameSurface(current.rootSurface, geometry.rootSurface)"));
        assertTrue(recovery.contains("rebindProducer()"));
        assertTrue(recovery.contains("return;"));
    }
}
