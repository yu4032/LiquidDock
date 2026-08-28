package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Contracts for opt-in widget dark-content adaptation without whole-widget inversion. */
public class WidgetDarkContentAdaptationContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void settingIsOptInAndExposedInConfigAndUi() throws Exception {
        String schema = Files.readString(MAIN.resolve("config/ConfigSchema.java"));
        String config = Files.readString(MAIN.resolve("LiquidDockConfig.java"));
        String compose = Files.readString(Path.of(
                "src/main/kotlin/com/hellovoid/liquiddock/ComposeSettingsActivity.kt"));
        String legacy = Files.readString(Path.of("src/main/res/xml/preferences.xml"));

        assertTrue(schema.contains("WIDGET_DARK_CONTENT"));
        assertTrue(schema.contains("liquid_widget_dark_content"));
        assertTrue(config.contains("widgetDarkContent"));
        assertTrue(compose.contains("ConfigSchema.Glass.WIDGET_DARK_CONTENT"));
        assertTrue(compose.contains("小组件深色内容适配"));
        assertTrue(legacy.contains("android:key=\"liquid_widget_dark_content\""));
    }

    @Test
    public void remoteViewsRequestsProviderNightResourcesBeforeTextFallback() throws Exception {
        Path nightHookPath = MAIN.resolve("LauncherRemoteViewsNightModeHook.java");
        assertTrue("dark-content adaptation must include a scoped RemoteViews night-resource hook",
                Files.exists(nightHookPath));
        if (!Files.exists(nightHookPath)) return;

        String nightHook = Files.readString(nightHookPath);
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));
        String adapter = Files.readString(MAIN.resolve("LauncherWidgetDarkContentAdapter.java"));

        assertTrue(nightHook.contains("Configuration.UI_MODE_NIGHT_MASK"));
        assertTrue(nightHook.contains("Configuration.UI_MODE_NIGHT_YES"));
        assertTrue(nightHook.contains("createConfigurationContext"));
        assertTrue(nightHook.contains("GlassRuntimeState.isWidgetDarkContentEnabled"));
        assertTrue(nightHook.contains("LauncherAppWidgetHostView"));
        assertTrue(nightHook.contains("applyAsync"));
        assertTrue(nightHook.contains("reapplyAsync"));
        assertTrue(nightHook.contains("reapplyCurrent"));
        assertTrue(module.contains("LauncherRemoteViewsNightModeHook.install(classLoader)"));
        assertTrue(adapter.contains("LauncherRemoteViewsNightModeHook.reapplyCurrent(host)"));
        assertTrue(adapter.contains("NATIVE_NIGHT_REQUESTED"));

        // Android's getDarkTextViews() means dark text for a light host background, the opposite
        // of this feature. Provider night resources must be selected through Configuration instead.
        assertFalse(nightHook.contains("getDarkTextViews"));
    }

    @Test
    public void remoteViewsOnlyRecolorsDarkNeutralTextAndRestoresOriginalColors() throws Exception {
        String adapter = Files.readString(MAIN.resolve("LauncherWidgetDarkContentAdapter.java"));

        assertTrue(adapter.contains("TextView"));
        assertTrue(adapter.contains("ColorStateList"));
        assertTrue(adapter.contains("getTextColors()"));
        assertTrue(adapter.contains("setTextColor(Color.WHITE)"));
        assertTrue(adapter.contains("isDarkNeutral"));
        assertTrue(adapter.contains("ORIGINAL_TEXT_COLORS"));
        assertTrue(adapter.contains("release"));

        assertFalse(adapter.contains("ImageView"));
        assertFalse(adapter.contains("ColorMatrixColorFilter"));
        assertFalse(adapter.contains("setAlpha(0"));
        assertFalse(adapter.contains("setVisibility"));
    }

    @Test
    public void mamlUsesNativeDarkVariableInsteadOfWholeWidgetInversion() throws Exception {
        String adapter = Files.readString(MAIN.resolve("LauncherWidgetDarkContentAdapter.java"));

        assertTrue(adapter.contains("__darkmode"));
        assertTrue(adapter.contains("putVariableNumber"));
        assertTrue(adapter.contains("requestUpdate"));
        assertFalse(adapter.contains("setLayerType"));
        assertFalse(adapter.contains("setRenderEffect"));
    }

    @Test
    public void liveToggleAndProviderRefreshReapplyAdaptation() throws Exception {
        String runtime = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(runtime.contains("WIDGET_DARK_CONTENT"));
        assertTrue(runtime.contains("isWidgetDarkContentEnabled"));
        assertTrue(hook.contains("onRuntimeWidgetDarkContentChanged"));
        assertTrue(hook.contains("LauncherWidgetDarkContentAdapter.apply"));
        assertTrue(hook.contains("LauncherWidgetDarkContentAdapter.release"));
        assertTrue(hook.contains("updateAppWidget"));
        assertTrue(hook.contains("installMamlBackgroundOwnershipHooks"));
    }
}
