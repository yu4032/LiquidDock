package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts backed by Xiaomi Launcher 4.50 lifecycle/decompilation evidence. */
public class LauncherPresentationDockAndLabelContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    private static String read(Path path) throws Exception {
        return Files.exists(path) ? Files.readString(path) : "";
    }

    @Test public void workspaceFreshBackdropWaitsForRealHomeAndUnlockBoundary()
            throws Exception {
        String hook = read(MAIN.resolve("LauncherGlassHomePresentationHook.java"));
        String controller = read(MAIN.resolve("LauncherGlassSceneController.java"));
        String module = read(MAIN.resolve("ModuleMain.java"));

        assertTrue(hook.contains("com.miui.home.recents.anim.WindowElement"));
        assertTrue(hook.contains("CLOSE_TO_HOME"));
        assertTrue(hook.contains("CLOSE_TO_HOME_CENTER"));
        assertTrue(hook.contains("onAnimationEnd"));
        assertTrue(hook.contains("com.miui.home.launcher.common.UnlockAnimationStateMachine"));
        assertTrue(hook.contains("PREPARE"));
        assertTrue(hook.contains("onSystemUiLockscreenGoneFinished"));
        assertTrue(hook.contains("setHomeTransitionPendingForAll"));
        assertTrue(hook.contains("setUnlockTransitionPendingForAll"));

        assertTrue(controller.contains("homeTransitionPending"));
        assertTrue(controller.contains("unlockTransitionPending"));
        assertTrue(controller.contains("isPresentationPending"));
        assertTrue(controller.contains("session.suspendWorkspaceProducer()"));
        assertTrue(controller.contains("if (isPresentationPending()) return;"));
        assertTrue(module.contains("LauncherGlassHomePresentationHook.install(classLoader)"));
    }

    @Test public void presentationHooksUseLauncher450ExactMethodSignatures() throws Exception {
        String hook = read(MAIN.resolve("LauncherGlassHomePresentationHook.java"));
        String homeStart = methodSlice(hook, "private static void hookHomeStart", "private static void hookHomeEnd");
        String homeEnd = methodSlice(hook, "private static void hookHomeEnd", "private static boolean containsHomeClose");
        String unlockState = methodSlice(hook, "private static void hookUnlockState", "private static void armUnlockCapture");

        // DEX signatures from Launcher 4.50:
        // WindowElement.animTo(Object)
        // WindowElement$mRectFSpringAnimListener$1.onAnimationEnd(RectFSpringAnim)
        // UnlockAnimationStateMachine.setState(UnlockAnimationStateMachine$STATE)
        assertTrue(homeStart.contains("Object.class"));
        assertTrue(homeEnd.contains("com.miui.home.recents.util.RectFSpringAnim"));
        assertTrue(unlockState.contains("com.miui.home.launcher.common.UnlockAnimationStateMachine$STATE"));
    }

    @Test public void launcherPrepareOnlyFreezesWorkspaceWallpaperCapture()
            throws Exception {
        String hook = read(MAIN.resolve("LauncherGlassHomePresentationHook.java"));
        String unlockState = methodSlice(
                hook, "private static void hookUnlockState", "private static void armUnlockCapture");

        assertTrue(unlockState.contains("PREPARE.equals(state)"));
        assertTrue(unlockState.contains("armUnlockCapture(\"Launcher/PREPARE\")"));
        assertFalse(unlockState.contains("finishUnlockBarrierNow"));
        assertFalse(unlockState.contains("prepareUnlockCaptureReturn"));

        // Xiaomi's Spring/Folme completion is presentation detail, not capture authority anymore.
        assertFalse(hook.contains("UserPresentAnimationCompatV12Spring$1"));
        assertFalse(hook.contains("UserPresentAnimationCompatV12Folme$1"));
        assertFalse(hook.contains("mAllAnimationViewNum"));
        assertFalse(hook.contains("mNumOfAnimatedView"));
    }

    @Test public void onlySystemUiFinishedCanRebuildAndReleaseUnlockCapture()
            throws Exception {
        String hook = read(MAIN.resolve("LauncherGlassHomePresentationHook.java"));

        int boundary = hook.indexOf("static void onSystemUiLockscreenGoneFinished()");
        int rollover = hook.indexOf("prepareUnlockCaptureReturn", boundary);
        int release = hook.indexOf("finishUnlockBarrierNow", rollover);
        assertTrue(boundary >= 0 && rollover > boundary && release > rollover);
        assertFalse(hook.contains("onUserPresent"));
        assertFalse(hook.contains("Choreographer"));
    }

    @Test public void hotseatDropAnimationEndForcesDockSceneGeometryRefresh() throws Exception {
        String hook = read(MAIN.resolve("DockGlassDropRefreshHook.java"));
        String renderer = read(MAIN.resolve("Miuix307ZeroCopyRenderer.java"));
        String texture = read(MAIN.resolve("Miuix307PassBlurTextureView.java"));
        String module = read(MAIN.resolve("ModuleMain.java"));

        assertTrue(hook.contains("com.miui.home.launcher.hotseats.HotSeatsListContent"));
        assertTrue(hook.contains("onDropAnimationFinish"));
        assertTrue(hook.contains("requestDockSceneRefresh"));
        assertTrue(renderer.contains("requestDockSceneRefresh"));
        assertTrue(texture.contains("requestDockSceneRefresh"));
        assertTrue(texture.contains("postOnAnimation"));
        assertTrue(texture.contains("updateBackdropMapping()"));
        assertTrue(module.contains("DockGlassDropRefreshHook.install(classLoader)"));
    }

    @Test public void applicationManagersResolveLiquidDockLabel() throws Exception {
        String manifest = Files.readString(Path.of("src/main/AndroidManifest.xml"));
        String strings = Files.readString(Path.of("src/main/res/values/strings.xml"));

        assertTrue(manifest.contains("android:label=\"@string/app_name\""));
        assertTrue(strings.contains("<string name=\"app_name\">LiquidDock</string>"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        if (start < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
