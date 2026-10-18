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
    public void workspaceEmptyGeometryCanPrimeReuseWithoutSkippingFailedCapture() throws Exception {
        String source = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        // A null geometry on a valid root transform must be represented by a
        // completed empty frame, not confused with an uninitialized frame.
        assertTrue(source.contains(
                "oldFrame == null && observed == null && staticRootTransformValid"));
        assertTrue(source.contains(
                "state.frame = new StaticGeometryFrame(null,"));
        // The first observation must still traverse/capture, including when the
        // guard's own fingerprint has become stable before a frame exists.
        assertTrue(source.contains(
                "node.canReuseStaticGeometry(root, widgetWidthFrame, staticViewFrame)"));
        assertTrue(source.contains("&& state.frame != null"));
        // Dynamic proxies and widgets never share the empty-folder cache path.
        assertTrue(source.contains(
                "node.nodeKind() != LauncherGlassNodeKind.WIDGET"));
        assertTrue(source.contains("!node.isLaunchProxyActive()"));
        assertTrue(source.contains("LD.Workspace.StaticNullCacheHits"));
    }

    @Test
    public void workspaceWidgetGeometryCacheVerifiesLiveSpanAndGridOffset() throws Exception {
        String cache = Files.readString(MAIN.resolve("LauncherGlassStaticGeometryCache.java"));
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));

        // OEM widget geometry changes without changing the material's View matrix:
        // both the current grid-dependent correction and native span must be checked.
        assertTrue(cache.contains("widgetSpanOwner != currentWidgetSpanOwner"));
        assertTrue(cache.contains("widgetSpanX != currentWidgetSpanX"));
        assertTrue(cache.contains("widgetWidthDeltaPx != currentWidgetWidthDeltaPx"));
        assertTrue(node.contains("spanX = resolveWidgetSpanX(spanOwner)"));
        assertTrue(node.contains("widthDelta = HomeGridCellGeometryHook.widgetHorizontalSpanDeltaPx("));
        assertTrue(session.contains(
                "node.canReuseStaticGeometry(root, widgetWidthFrame, staticViewFrame)"));
        // The forced native-animation path must invalidate before checking cache.
        assertTrue(session.contains("if (forceStaticCapture) {"));
        assertTrue(session.contains("node.invalidateStaticGeometry();"));
        assertTrue(session.contains("LD.Workspace.StaticWidgetCacheHits"));
    }

    @Test
    public void workspaceStaticAncestorSamplingIsFrameLocalAndKeepsNodeHistory()
            throws Exception {
        String cache = Files.readString(MAIN.resolve("LauncherGlassStaticGeometryCache.java"));
        String session = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String node = Files.readString(MAIN.resolve("LauncherGlassStaticNode.java"));

        // A shared View observation is never a cross-frame revision authority.
        assertTrue(cache.contains("IdentityHashMap<View, ViewSample> observed"));
        assertTrue(cache.contains("observed.clear();"));
        assertTrue(cache.contains("next.read(view);"));
        assertTrue(cache.contains("dirty |= ancestry.get(depth).observe(cursor, current);"));
        assertTrue(cache.contains("current.transientMotion"));
        assertTrue(cache.contains("animationMatrix"));
        assertTrue(cache.contains("cursor == root"));
        assertTrue(cache.contains("widgetSpanX != currentWidgetSpanX"));
        assertTrue(cache.contains("widgetWidthDeltaPx != currentWidgetWidthDeltaPx"));
        assertTrue(session.contains("staticViewFrame.reset();"));
        assertTrue(session.contains("if (forceStaticCapture) {"));
        assertTrue(session.contains("node.invalidateStaticGeometry();"));
        assertTrue(node.contains("spanOwner, spanX, widthDelta, staticViewFrame"));
        assertTrue(session.contains("LD.Workspace.StaticViewReads"));
        assertTrue(session.contains("LD.Workspace.StaticViewReuseHits"));
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
