package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contracts for routing WallpaperContentGeneration through Workspace glass only. */
public class LauncherWallpaperBackdropGenerationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    private static String method(String source, String declaration, String nextDeclaration) {
        int start = source.indexOf(declaration);
        if (start < 0) return "";
        int end = source.indexOf(nextDeclaration, start + declaration.length());
        return end >= 0 ? source.substring(start, end) : source.substring(start);
    }

    @Test public void sceneControllerOwnsWallpaperGenerationOutsideSceneState() throws Exception {
        String controller = read("LauncherGlassSceneController.java");

        assertTrue("each Workspace root must own independent wallpaper content freshness",
                controller.contains("LauncherWallpaperContentState wallpaperContentState"));
        assertTrue("vendor wallpaper changes must advance every active root's content generation",
                controller.contains("onWallpaperChangedForAll()"));
        assertTrue("Workspace UI wallpaper notification must route a candidate boundary",
                controller.contains("onWallpaperCandidate(View"));
        assertTrue("compositor-ready notification must route an authoritative boundary",
                controller.contains("onWallpaperAuthoritativeForAll()"));
    }

    @Test public void wallpaperPulseReusesGenericSceneFreshnessWithoutInvalidatingSceneState()
            throws Exception {
        String controller = read("LauncherGlassSceneController.java");
        String pulse = method(controller, "void requestWallpaperPulse(",
                "private void applyLayerVisibility(");

        assertTrue("wallpaper pulse must reuse the already validated one-shot Session fresh path",
                pulse.contains("session.requestFreshBackdrop(state.generation())"));
        assertFalse("wallpaper content changes are not scene-generation invalidations",
                pulse.contains("onGenerationInvalidated") || pulse.contains("generation++"));
        assertFalse("old glass remains visible until the fresh backdrop atomically replaces it",
                pulse.contains("applyLayerVisibility") || pulse.contains("setSceneVisible"));
    }

    @Test public void rootControllerOwnsTheInFlightWallpaperFrameToken() throws Exception {
        String controller = read("LauncherGlassSceneController.java");
        String session = read("LauncherGlassSession.java");

        assertTrue("one root controller must bind the next fresh frame to a wallpaper generation",
                controller.contains("wallpaperPulseGeneration"));
        assertTrue("candidate and authoritative phases must stay distinguishable",
                controller.contains("wallpaperPulseAuthoritative"));
        assertTrue("the controller must know whether a wallpaper pulse is currently in flight",
                controller.contains("wallpaperPulseInFlight"));
        assertFalse("generic PassBlur Session must not duplicate wallpaper semantic state",
                session.contains("wallpaperRequestedGeneration")
                        || session.contains("wallpaperRequestedAuthoritative")
                        || session.contains("wallpaperPulseInFlight"));
    }

    @Test public void existingFreshFrameAcknowledgementConsumesWallpaperToken() throws Exception {
        String controller = read("LauncherGlassSceneController.java");
        String callback = method(controller, "static void onFreshFrameRendered(",
                "static long invalidateForProducerChange(");

        assertTrue("the real consumed OES frame boundary must also acknowledge wallpaper freshness",
                callback.contains("onWallpaperFrameConsumed"));
        assertTrue("candidate frame consumption may release a deferred authoritative pulse",
                controller.contains("onCandidateFrameConsumed"));
        assertTrue("authoritative frame consumption must commit only its generation",
                controller.contains("onFrameCommitted"));
    }

    @Test public void wallpaperRefreshStillReturnsWorkspaceProducerToIdle() throws Exception {
        String session = read("LauncherGlassSession.java");
        String drain = method(session, "private void drainFrameWork()",
                "private void refreshProducer()");

        assertTrue("fresh wallpaper frames must keep the Workspace one-shot power policy",
                drain.contains("Miuix307PassBlurBridge.pauseUpdates(binding)"));
        assertFalse("WallpaperContentGeneration must not enable a continuous Workspace producer",
                session.contains("wallpaperContinuous")
                        || session.contains("setProducerUpdatesEnabled(true, \"wallpaper"));
    }
}
