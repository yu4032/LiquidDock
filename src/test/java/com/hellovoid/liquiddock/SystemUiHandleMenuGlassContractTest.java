package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static vendor/API contract for the HyperOS SystemUI app-handle popup glass integration. */
public class SystemUiHandleMenuGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path UI = Path.of(
            "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt");

    private static String read(String file) throws Exception {
        return Files.readString(MAIN.resolve(file));
    }

    @Test
    public void systemUiInstallsOptInHandleMenuHookWithoutReplacingTimingSources() throws Exception {
        String module = read("ModuleMain.java");
        assertTrue(module.contains("SystemUiKeyguardGoneSource.install(classLoader)"));
        assertTrue(module.contains("SystemUiHomeTransitionSource.install(classLoader)"));
        assertTrue(module.contains("runtimeConfig.glass.systemUiHandleMenuEnabled"));
        assertTrue(module.contains("SystemUiHandleMenuGlassHook.install(classLoader, runtimeConfig.glass)"));
    }

    @Test
    public void hookCoversMiuiCaptionMenuAndAospHandleMenuBoundaries() throws Exception {
        String hook = read("SystemUiHandleMenuGlassHook.java");
        assertTrue(hook.contains(
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot"));
        assertTrue(hook.contains("\"addWindow\""));
        assertTrue(hook.contains("\"caption_menu_container\""));
        assertTrue(hook.contains("\"MiuiCaptionContainerView\""));
        assertTrue(hook.contains("LayoutInflater.class"));
        assertTrue(hook.contains("\"desktop_mode_window_decor_handle_menu\""));
        assertTrue(hook.contains("\"windowing_pill\""));
        assertFalse(hook.contains("getDeclaredConstructors()"));
        assertFalse(hook.contains("\"onAssistContentReceived\""));
        assertFalse(hook.contains("\"app_info_pill\""));
        assertFalse(hook.contains("\"more_actions_pill\""));
        assertFalse(hook.contains("\"open_in_app_or_browser_pill\""));
    }

    @Test
    public void glassUsesNativePassWindowBackdropAndNeverBindsPopupRootProducer()
            throws Exception {
        String hook = read("SystemUiHandleMenuGlassHook.java");
        assertTrue(hook.contains("MiBlurBridge.applyPassWindowBlur"));
        assertTrue(hook.contains("MiBlurBridge.clearPassWindowBlur"));
        assertTrue(hook.contains("target.setBackground(null)"));
        assertTrue(hook.contains("Windowless caption menus are not safe RootPassBlur producer roots"));
        assertFalse(hook.contains("new SystemUiHandleMenuGlassSession"));
        assertFalse(hook.contains("requestInitialCapture()"));
        assertTrue(hook.contains("restoreStockBackground()"));
    }

    @Test
    public void backdropProbeIsReadOnlyAndCannotCreateAProducer() throws Exception {
        String hook = read("SystemUiHandleMenuGlassHook.java");
        String probe = read("SystemUiHandleMenuBackdropProbe.java");
        assertTrue(hook.contains("SystemUiHandleMenuBackdropProbe.install(classLoader)"));
        assertTrue(hook.contains("SystemUiHandleMenuBackdropProbe.logMenuSnapshot(root, target)"));
        assertTrue(probe.contains("RootPassBlurEndpointBridge.inspect(view)"));
        assertFalse(probe.contains("PassBlurBindRequest"));
        assertFalse(probe.contains("Miuix307PassBlurBridge.bind"));
        assertFalse(probe.contains("new RootPassBlurBackend"));
        assertFalse(probe.contains("SystemUiHandleMenuGlassSession"));
    }

    @Test
    public void implementationDoesNotIntroduceCpuCaptureOrTimingFallback() throws Exception {
        String hook = read("SystemUiHandleMenuGlassHook.java");
        assertFalse(hook.contains("PixelCopy"));
        assertFalse(hook.contains("Bitmap"));
        assertFalse(hook.contains("postDelayed"));
        assertFalse(hook.contains("Thread.sleep"));
    }

    @Test
    public void guiExposesDedicatedOptInSwitch() throws Exception {
        String ui = Files.readString(UI);
        assertTrue(ui.contains("ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS"));
        assertTrue(ui.contains("应用顶部菜单液态玻璃"));
    }
}
