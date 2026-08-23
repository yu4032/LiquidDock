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

    @Test public void wallpaperPulseDoesNotInvalidateSceneGenerationOrHideLayer() throws Exception {
        String controller = read("LauncherGlassSceneController.java");
        String pulse = method(controller, "private void requestWallpaperPulse(",
                "private void applyLayerVisibility(");

        assertTrue("wallpaper pulses must be routed to the existing root session",
                pulse.contains("session.requestWallpaperBackdrop"));
        assertFalse("wallpaper content changes are not scene-generation invalidations",
                pulse.contains("onGenerationInvalidated") || pulse.contains("generation++"));
        assertFalse("old glass remains visible until the fresh backdrop atomically replaces it",
                pulse.contains("applyLayerVisibility") || pulse.contains("setSceneVisible"));
    }

    @Test public void sessionTracksWallpaperTokenWithoutOverwritingSceneGeneration() throws Exception {
        String session = read("LauncherGlassSession.java");
        String request = method(session, "void requestWallpaperBackdrop(",
                "void requestSceneRedraw(");

        assertTrue("session needs a generation token for the wallpaper pulse in flight",
                session.contains("wallpaperRequestedGeneration"));
        assertTrue("session needs the candidate/authoritative phase for the pulse in flight",
                session.contains("wallpaperRequestedAuthoritative"));
        assertTrue("session must know whether the next OES frame belongs to a wallpaper pulse",
                session.contains("wallpaperPulseInFlight"));
        assertTrue("wallpaper freshness must reuse the validated producer recovery path",
                request.contains("recoverFreshBackdropOnUi"));
        assertFalse("WallpaperContentGeneration must never replace SceneGeneration",
                request.contains("sceneGeneration = generation")
                        || request.contains("invalidateGeneration(generation)"));
    }

    @Test public void consumedOesFrameCarriesWallpaperGenerationAndPhase() throws Exception {
        String session = read("LauncherGlassSession.java");
        String drain = method(session, "private void drainFrameWork()",
                "private void refreshProducer()");
        String controller = read("LauncherGlassSceneController.java");

        assertTrue("the exact wallpaper generation must be snapshotted when its OES frame arrives",
                drain.contains("consumedWallpaperGeneration"));
        assertTrue("candidate and authoritative frames must remain distinguishable",
                drain.contains("consumedWallpaperAuthoritative"));
        assertTrue("frame consumption must return the token to the root controller",
                drain.contains("LauncherGlassSceneController.onWallpaperFrameConsumed"));
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
