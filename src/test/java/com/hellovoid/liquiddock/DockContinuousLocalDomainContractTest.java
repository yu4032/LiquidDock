package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Dock stays an independent continuous TextureView/EGL domain and owns no Workspace scene nodes. */
public class DockContinuousLocalDomainContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void dockProducerRendersEveryProducerFrameIndependentlyOfWorkspace() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("input.setOnFrameAvailableListener"));
        assertTrue(view.contains("drawLatestFrame(true);"));
        assertFalse(view.contains("LauncherGlassSession"));
        assertFalse(view.contains("LauncherGlassSceneController"));
    }

    @Test public void dockOutputIsOneLegacyTextureViewWithoutPerItemGlassNodes() throws Exception {
        String view = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));

        assertTrue(view.contains("extends TextureView"));
        assertFalse(Files.exists(MAIN.resolve("DockGlassItemNode.java")));
        assertFalse(Files.exists(MAIN.resolve("DockGlassCompositor.java")));
        assertFalse(view.contains("DockGlassSceneSnapshot"));
    }

    @Test public void dockBindNeverAutoPausesItsContinuousProducer() throws Exception {
        String bridge = Files.readString(MAIN.resolve("Miuix307PassBlurBridge.java"));
        int bindStart = bridge.indexOf("static Binding bind(View materialHost, Surface producerSurface");
        int overloadStart = bridge.indexOf("/** Compatibility overload", bindStart);
        assertTrue(bindStart >= 0 && overloadStart > bindStart);
        String bind = bridge.substring(bindStart, overloadStart);

        assertTrue(bind.contains("setUpdateTextureFlag.invoke("));
        assertTrue(bind.contains("Boolean.TRUE"));
        assertFalse(bind.contains("materialHost.getRootView() == materialHost"));
        assertFalse(bind.contains("schedulePauseUpdates("));
        assertFalse(bind.contains("pauseUpdates("));
    }
}
