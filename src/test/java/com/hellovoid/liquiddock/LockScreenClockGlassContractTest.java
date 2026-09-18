package com.hellovoid.liquiddock;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Architecture coverage for the OS3 lockscreen clock replacement and settings surface. */
public class LockScreenClockGlassContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");
    private static final Path KOTLIN = Path.of("src/main/kotlin/com/hellovoid/liquiddock");

    @Test public void settingsLiveUnderThirdPartyAppsWithCustomizationAndSystemUiRestart() throws Exception {
        String pages = Files.readString(KOTLIN.resolve("GboardSettingsPages.kt"));
        String compose = Files.readString(KOTLIN.resolve("ComposeSettingsActivity.kt"));

        assertTrue(pages.contains("锁屏时钟"));
        assertTrue(pages.contains("LockScreenClockGlassPreferences.ENABLED_KEY"));
        assertTrue(pages.contains("恢复继承全局外观"));
        assertTrue(pages.contains("玻璃模糊"));
        assertTrue(compose.contains("Page.LockScreenClock"));
        assertTrue(compose.contains("activity.restartSystemUi()"));
        assertTrue(compose.contains("openLockScreenClock"));
    }

    @Test public void runtimeReusesSystemUiNativeClockMaterialWithoutOverlayRenderer()
            throws Exception {
        String nativeHook = Files.readString(
                MAIN.resolve("LockScreenClockNativeMaterialHook.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String blur = Files.readString(MAIN.resolve("MiBlurBridge.java"));
        String sceneSource = Files.readString(MAIN.resolve("SystemUiKeyguardGoneSource.java"));
        String prefs = Files.readString(MAIN.resolve("LockScreenClockGlassPreferences.java"));

        assertTrue(nativeHook.contains("com.miui.clock.utils.ClockEffectUtils"));
        assertTrue(nativeHook.contains("setClockEffectsContainer"));
        assertTrue(nativeHook.contains("setClockEffectsView"));
        assertTrue(nativeHook.contains("SystemUiKeyguardGoneSource.isLockscreenScene()"));
        assertTrue(nativeHook.contains("!Boolean.TRUE.equals(args[3])"));
        assertTrue(nativeHook.contains("!Boolean.TRUE.equals(args[6])"));
        assertTrue(nativeHook.contains("isTimeMember"));
        assertTrue(nativeHook.contains("isClockContainer"));
        assertTrue(nativeHook.contains("date"));
        assertTrue(nativeHook.contains("weather"));
        assertTrue(nativeHook.contains("notification"));

        assertTrue(module.contains("LockScreenClockNativeMaterialHook.install"));
        assertFalse(module.contains("LockScreenClockGlassHook.install"));
        assertFalse(module.contains("LockScreenClockPassBlurContinuousAuthority.install"));

        assertTrue(sceneSource.contains(
                "LockScreenClockNativeMaterialHook.onLockscreenSceneChanged"));
        assertFalse(sceneSource.contains(
                "LockScreenClockGlassHook.onLockscreenSceneChanged"));

        assertTrue(blur.contains("applyClockMaterialContainer"));
        assertTrue(blur.contains("applyClockMaterialMember"));
        assertTrue(blur.contains("SET_PASS_WINDOW_BLUR_ENABLED.invoke(view, true)"));
        assertTrue(blur.contains("SET_MI_BACKGROUND_BLUR_MODE.invoke(view, 1)"));
        assertTrue(blur.contains("SET_MI_VIEW_BLUR_MODE.invoke(view, 3)"));
        assertTrue(blur.contains("new Point(tint, 101)"));

        // Native material path must not create a second clock renderer.
        assertFalse(nativeHook.contains("TextureView"));
        assertFalse(nativeHook.contains("Surface"));
        assertFalse(nativeHook.contains("EGL"));
        assertFalse(nativeHook.contains("Bitmap"));
        assertFalse(nativeHook.contains("RootPassBlurBackend"));
        assertFalse(nativeHook.contains("LockScreenClockGlyphMaskSource"));
        assertFalse(nativeHook.contains("MiuiSearchboxGlassSession"));

        assertTrue(prefs.contains("glyph_enabled_v2"));
        assertTrue(prefs.contains("reader.b(ENABLED_KEY, ENABLED_DEFAULT)"));
    }

}
