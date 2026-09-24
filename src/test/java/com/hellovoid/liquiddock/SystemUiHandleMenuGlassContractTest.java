package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Static ownership contract for the HyperOS SystemUI caption Handle Menu integration. */
public class SystemUiHandleMenuGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void hookUsesRealMiuiCaptionWindowBoundaryAndNativeBackdrop() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        assertTrue(hook.contains(
                "com.android.wm.shell.multitasking.miuimultiwinswitch.miuiwindowdecor.decoration.MiuiDecorationDot"));
        assertTrue(hook.contains("\"addWindow\""));
        assertTrue(hook.contains("\"caption_menu_container\""));
        assertTrue(hook.contains("\"desktop_mode_window_decor_handle_menu\""));
        assertTrue(hook.contains("\"windowing_pill\""));
        assertTrue(hook.contains("MiBlurBridge.applyPassWindowBlur(target, nativeBlurRadiusPx)"));
        assertTrue(hook.contains("target.setBackground(null)"));
        assertTrue(hook.contains("restoreStockBackground()"));

        assertFalse(hook.contains("PassBlurBindRequest.systemUiHandleMenu"));
        assertFalse(hook.contains("new RootPassBlurBackend"));
        assertFalse(hook.contains("new SystemUiHandleMenuGlassSession"));
        assertFalse(hook.contains("PixelCopy"));
        assertFalse(hook.contains("Bitmap"));
        assertFalse(hook.contains("postDelayed"));
    }

    @Test
    public void animationProbeIsStrictlyReadOnlyAndOwnedByBindingLifetime() throws Exception {
        String hook = Files.readString(MAIN.resolve("SystemUiHandleMenuGlassHook.java"));
        String probe = Files.readString(MAIN.resolve("SystemUiHandleMenuAnimationProbe.java"));

        assertTrue(hook.contains("animationProbe.start()"));
        assertTrue(hook.contains("animationProbe.stop()"));
        assertTrue(probe.contains("OnPreDrawListener"));
        assertTrue(probe.contains("getScaleX()"));
        assertTrue(probe.contains("getScaleY()"));
        assertTrue(probe.contains("getTranslationX()"));
        assertTrue(probe.contains("getTranslationY()"));
        assertTrue(probe.contains("getPivotX()"));
        assertTrue(probe.contains("getPivotY()"));
        assertTrue(probe.contains("getGlobalVisibleRect"));
        assertTrue(probe.contains("RootPassBlurEndpointBridge.inspect(sourceRoot)"));
        assertTrue(probe.contains("[DC][SystemUiHandleMenuAnim]"));

        assertFalse(probe.contains("setScaleX("));
        assertFalse(probe.contains("setScaleY("));
        assertFalse(probe.contains("setTranslationX("));
        assertFalse(probe.contains("setTranslationY("));
        assertFalse(probe.contains("setAlpha("));
        assertFalse(probe.contains("SurfaceControl.Transaction"));
        assertFalse(probe.contains("PassBlurBindRequest"));
        assertFalse(probe.contains("MiBlurBridge."));
        assertFalse(probe.contains("postDelayed"));
    }

    @Test
    public void moduleAndSchemaKeepFeatureOptIn() throws Exception {
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String config = Files.readString(MAIN.resolve("LiquidDockConfig.java"));
        String schema = Files.readString(
                Path.of("src/main/java/com/hellovoid/liquiddock/config/ConfigSchema.java"));

        assertTrue(module.contains("runtimeConfig.glass.systemUiHandleMenuEnabled"));
        assertTrue(module.contains("SystemUiHandleMenuGlassHook.install(classLoader, runtimeConfig.glass)"));
        assertTrue(config.contains("systemUiHandleMenuEnabled"));
        assertTrue(schema.contains("SYSTEMUI_HANDLE_MENU_GLASS = bool("));
        assertTrue(schema.contains("\"liquid_systemui_handle_menu_glass\", false, false, false"));
    }
}
