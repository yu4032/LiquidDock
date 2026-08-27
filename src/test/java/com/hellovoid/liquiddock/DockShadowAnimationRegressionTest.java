package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Contracts for the independent whole-Dock shadow during vendor Dock resize animation. */
public class DockShadowAnimationRegressionTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void transientAnimationSiblingDoesNotReparentTrackedShadow() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertFalse("temporary siblings must not force strict shadow/background adjacency",
                main.contains("shadowIndex + 1 == backgroundIndex"));
        assertTrue("an already-lower shadow remains valid even when animation siblings sit between it and the background",
                main.contains("shadowIndex < backgroundIndex"));
    }

    @Test
    public void shadowZOrderRepairWaitsForResizeAnimationToSettle() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));

        assertTrue("z-order mutations must be deferred while the vendor Dock reports animation",
                main.contains("if (!animating(dockBg)) {\n            ensureShadowBelowBackground"));
    }

    @Test
    public void deliberateShadowReinsertRestoresWeakTracking() throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        int helper = main.indexOf("private static void ensureShadowBelowBackground");
        int next = main.indexOf("private static void installDockResizeAnimationBypass", helper);
        String body = main.substring(helper, next);

        assertTrue("detach clears the tracked owner, so a deliberate reinsert must restore it",
                body.contains("shadowViewRef = new WeakReference<>(shadow);"));
    }

    @Test
    public void settledShadowGeometryUsesLaidOutBoundsWithoutDuplicateSoftwareLayerResize()
            throws Exception {
        String main = Files.readString(MAIN.resolve("MainHook.java"));
        String geometry = slice(main,
                "private static void syncShadowGeometry()",
                "private static void syncAll(View bg)");
        String sync = slice(main,
                "private static void syncAll(View bg)",
                "private static void removeDockShadow()");

        assertTrue("visible shadow geometry must come from the Dock View after layout",
                geometry.contains("int targetWidth = dockBg.getWidth() + shadowPad * 2;")
                        && geometry.contains("int targetHeight = dockBg.getHeight() + shadowPad * 2;"));
        assertTrue("software shadow layer must not be resized when its dimensions are unchanged",
                geometry.contains("if (lp.width != targetWidth || lp.height != targetHeight)"));
        assertFalse("settled shadow must not publish speculative vendor mWidth/mHeight as visible geometry",
                sync.contains("HookUtil.getIntField(bg, \"mWidth\")")
                        || sync.contains("HookUtil.getIntField(bg, \"mHeight\")"));
        assertFalse("one settled frame must not perform an immediate resize and then post the same resize again",
                sync.contains("shadowView.post(MainHook::syncShadowGeometry)"));
    }

    private static String slice(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        if (from < 0 || to < 0) throw new AssertionError("source anchors unavailable");
        return source.substring(from, to);
    }
}
