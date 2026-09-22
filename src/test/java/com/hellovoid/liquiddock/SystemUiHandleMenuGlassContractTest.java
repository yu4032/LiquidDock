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
    public void hookUsesVerifiedVendorLifecycleAndStablePillIds() throws Exception {
        String hook = read("SystemUiHandleMenuGlassHook.java");
        assertTrue(hook.contains(
                "com.android.wm.shell.windowdecor.DesktopModeWindowDecoration"));
        assertTrue(hook.contains("\"onAssistContentReceived\""));
        assertTrue(hook.contains("AssistContent.class"));
        assertTrue(hook.contains("\"closeHandleMenu\""));
        assertTrue(hook.contains("\"windowing_pill\""));
        assertFalse(hook.contains("\"app_info_pill\""));
        assertFalse(hook.contains("\"more_actions_pill\""));
        assertFalse(hook.contains("\"open_in_app_or_browser_pill\""));
    }

    @Test
    public void glassUsesRootPassBlurAndKeepsStockBackgroundUntilFirstPresentation()
            throws Exception {
        String hook = read("SystemUiHandleMenuGlassHook.java");
        String session = read("SystemUiHandleMenuGlassSession.java");
        String sink = read("SystemUiHandleMenuGlassSinkView.java");
        assertTrue(session.contains("RootPassBlurBackend"));
        assertTrue(session.contains("PassBlurBindRequest.systemUiHandleMenu(sourceRoot)"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(sink.contains("setOpaque(false)"));
        assertTrue(sink.contains("group.addView(sink, 0, new ViewGroup.LayoutParams(0, 0))"));
        assertTrue(hook.contains("onFirstFramePresented"));
        assertTrue(hook.contains("windowing.setBackground(null)"));
        assertTrue(hook.contains("restoreStockBackgrounds()"));
    }

    @Test
    public void implementationDoesNotIntroduceCpuCaptureOrTimingFallback() throws Exception {
        String hook = read("SystemUiHandleMenuGlassHook.java");
        String session = read("SystemUiHandleMenuGlassSession.java");
        String all = hook + session;
        assertFalse(all.contains("PixelCopy"));
        assertFalse(all.contains("Bitmap"));
        assertFalse(all.contains("postDelayed"));
        assertFalse(all.contains("Thread.sleep"));
    }

    @Test
    public void guiExposesDedicatedOptInSwitch() throws Exception {
        String ui = Files.readString(UI);
        assertTrue(ui.contains("ConfigSchema.Glass.SYSTEMUI_HANDLE_MENU_GLASS"));
        assertTrue(ui.contains("应用顶部菜单液态玻璃"));
    }
}
