package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** The Settings pilot is strictly opt-in and never owns Settings persistence. */
public class SystemSettingsScopeContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void settingsScopeIsOptInAndTargeted() throws Exception {
        String scope = read("resources/META-INF/xposed/scope.list");
        String module = read("java/com/hellovoid/liquiddock/ModuleMain.java");
        String schema = read("java/com/hellovoid/liquiddock/config/ConfigSchema.java");
        String registry = read("java/com/hellovoid/liquiddock/ThirdPartyGlassAdapterRegistry.java");

        assertTrue(scope.lines().anyMatch("com.android.settings"::equals));
        int settingsGate = module.indexOf("if (SETTINGS_PACKAGE.equals(packageName)) {");
        int systemUiGate = module.indexOf("if (SYSTEM_UI_PACKAGE.equals(packageName))");
        assertTrue(settingsGate > 0 && settingsGate < systemUiGate);
        assertTrue(module.contains("ConfigSchema.SystemSettings.PRISMAL_SWITCH"));
        assertTrue(module.contains("SETTINGS_PACKAGE.equals(loadedProcessName)"));
        assertTrue(module.contains("\"OS3.0.310.0.WAOCNXM\".equals(incremental)"));
        assertTrue(module.contains("remoteKeyPresent="));
        assertTrue(module.contains("config master="));
        assertTrue(module.contains("SystemSettingsPrismalSwitchHook.install(param.getClassLoader())"));
        assertTrue(schema.contains("\"system_settings_prismal_switch\", false, false, false"));
        assertTrue(schema.contains("add(keys, SystemSettings.PRISMAL_SWITCH)"));
        assertFalse(registry.contains("\"com.android.settings\""));
    }

    @Test
    public void switchExperimentRetainsNativeOwnershipAndHasAStockFallback() throws Exception {
        String hook = read("java/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchHook.java");
        String overlay = read("kotlin/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchOverlay.kt");
        String ui = read("kotlin/com/hellovoid/liquiddock/SystemSettingsPage.kt");
        String nav = read("kotlin/com/hellovoid/liquiddock/SettingsNavigationModel.kt");

        assertTrue(hook.contains("miuix.slidingwidget.widget.SlidingButton"));
        assertTrue(hook.contains("TARGET_SWITCH = \"miuix.slidingwidget.widget.SlidingSwitch\""));
        assertTrue(hook.contains("TARGET_SWITCH_COMPAT = \"miuix.slidingwidget.widget.SlidingSwitchCompat\""));
        assertTrue(hook.contains("declared(target, \"onDraw\", Canvas.class)"));
        assertTrue(hook.contains("declared(target, \"onMeasure\", int.class, int.class)"));
        assertFalse(hook.contains("CompoundButton.class.getDeclaredMethod"));
        assertTrue(overlay.contains("original.id != android.R.id.checkbox"));
        assertTrue(overlay.contains("original.id != android.R.id.switch_widget"));
        assertTrue(overlay.contains("original.rootView.findViewById<View>(android.R.id.content)"));
        assertTrue(overlay.contains("performNativePreferenceClick()"));
        assertTrue(overlay.contains("owner.performClick()"));
        assertTrue(overlay.contains("stock.performClick()"));
        assertTrue(overlay.contains("stock.alpha = stockAlpha"));
        assertTrue(overlay.contains("content.removeView(view)"));
        assertTrue(overlay.contains("content.addView("));
        assertTrue(overlay.contains("stock.getLocationOnScreen(stockPosition)"));
        assertTrue(overlay.contains("stock.getGlobalVisibleRect(visibleRect)"));
        assertFalse(overlay.contains("content.removeView(stock)"));
        assertTrue(overlay.contains("GuiOnTouchPrismalToggle("));
        assertFalse(overlay.contains("setOnCheckedChangeListener"));
        assertFalse(overlay.contains("PixelCopy"));
        assertFalse(overlay.contains("ScreenCapture"));
        assertTrue(ui.contains("ConfigSchema.SystemSettings.PRISMAL_SWITCH"));
        assertTrue(nav.contains("Page.SystemSettings"));
    }

    private static String read(String file) throws Exception {
        return Files.readString(MAIN.resolve(file));
    }
}
