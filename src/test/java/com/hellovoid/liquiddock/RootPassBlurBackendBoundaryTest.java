package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static architecture bans for the generic root PassBlur backend. */
public class RootPassBlurBackendBoundaryTest {
    @Test
    public void genericBackendDoesNotReflectIntoViewRootOrSurfaceControl() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String backend = Files.readString(main.resolve("RootPassBlurBackend.java"));
        String bridge = Files.readString(main.resolve("RootPassBlurEndpointBridge.java"));

        assertFalse(backend.contains("java.lang.reflect"));
        assertFalse(backend.contains("getViewRootImpl"));
        assertFalse(backend.contains("mSurfaceSize"));
        assertFalse(backend.contains("mWindowAttributes"));
        assertFalse(backend.contains("android.view.SurfaceControl"));

        assertTrue(bridge.contains("getViewRootImpl"));
        assertTrue(bridge.contains("getSurfaceControl"));
    }

    @Test
    public void hotPathCachesEglBindingAndNormalizeProgramLocations() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String backend = Files.readString(main.resolve("RootPassBlurBackend.java"));

        assertTrue(backend.contains("if (currentEglSurface == surface) return;"));
        assertTrue(backend.contains("if (currentEglSurface == eglPbufferSurface) return;"));
        assertTrue(backend.contains("normalizeTextureLocation = requireUniform(normalizeProgram, \"uTexture\")"));
        assertTrue(backend.contains("normalizePositionLocation = requireAttrib(normalizeProgram, \"aPosition\")"));

        assertFalse("hot normalize path must use cached uniforms",
                backend.contains("glUniform1i(requireUniform(normalizeProgram"));
        assertTrue("normalize sampler unit must be initialized with the linked program",
                backend.contains("GLES20.glUseProgram(normalizeProgram);\n"
                        + "            GLES20.glUniform1i(normalizeTextureLocation, 0);"));
        assertFalse("hot normalize path must use cached attributes",
                backend.contains("bindQuad(normalizeProgram)"));
        assertFalse("full root normalization overwrite must not clear first",
                backend.contains("glViewport(0, 0, normalizedWidth, normalizedHeight);\n"
                        + "        GLES20.glClearColor(0f, 0f, 0f, 0f);"));
    }

    @Test
    public void workspaceMotionTemporarilyBypassesConsumerCapAndUsesBridgeLease() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String backend = Files.readString(main.resolve("RootPassBlurBackend.java"));
        String bridge = Files.readString(main.resolve("Miuix307PassBlurBridge.java"));
        String session = Files.readString(main.resolve("LauncherGlassSession.java"));

        assertTrue(backend.contains("private volatile boolean transitionFrameSyncEnabled;"));
        assertTrue(backend.contains("boolean transitionSync = transitionFrameSyncEnabled"));
        assertTrue(backend.contains("transitionSync\n                || gate == null"));
        assertTrue(backend.contains("sourceFrameGate = new PassBlurSourceFrameGate(renderFps);"));
        assertTrue(backend.contains("setWorkspaceTransitionFrameSync(current, enabled)"));

        assertTrue(bridge.contains("domain == PassBlurDomain.LAUNCHER_WORKSPACE"));
        assertTrue(bridge.contains("binding.workspaceTransitionFrameSync"));
        assertTrue(bridge.contains("WORKSPACE_FRAME_SYNC_TAG"));

        assertTrue(session.contains("WorkspaceTransitionFrameSyncState"));
        assertTrue(session.contains("geometryMotionChanged = true;"));
        assertTrue(session.contains("root.postInvalidateOnAnimation();"));
        assertTrue("configured Workspace FPS remains a static/idle policy",
                session.contains("passBlurRenderFps"));
    }

    @Test
    public void sourceRecoveryUsesFrameLifecycleInsteadOfFixedDelay() throws Exception {
        Path main = Path.of("src/main/java/com/hellovoid/liquiddock");
        String backend = Files.readString(main.resolve("RootPassBlurBackend.java"));

        assertFalse("root PassBlur recovery must never use a fixed-delay watchdog",
                backend.contains("postDelayed("));
        assertTrue("producer readiness retries must stay tied to real animation-frame opportunities",
                backend.contains("postOnAnimation("));
    }
}
