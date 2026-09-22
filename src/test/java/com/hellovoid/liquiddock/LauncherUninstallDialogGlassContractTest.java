package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Source contract for Launcher-owned uninstall/remove dialog glass. */
public class LauncherUninstallDialogGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test public void hooksOnlySourceVerifiedLauncherUninstallEntryPoints() throws Exception {
        String hook = Files.readString(MAIN.resolve("LauncherUninstallDialogGlassHook.java"));
        assertTrue(hook.contains("com.miui.home.launcher.uninstall.UninstallController"));
        assertTrue(hook.contains("\"showDialog\""));
        assertTrue(hook.contains("\"hideAppWidthDialog\""));
        assertTrue(hook.contains("\"mDeleteDialog\""));
        assertTrue(hook.contains("\"mRemoveDialog\""));
        assertFalse(hook.contains("android.app.AlertDialog"));
        assertFalse(hook.contains("Dialog.class"));
        assertFalse(hook.contains("\"show\", chain"));
    }

    @Test public void dialogUsesLauncherProducerAndExactSemanticContainer() throws Exception {
        String coordinator =
                Files.readString(MAIN.resolve("LauncherDialogGlassCoordinator.java"));
        assertTrue(coordinator.contains(
                "com.miui.home.launcher.uninstall.UninstallDialogViewContainer"));
        assertTrue(coordinator.contains("LauncherGlassSessionRegistry.acquire(authorityAnchor"));
        assertTrue(coordinator.contains("LauncherGlassSinkView.attachToExternalMaterial"));
        assertTrue(coordinator.contains("LauncherGlassSceneController.requestFreshForRoot"));
        assertFalse(coordinator.contains("LauncherGlassSessionRegistry.acquire(material"));
        assertFalse(coordinator.contains("PassBlurBindRequest"));
    }

    @Test public void vendorBackgroundWaitsForRealGlassFrameAndIsRestored() throws Exception {
        String coordinator =
                Files.readString(MAIN.resolve("LauncherDialogGlassCoordinator.java"));
        String sink = Files.readString(MAIN.resolve("LauncherGlassSinkView.java"));
        assertTrue(coordinator.contains("sink.runWhenFirstFramePresented"));
        assertTrue(coordinator.contains("binding.originalBackground = material.getBackground()"));
        assertTrue(coordinator.contains("material.setBackground(null)"));
        assertTrue(coordinator.contains("material.setBackground(binding.originalBackground)"));
        assertTrue(sink.contains("void runWhenFirstFramePresented(Runnable listener)"));
        assertTrue(sink.contains("public void onSurfaceTextureUpdated(SurfaceTexture surface)"));
        assertFalse(coordinator.contains("postDelayed("));
    }

    @Test public void featureHasDedicatedDefaultOnSetting() throws Exception {
        String schema = Files.readString(
                Path.of("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));
        String settings = SourceContractText.read(
                Path.of("src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        assertTrue(schema.contains("UNINSTALL_DIALOG_GLASS = bool("));
        assertTrue(schema.contains("\"liquid_uninstall_dialog_glass\", true, true, true"));
        assertTrue(settings.contains("ConfigSchema.Glass.UNINSTALL_DIALOG_GLASS"));
        assertTrue(settings.contains("桌面卸载弹窗玻璃背景"));
    }
}
