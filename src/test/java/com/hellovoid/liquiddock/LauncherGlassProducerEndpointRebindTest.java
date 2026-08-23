package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression coverage for HyperOS SetPassBlurSurface rejecting a reused producer binder. */
public class LauncherGlassProducerEndpointRebindTest {
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

    @Test public void surfaceGenerationRebindRollsProducerEndpointInsteadOfReusingBinder()
            throws Exception {
        String source = sessionSource();
        String rebind = method(source,
                "private void rebindProducer()",
                "private void renderScene(");
        assertTrue(rebind.contains("rollInputProducerForRebind"));
        assertFalse(rebind.contains("mainHandler.post(() -> bindProducerWhenReady(0))"));
    }

    @Test public void rolloverClearsOldEndpointBeforeCreatingNewBufferQueueProducer()
            throws Exception {
        String source = sessionSource();
        String rollover = method(source,
                "private void rollInputProducerForRebind()",
                "private void releaseInputProducerEndpointOnRenderThread()");
        assertTrue(rollover.contains("postRender"));
        assertTrue(rollover.contains("releaseInputProducerEndpointOnRenderThread()"));
        assertTrue(rollover.contains("createInputProducer()"));
        assertTrue(rollover.indexOf("releaseInputProducerEndpointOnRenderThread()")
                < rollover.indexOf("createInputProducer()"));
    }

    @Test public void endpointReleaseInvalidatesSurfaceTextureSurfaceAndOesTexture()
            throws Exception {
        String source = sessionSource();
        String release = method(source,
                "private void releaseInputProducerEndpointOnRenderThread()",
                "private void renderScene(");
        assertTrue(release.contains("inputProducerSurface = null"));
        assertTrue(release.contains("inputSurfaceTexture = null"));
        assertTrue(release.contains("input.setOnFrameAvailableListener(null)"));
        assertTrue(release.contains("producer.release()"));
        assertTrue(release.contains("input.release()"));
        assertTrue(release.contains("GLES20.glDeleteTextures"));
        assertTrue(release.contains("oesTexture = 0"));
        assertTrue(release.contains("frameAvailable.set(false)"));
        assertTrue(release.contains("hasConsumedFrame = false"));
        assertTrue(release.contains("backdropPrepared = false"));
    }

    @Test public void rolloverKeepsSingleProducerInvariant() throws Exception {
        String source = sessionSource();
        String rollover = method(source,
                "private void rollInputProducerForRebind()",
                "private void releaseInputProducerEndpointOnRenderThread()");
        assertFalse(rollover.contains("new SurfaceTexture"));
        assertFalse(rollover.contains("new Surface("));
        assertTrue(rollover.contains("createInputProducer()"));
    }

    @Test public void shutdownReusesEndpointReleaseHelperInsteadOfDuplicatingCleanup()
            throws Exception {
        String source = sessionSource();
        String releaseGl = method(source,
                "private void releaseGl()",
                "private ProducerGeometry readSurfaceGeometry(");
        assertTrue(releaseGl.contains("releaseInputProducerEndpointOnRenderThread()"));
        assertFalse(releaseGl.contains("Surface producer = inputProducerSurface"));
        assertFalse(releaseGl.contains("SurfaceTexture input = inputSurfaceTexture"));
        assertFalse(releaseGl.contains("GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0)"));
    }
}
