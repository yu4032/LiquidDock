package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Workspace glass is Launcher-only and samples desktop wallpaper without unlock-state gating. */
public class LauncherUnlockCaptureBoundaryContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path SCOPE = Path.of("src/main/resources/META-INF/xposed/scope.list");

    private static String read(String name) throws Exception {
        return Files.readString(MAIN.resolve(name));
    }

    @Test public void xposedScopeContainsLauncherOnly() throws Exception {
        String scope = Files.readString(SCOPE).trim();
        assertTrue(scope.equals("com.miui.home"));
        assertFalse(scope.contains("com.android.systemui"));
    }

    @Test public void moduleDoesNotInstallAnySystemUiSourceOrReceiver() throws Exception {
        String module = read("ModuleMain.java");
        assertFalse(module.contains("SYSTEM_UI_PACKAGE"));
        assertFalse(module.contains("SystemUiKeyguardGoneSource"));
        assertFalse(module.contains("SystemUiKeyguardGoneRuntime"));
    }

    @Test public void systemUiUnlockBridgeFilesAreRemoved() {
        assertFalse(Files.exists(MAIN.resolve("SystemUiKeyguardGoneProtocol.java")));
        assertFalse(Files.exists(MAIN.resolve("SystemUiKeyguardGoneRuntime.java")));
        assertFalse(Files.exists(MAIN.resolve("SystemUiKeyguardGoneSource.java")));
    }

    @Test public void launcherGlassDoesNotGateWallpaperCaptureOnUnlockState() throws Exception {
        String hook = read("LauncherGlassHomePresentationHook.java");
        String bridge = read("Miuix307PassBlurBridge.java");
        String scene = read("LauncherGlassSceneController.java");
        String registry = read("LauncherGlassSessionRegistry.java");

        assertFalse(hook.contains("UnlockAnimationStateMachine"));
        assertFalse(hook.contains("onSystemUiLockscreenGoneFinished"));
        assertFalse(hook.contains("unlockTransitionArmed"));
        assertFalse(hook.contains("setUnlockTransitionPendingForAll"));
        assertFalse(hook.contains("suspendForUnlockCapture"));
        assertFalse(hook.contains("prepareUnlockCaptureReturn"));
        assertFalse(hook.contains("isUnlockCaptureBlocked"));
        assertFalse(bridge.contains("isUnlockCaptureBlocked"));
        assertFalse(scene.contains("unlockTransitionPending"));
        assertFalse(scene.contains("setUnlockTransitionPendingForAll"));
        assertFalse(registry.contains("suspendForUnlockCapture"));
        assertFalse(registry.contains("prepareUnlockCaptureReturn"));
    }
}
