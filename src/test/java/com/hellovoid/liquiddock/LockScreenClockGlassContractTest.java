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
        String sceneSource = Files.readString(MAIN.resolve("SystemUiKeyguardGoneSource.java"));
        String prefs = Files.readString(MAIN.resolve("LockScreenClockGlassPreferences.java"));

        assertTrue(nativeHook.contains("com.miui.clock.utils.ClockEffectUtils"));
        assertTrue(nativeHook.contains("com.miui.clock.utils.MiuiBlurUtils"));
        assertTrue(nativeHook.contains("setClockEffectsContainer"));
        assertTrue(nativeHook.contains("setClockEffectsView"));
        assertTrue(nativeHook.contains("SystemUiKeyguardGoneSource.isLockscreenScene()"));
        assertTrue(nativeHook.contains("int aodIndex"));
        assertTrue(nativeHook.contains("!Boolean.TRUE.equals(args[aodIndex])"));
        assertTrue(nativeHook.contains("isTimeMember"));
        assertTrue(nativeHook.contains("chooseBackgroundBlurContainer"));
        assertTrue(nativeHook.contains("MEMBER_CONTAINERS"));
        assertTrue(nativeHook.contains("ROOT_CONTAINERS"));
        assertTrue(nativeHook.contains("native member/container route"));

        // HyperOS native Glass wrappers recovered from the decompiled clock package.
        assertTrue(nativeHook.contains("setGlassBlurContainer"));
        assertTrue(nativeHook.contains("setGlassEffectMethod"));
        assertTrue(nativeHook.contains("setPaintGlassEffect"));
        assertTrue(nativeHook.contains("setMiGlassClip"));
        assertTrue(nativeHook.contains("clearGlassBlurContainer"));
        assertTrue(nativeHook.contains("clearGlassEffectMethod"));

        // Draw hooks are installed lazily from the actual runtime time-view class. This covers
        // MiuiTextGlassView as well as pad clock TextViews without guessing a style class.
        assertTrue(nativeHook.contains("ensureDrawHook(view.getClass())"));
        assertTrue(nativeHook.contains("\"onDraw\""));
        assertTrue(nativeHook.contains("DRAW_HOOKS"));
        assertTrue(nativeHook.contains("paint.getTextPath"));
        assertTrue(nativeHook.contains("canvas.drawPath"));
        assertTrue(nativeHook.contains("bounds.left - 50f"));
        assertTrue(nativeHook.contains("setNativeGlassMember("));

        // Styles that do not publish chooseBackgroundBlurContainer still resolve their owning
        // MiuiBaseClock2 root as the native backdrop container.
        assertTrue(nativeHook.contains("resolveClockRootContainer"));
        assertTrue(nativeHook.contains("com.miui.clock.MiuiBaseClock2"));

        // Scene cleanup must remove only LiquidDock's forced Glass state.
        assertTrue(nativeHook.contains("clearNativeGlassMember"));
        assertTrue(nativeHook.contains("clearNativeGlassContainer"));

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

        assertFalse(nativeHook.contains("MiBlurBridge.applyClockMaterialContainer"));
        assertFalse(nativeHook.contains("MiBlurBridge.applyClockMaterialMember"));

        // Native material path must not create a second clock renderer.
        assertFalse(nativeHook.contains("import android.view.TextureView"));
        assertFalse(nativeHook.contains("new TextureView("));
        assertFalse(nativeHook.contains("import android.view.Surface"));
        assertFalse(nativeHook.contains("EGL14"));
        assertFalse(nativeHook.contains("Bitmap.createBitmap"));
        assertFalse(nativeHook.contains("RootPassBlurBackend"));
        assertFalse(nativeHook.contains("LockScreenClockGlyphMaskSource"));
        assertFalse(nativeHook.contains("MiuiSearchboxGlassSession"));

        assertTrue(prefs.contains("glyph_enabled_v2"));
        assertTrue(prefs.contains("reader.b(ENABLED_KEY, ENABLED_DEFAULT)"));
    }

}
