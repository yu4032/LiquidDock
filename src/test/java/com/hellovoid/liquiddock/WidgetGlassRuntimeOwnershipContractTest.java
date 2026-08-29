package com.hellovoid.liquiddock;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for live widget-glass ownership after the preference is toggled off. */
public class WidgetGlassRuntimeOwnershipContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void widgetGlassPreferenceIsTrackedAsLiveRuntimeState() throws Exception {
        String state = Files.readString(MAIN.resolve("GlassRuntimeState.java"));
        String module = Files.readString(MAIN.resolve("ModuleMain.java"));

        assertTrue(state.contains("ConfigSchema.Glass.WIDGET_GLASS.name()"));
        assertTrue(state.contains("static boolean isWidgetEnabled()"));
        assertTrue(state.contains("onRuntimeWidgetGlassDisabled()"));
        assertTrue(module.contains("runtimeConfig.glass.widgetEnabled"));
    }

    @Test
    public void staleInstalledHooksCannotReclaimWidgetBackgroundWhenLiveStateIsOff() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(hook.contains("GlassRuntimeState.isWidgetEnabled()"));
        assertTrue(hook.contains("static void onRuntimeWidgetGlassDisabled()"));
        assertTrue(hook.contains("LauncherWidgetBackgroundController.release(host)"));
        assertTrue(hook.contains("kind == LauncherGlassDragState.Kind.WIDGET")
                && hook.contains("!GlassRuntimeState.isWidgetEnabled()"));
    }
}
