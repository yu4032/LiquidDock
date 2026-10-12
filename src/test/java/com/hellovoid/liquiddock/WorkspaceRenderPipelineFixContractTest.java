package com.hellovoid.liquiddock;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Integration seams that must remain fail-open across Launcher 4.50 vendor updates. */
public class WorkspaceRenderPipelineFixContractTest {
    private static String javaSource(String file) throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock", file));
    }

    @Test public void unchangedRebindDoesNotRecaptureProducerOrBootstrapEveryWidget()
            throws Exception {
        String session = javaSource("LauncherGlassSession.java");
        String scene = javaSource("LauncherGlassSceneController.java");
        String hook = javaSource("MiuixLauncherStaticGlassHook.java");

        assertTrue(session.contains("appliedGlassConfig == glassConfig) return;"));
        assertTrue(session.contains("staticNodes.containsKey(node)) return;"));
        assertTrue(session.contains("root.postInvalidateOnAnimation();"));
        assertTrue(scene.contains("if (bootstrapPosted || bootstrapReconciled) return;"));
        assertTrue(scene.contains("bootstrapReconciled = true;"));
        assertFalse(hook.contains("} else {\n            node.requestLifecycleRefresh();"));
        assertTrue(hook.contains("node.setNativeCornerRadiusPx(resolveCornerRadius(host, kind));"));
    }

    @Test public void occludedLauncherGatesOnlyWorkspaceNotRecents() throws Exception {
        String scene = javaSource("LauncherGlassSceneController.java");
        String session = javaSource("LauncherGlassSession.java");
        String hook = javaSource("MiuixLauncherStaticGlassHook.java");
        assertTrue(hook.contains("\"onStop\""));
        assertTrue(hook.contains("\"onResume\""));
        assertTrue(hook.contains("setLauncherStoppedForAll(true)"));
        assertTrue(hook.contains("setLauncherStoppedForAll(false)"));
        assertTrue(scene.contains("session.hasVisibleRecentsConsumer()"));
        assertTrue(scene.contains("state.onGenerationInvalidated();"));
        assertTrue(scene.contains("if (launcherStopped || isPresentationPending()) return;"));
        int sharedFrame = session.indexOf("child.onSharedSourceFrame(backend, frame);");
        int stop = session.indexOf("if (launcherStopped && !MainHook.isWorkstationMode()) return;", sharedFrame);
        assertTrue("Recents must be served before occluded HOME skips work",
                sharedFrame > 0 && stop > sharedFrame);
        assertTrue(session.contains("if (WorkstationProducerPolicy.shouldPauseSharedProducer("));
        assertTrue(session.contains("public boolean needsNormalizedSourceFrame()"));
        assertTrue(session.contains("|| hasVisibleRecentsConsumer()"));
        String backend = javaSource("RootPassBlurBackend.java");
        assertTrue(backend.contains("default boolean needsNormalizedSourceFrame()"));
        assertTrue(backend.contains("if (consumer != null && !consumer.needsNormalizedSourceFrame()) {"));
        assertTrue(backend.contains("drainSourceFrameWithoutRender(input);"));
    }

    @Test public void emptyOutputHasOneClearAndNeverSkipsRealGlass() throws Exception {
        String session = javaSource("LauncherGlassSession.java");
        assertTrue(session.contains("WorkspaceStaticOutputPolicy.shouldRender("));
        assertTrue(session.contains("output.blankPresented = !drawable;"));
        assertTrue(session.contains("output.blankPresented = false;"));
        assertTrue(session.contains("sourceBackend.swapBuffers(output.eglSurface);"));
        assertTrue(session.contains("prismalRenderer.beginGlassFrameOnSurface("));
        assertTrue(session.contains("if (alpha <= 0.001f) continue;"));
        String node = javaSource("LauncherGlassStaticNode.java");
        assertTrue(node.contains("if (!wasDrawable && visibilityAlpha > 0.001f) {"));
        assertTrue(node.contains("root.postInvalidateOnAnimation();"));
    }

    @Test public void repeatedSceneVisibilityDoesNotCancelNativeFade() throws Exception {
        String layer = javaSource("LauncherGlassStaticLayer.java");
        assertTrue(layer.contains("sceneTargetKnown && sceneTargetVisible == visible"));
        assertTrue(layer.contains("cancelSceneAnimation();"));
        assertTrue(layer.contains("setOpaque(false);"));
    }
}
