package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Locks the one-static-surface Launcher compositor architecture. */
public class LauncherGlassStaticCompositorContractTest {
    private static final Path SESSION = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassSession.java");
    private static final Path FOLDER = Path.of(
            "src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java");
    private static final Path DRAG = Path.of(
            "src/main/java/com/hellovoid/liquiddock/MiuixLauncherDragOverlayHook.java");
    private static final Path STATIC_NODE = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticNode.java");
    private static final Path STATIC_LAYER = Path.of(
            "src/main/java/com/hellovoid/liquiddock/LauncherGlassStaticLayer.java");

    @Test
    public void staticArchitectureUsesOneRootLayerAndLightweightNodes() throws Exception {
        assertTrue("missing lightweight static node", Files.exists(STATIC_NODE));
        assertTrue("missing shared static layer", Files.exists(STATIC_LAYER));

        String node = Files.readString(STATIC_NODE);
        String layer = Files.readString(STATIC_LAYER);
        assertFalse(node.contains("extends TextureView"));
        assertFalse(node.contains("new Surface("));
        assertFalse(node.contains("EGLSurface"));
        assertTrue(layer.contains("extends TextureView"));
        assertTrue(layer.contains("WeakHashMap<View, LauncherGlassStaticLayer>"));
        assertTrue(layer.contains("rootGroup.addView(layer, 0"));
    }

    @Test
    public void folderStaticPathDoesNotCreatePerFolderSinkViews() throws Exception {
        String folder = Files.readString(FOLDER);
        assertTrue(folder.contains("LauncherGlassStaticNode"));
        assertFalse(folder.contains("Map<View, WeakReference<LauncherGlassSinkView>> CLAIMED"));
        assertFalse(folder.contains("LauncherGlassSinkView.attachToMaterial"));
    }

    @Test
    public void sessionHasOneStaticOutputAndGeometryRedrawDoesNotRebuildBackdrop() throws Exception {
        String session = Files.readString(SESSION);
        assertTrue(session.contains("OutputState staticOutput"));
        assertTrue(session.contains("registerStaticNode"));
        assertTrue(session.contains("attachStaticOutput"));
        assertTrue(session.contains("renderNormalizationRoot"));
        assertTrue(session.contains("presentFull("));
        assertFalse(session.contains("boolean atlasChanged = rebuildAtlasLayout(root);"));
    }

    @Test
    public void dragKeepsOneDragSinkButSuppressesLightweightStaticNode() throws Exception {
        String drag = Files.readString(DRAG);
        assertTrue(drag.contains("LauncherGlassStaticNode"));
        assertTrue(drag.contains("LauncherGlassSinkView"));
        assertTrue(drag.contains("LauncherGlassStaticNode.find("));
        assertFalse(drag.contains("findStaticSink("));
    }
}
