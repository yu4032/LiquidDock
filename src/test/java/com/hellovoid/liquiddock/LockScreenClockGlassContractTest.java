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

    @Test public void runtimeDirectlyReplacesClockWithoutVendorGlassCapability() throws Exception {
        String hook = Files.readString(MAIN.resolve("LockScreenClockGlassHook.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String session = Files.readString(MAIN.resolve("MiuiSearchboxGlassSession.java"));

        assertTrue(hook.contains("com.miui.clock.MiuiClockController"));
        assertTrue(hook.contains("addClockView"));
        assertTrue(hook.contains("glyphMaskSource.suppressNativeGlyphs()"));
        assertTrue(hook.contains("glyphMaskSource.restoreNativeGlyphs()"));
        assertFalse(hook.contains("supportGlassEffect"));
        assertFalse(hook.contains("isGlassEffectEnable"));
        assertFalse(hook.contains("setMiGlass"));
        assertFalse(hook.contains("clockEffect"));
        assertTrue(module.contains("LockScreenClockGlassHook.install"));
        assertTrue(hook.contains("scheduleAttach(candidate)"));
        assertTrue(hook.contains("post-create inspection failed; native clock retained"));
        assertTrue(hook.contains("tryAttachFailClosed"));
        assertTrue(hook.contains("pre-draw failed; native clock retained"));
        assertTrue(hook.contains("presentation handoff failed; native clock retained"));
        assertTrue(hook.contains("RootPassBlurEndpointBridge.inspect(root)"));
        assertTrue(hook.contains("root endpoint not ready; waiting for next frame"));
        assertTrue(hook.contains("postOnAnimation"));
        assertTrue(session.contains("[DC][LockScreenClockGlass] session failure stage="));
        String mask = Files.readString(MAIN.resolve("LockScreenClockGlyphMaskSource.java"));
        String composite = Files.readString(MAIN.resolve("Miuix307PrismalCompositeShaders.java"));
        assertTrue(mask.contains("com.miui.clock.MiuiTextGlassView"));
        assertTrue(mask.contains("glyph.draw(canvas)"));
        assertTrue(session.contains("GLYPH_MASK_FRAGMENT"));
        assertTrue(session.contains("uploadPendingGlyphMask"));
        assertTrue(composite.contains("uGlyphMask"));
        assertTrue(composite.contains("glass.a * alpha"));

        String prefs = Files.readString(MAIN.resolve("LockScreenClockGlassPreferences.java"));
        assertTrue(prefs.contains("glyph_enabled_v2"));
        assertTrue(prefs.contains("reader.b(ENABLED_KEY, ENABLED_DEFAULT)"));
        assertFalse(prefs.contains("ENABLED_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, \"enabled\")"));
    }
}
