package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Performance contract: normal Launcher frames must not replay expensive glass maintenance. */
public class GlassFrameSchedulingContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void dockVendorBlurSuppressionIsEventDriven() throws Exception {
        String source = Files.readString(MAIN.resolve("MiuixGlassHook.java"));
        assertFalse("vendor blur suppression must not run from a root OnPreDraw listener",
                source.contains("installVendorGpuBlurSuppressor"));
    }

    @Test
    public void dockProducerGeometryIsNotPolledFromRootPreDraw() throws Exception {
        String source = Files.readString(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String observer = methodSlice(source,
                "private void installGeometryObserver()",
                "private void removeGeometryObserver()");
        assertFalse("Dock producer geometry must not be reflected on every root frame",
                observer.contains("addOnPreDrawListener"));
        assertTrue("geometry changes need an explicit event-driven refresh entry",
                source.contains("void requestGeometryRefresh()"));
    }

    @Test
    public void workspacePreDrawDoesNotPollProducerSurface() throws Exception {
        String source = Files.readString(MAIN.resolve("LauncherGlassSession.java"));
        String sync = methodSlice(source,
                "private void syncSceneOnUiThread()",
                "private boolean refreshProducerGeometryOnUi(View root)");
        assertFalse("node synchronization must not reflect producer geometry every frame",
                sync.contains("refreshProducerGeometryOnUi(root)"));
        assertTrue("root-size changes still need an explicit producer geometry check",
                source.contains("if (rootGeometryChanged) refreshProducerGeometryOnUi(root);"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + 1);
        if (start < 0 || end < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
