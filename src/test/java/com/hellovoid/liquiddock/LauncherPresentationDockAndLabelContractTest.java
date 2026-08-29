package com.hellovoid.liquiddock;

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

    @Test public void workspaceFreshBackdropWaitsForRealHomeAndUnlockPresentationEnd()
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
        String unlockState = methodSlice(hook, "private static void hookUnlockState", "private static void hookUnlockSpringFinish");

        // DEX signatures from Launcher 4.50:
        // WindowElement.animTo(Object)
        // WindowElement$mRectFSpringAnimListener$1.onAnimationEnd(RectFSpringAnim)
        // UnlockAnimationStateMachine.setState(UnlockAnimationStateMachine$STATE)
        assertTrue(homeStart.contains("Object.class"));
        assertTrue(homeEnd.contains("com.miui.home.recents.util.RectFSpringAnim"));
        assertTrue(unlockState.contains("com.miui.home.launcher.common.UnlockAnimationStateMachine$STATE"));
    }

    @Test public void padUnlockUsesSpringTerminalCountAndNoAnimationIdleEscape()
            throws Exception {
        String hook = read(MAIN.resolve("LauncherGlassHomePresentationHook.java"));

        // Launcher 4.50 createAnimation() selects V12Spring on non-fold devices. Its listener
        // decrements mAllAnimationViewNum and resets the animation state only on the final view.
        assertTrue(hook.contains("UserPresentAnimationCompatV12Spring$1"));
        assertTrue(hook.contains("\"onAnimationEnd\""));
        assertTrue(hook.contains("mAllAnimationViewNum"));
        assertTrue(hook.contains("releaseUnlockWhenSpringComplete"));
        assertTrue("HookUtil requires the real Animator parameter for this exact override",
                hook.contains("android.animation.Animator.class"));

        // PREPARE can also resolve to no user-present animation at all. setState(IDLE) must then
        // release the barrier instead of waiting forever for a listener that will never run.
        assertTrue(hook.contains("IDLE"));
        assertTrue(hook.contains("releaseUnlockIfIdleWithoutAnimation"));

        int springHook = hook.indexOf("UserPresentAnimationCompatV12Spring$1");
        int proceed = hook.indexOf("chain.proceed", springHook);
        int terminalCheck = hook.indexOf("releaseUnlockWhenSpringComplete", proceed);
        assertTrue("Spring completion must be evaluated after Xiaomi updates its remaining count",
                springHook >= 0 && proceed > springHook && terminalCheck > proceed);
    }

    @Test public void foldFolmeFallbackOnlyReleasesAfterAllAnimatedViewsComplete()
            throws Exception {
        String hook = read(MAIN.resolve("LauncherGlassHomePresentationHook.java"));

        assertTrue(hook.contains("UserPresentAnimationCompatV12Folme$1"));
        assertTrue(hook.contains("onComplete"));
        assertTrue(hook.contains("onCancel"));
        assertTrue(hook.contains("mNumOfAnimatedView"));
        assertTrue(hook.contains("mNumOfCurrentAnimatedView"));
        assertTrue(hook.contains("releaseUnlockWhenFolmeComplete"));
        assertTrue("Folme callback overrides take one Object parameter",
                hook.contains("Object.class"));
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
