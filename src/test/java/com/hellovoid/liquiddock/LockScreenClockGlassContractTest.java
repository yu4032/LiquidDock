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

    @Test public void runtimeUsesLiquidDockPrismalMaskGlassInsteadOfVendorClockMaterial()
            throws Exception {
        String hook = Files.readString(MAIN.resolve("LockScreenClockGlassHook.java"));
        String mask = Files.readString(MAIN.resolve("LockScreenClockGlyphMaskSource.java"));
        String session = Files.readString(MAIN.resolve("MiuiSearchboxGlassSession.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String sceneSource = Files.readString(MAIN.resolve("SystemUiKeyguardGoneSource.java"));
        String authority = Files.readString(
                MAIN.resolve("LockScreenClockPassBlurContinuousAuthority.java"));
        String prefs = Files.readString(MAIN.resolve("LockScreenClockGlassPreferences.java"));

        // The runtime must use LiquidDock's own rendering stack, not HyperOS clock materials.
        assertTrue(module.contains("LockScreenClockPassBlurContinuousAuthority.install"));
        assertTrue(module.contains("LockScreenClockGlassHook.install"));
        assertFalse(module.contains("LockScreenClockNativeMaterialHook.install"));
        assertTrue(sceneSource.contains(
                "LockScreenClockGlassHook.onLockscreenSceneChanged"));
        assertFalse(sceneSource.contains(
                "LockScreenClockNativeMaterialHook.onLockscreenSceneChanged"));

        // Clock discovery is semantic and fail-closed inside the real lockscreen scene.
        assertTrue(hook.contains("MiuiClockController#addClockView"));
        assertTrue(hook.contains("SystemUiKeyguardGoneSource.isLockscreenScene()"));
        assertTrue(hook.contains("LockScreenClockGlyphMaskSource.resolve"));
        assertTrue(hook.contains("glyphMaskSource.suppressNativeGlyphs()"));
        assertTrue(hook.contains("session.requestFreshCapture()"));
        assertTrue(hook.indexOf("glyphMaskSource.suppressNativeGlyphs()")
                < hook.indexOf("session.requestFreshCapture()"));
        assertTrue(hook.contains("restoreNativeGlyphs"));

        // The visual result must be canonical Prismal glass constrained by an SDF glyph mask.
        assertTrue(mask.contains("toSignedDistanceBitmap"));
        assertTrue(mask.contains("sdfRangePx"));
        assertTrue(mask.contains("maskToRoot"));
        assertTrue(session.contains("PrismalRenderer"));
        assertTrue(session.contains("prepareBackdrop"));
        assertTrue(session.contains("drawMaskGlass"));
        assertTrue(session.contains("glyphMaskTexture"));
        assertTrue(session.contains("presentFull"));

        // Producer authority is LiquidDock-owned and isolated to the clock PassBlur domain.
        assertTrue(authority.contains("SetPassBlurSurface"));
        assertTrue(authority.contains("setUpdateTextureFlag"));
        assertTrue(session.contains("PassBlurDomain.LOCKSCREEN_CLOCK"));
        assertTrue(session.contains("PassBlurBindRequest.lockScreenClock"));

        assertTrue(prefs.contains("glyph_enabled_v2"));
        assertTrue(prefs.contains("reader.b(ENABLED_KEY, ENABLED_DEFAULT)"));
    }
}
