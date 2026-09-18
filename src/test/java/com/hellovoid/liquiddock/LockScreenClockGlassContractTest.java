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
        String composite = Files.readString(MAIN.resolve("Miuix307PrismalCompositeShaders.java"));

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
        assertTrue(hook.contains("classic time glyph source not ready; waiting for next frame"));
        assertTrue(hook.contains("SystemUiKeyguardGoneSource.isLockscreenScene()"));
        assertTrue(hook.contains("native time glyphs suppressed before fresh capture"));
        assertTrue(session.contains("uGlyphRefractionPx"));
        assertTrue(session.contains("uGlyphHighlightStrength"));
        assertTrue(composite.contains("uGlyphTexel"));
        assertTrue(composite.contains("uGlyphRefractionPx"));
        assertTrue(composite.contains("vec2 grad"));
        assertTrue(hook.contains("postOnAnimation"));
        assertTrue(session.contains("[DC][LockScreenClockGlass] session failure stage="));
        String mask = Files.readString(MAIN.resolve("LockScreenClockGlyphMaskSource.java"));
        assertTrue(mask.contains("\"mTimeView\""));
        assertTrue(mask.contains("\"mTimeView2\""));
        assertTrue(mask.contains("\"mHourTextStyle1\""));
        assertTrue(mask.contains("\"mMinuteTextStyle1\""));
        assertTrue(mask.contains("isTimeResourceName"));
        assertTrue(mask.contains("lower.contains(\"date\")"));
        assertTrue(mask.contains("lower.contains(\"weather\")"));
        assertTrue(mask.contains("\"time_view\""));
        assertTrue(mask.contains("\"time_view2\""));
        assertTrue(mask.contains("getResourceEntryName"));
        assertFalse(mask.contains("text.length() > 0"));
        assertTrue(mask.contains("glyph.draw(canvas)"));
        assertTrue(session.contains("GLYPH_MASK_FRAGMENT"));
        assertTrue(session.contains("uploadPendingGlyphMask"));
        assertTrue(composite.contains("uGlyphMask"));
        assertTrue(composite.contains("uBackdrop"));
        assertTrue(composite.contains("uBlurredBackdrop"));
        assertTrue(composite.contains("gl_FragColor = vec4(glass * alpha, alpha)"));
        assertTrue(session.contains("normalizedBackdropTexture()"));
        assertTrue(session.contains("blurredBackdropTexture()"));
        assertTrue(mask.contains("View.INVISIBLE"));
        assertTrue(mask.contains("originalVisibility"));
        assertTrue(mask.contains("findAlphaBounds"));
        assertTrue(mask.contains("insideTimeContainer"));
        assertTrue(hook.contains("suspendForScene"));
        assertTrue(hook.contains("resumeForScene"));
        assertTrue(hook.contains("KNOWN_CLOCKS"));
        String sceneSource = Files.readString(MAIN.resolve("SystemUiKeyguardGoneSource.java"));
        assertTrue(sceneSource.contains("LockScreenClockGlassHook.onLockscreenSceneChanged"));

        String prefs = Files.readString(MAIN.resolve("LockScreenClockGlassPreferences.java"));
        assertTrue(prefs.contains("glyph_enabled_v2"));
        assertTrue(prefs.contains("reader.b(ENABLED_KEY, ENABLED_DEFAULT)"));
        assertFalse(prefs.contains("ENABLED_KEY = ThirdPartyGlassProfiles.key(PROFILE_ID, \"enabled\")"));
    }
}
