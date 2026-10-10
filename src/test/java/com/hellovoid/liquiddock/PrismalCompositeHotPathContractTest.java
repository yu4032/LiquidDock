package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class PrismalCompositeHotPathContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final List<String> SESSIONS = List.of(
            "LauncherGlassSession.java",
            "GboardFloatingGlassSession.java",
            "SecurityCenterGlassSession.java",
            "RecentsCapsuleGlassSession.java",
            "MiuiSearchboxGlassSession.java",
            "ShortcutPopupGlassSession.java",
            "SystemUiHandleMenuPrismalSession.java");

    @Test
    public void compositeDrawLoopsReuseLinkedLocations() throws Exception {
        for (String file : SESSIONS) {
            String source = Files.readString(MAIN.resolve(file));
            assertTrue(file, source.contains("GLES20.glGetAttribLocation(compositeProgram, \"aPosition\")"));
            assertTrue(file, source.contains("compositeTextureLocation = requireUniform(compositeProgram, \"uTexture\")"));
            assertTrue(file, source.contains("compositeCropRectLocation = requireUniform(compositeProgram, \"uCropRect\")"));
            assertFalse(file, source.contains("bindQuad(compositeProgram)"));
            assertFalse(file, source.contains("GLES20.glUniform1i(requireUniform(compositeProgram, \"uTexture\"), 0)"));
            assertFalse(file, source.contains("GLES20.glUniform4f(requireUniform(compositeProgram, \"uCropRect\")"));
        }
    }

    @Test
    public void workspacePreDrawProfilingUsesPerfettoWithoutDebugLogOrProducerChanges()
            throws Exception {
        // Existing audited static contract: the runtime still needs an on-device trace.
        String source = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        assertTrue(source.contains("boolean tracing = Trace.isEnabled()"));
        assertTrue(source.contains("LD.Workspace.SceneSync"));
        assertTrue(source.contains("LD.Workspace.DragNodes"));
        assertTrue(source.contains("LD.Workspace.StaticNodes"));
        assertTrue(source.contains("LD.Workspace.SourceReconcile"));
        assertTrue(source.contains("LD.Workspace.DragCandidates"));
        assertTrue(source.contains("LD.Workspace.DragGeometryReads"));
        assertTrue(source.contains("LD.Workspace.StaticCandidates"));
        assertTrue(source.contains("LD.Workspace.StaticGeometryReads"));
        assertTrue(source.contains("if (tracing) Trace.beginSection"));
        assertTrue(source.contains("if (tracing) Trace.endSection"));
        assertTrue(source.contains("syncSceneOnUiThreadInternal(tracing)"));
        assertTrue(source.contains("sourceBackend.reconcileRoot()"));
        assertTrue(source.contains("LauncherGlassHomePresentationHook.isUnlockCaptureBlocked()"));
        assertTrue(source.contains("transitionFrameSync.onPreDraw(geometryMotionChanged)"));
        assertTrue(source.contains("outputRenderState.request(staticDirty, dragDirty)"));
        assertFalse(source.contains("Thread.sleep("));
    }

    @Test
    public void securityCenterCompositeDoesNotPollGlErrorEveryPresent() throws Exception {
        String source = Files.readString(MAIN.resolve("SecurityCenterGlassSession.java"));
        assertFalse(source.contains("GLES20.glGetError()"));
    }

    @Test
    public void systemUiNormalizeDrawLoopReusesLinkedLocations() throws Exception {
        String source = Files.readString(MAIN.resolve("SystemUiHandleMenuPrismalSession.java"));
        assertTrue(source.contains("normalizeTexMatrixLocation = requireUniform(normalizeProgram, \"uTexMatrix\")"));
        assertTrue(source.contains("normalizeBackdropRectLocation = requireUniform(normalizeProgram, \"uBackdropRect\")"));
        assertFalse(source.contains("bindQuad(normalizeProgram)"));
        assertFalse(source.contains("GLES20.glUniform1i(requireUniform(normalizeProgram, \"uTexture\"), 0)"));
    }
}
