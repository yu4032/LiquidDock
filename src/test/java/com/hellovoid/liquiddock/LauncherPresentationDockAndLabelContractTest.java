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
        assertTrue(hook.contains("UserPresentAnimationCompatV12Folme$1"));
        assertTrue(hook.contains("onComplete"));
        assertTrue(hook.contains("onCancel"));
        assertTrue(hook.contains("resetAnimationViewNum"));
        assertTrue(hook.contains("setHomeTransitionPendingForAll"));
        assertTrue(hook.contains("setUnlockTransitionPendingForAll"));

        assertTrue(controller.contains("homeTransitionPending"));
        assertTrue(controller.contains("unlockTransitionPending"));
        assertTrue(controller.contains("isPresentationPending"));
        assertTrue(controller.contains("session.suspendWorkspaceProducer()"));
        assertTrue(controller.contains("if (isPresentationPending()) return;"));
        assertTrue(module.contains("LauncherGlassHomePresentationHook.install(classLoader)"));
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
}
