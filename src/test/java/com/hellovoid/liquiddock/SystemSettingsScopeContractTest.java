package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

/** Opt-in Settings switch must not depend on Prismal App Startup or Jetpack Compose. */
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
        assertTrue(module.contains("SystemSettingsPrismalSwitchHook.install(loader)"));
        assertTrue(schema.contains("\"system_settings_prismal_switch\", false, false, false"));
        assertTrue(schema.contains("add(keys, SystemSettings.PRISMAL_SWITCH)"));
        assertFalse(registry.contains("\"com.android.settings\""));
    }

    @Test
    public void rendererHooksOnlyNativeOnDrawAndRetainsStockBusinessAuthority() throws Exception {
        String hook = read("java/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchHook.java");
        String painter = read("java/com/hellovoid/liquiddock/SystemSettingsNativeSwitchPainter.java");
        String motion = read("java/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchMotion.java");
        String reference = read("kotlin/com/hellovoid/liquiddock/GuiOnTouchPrismalToggle.kt");
        String ui = read("kotlin/com/hellovoid/liquiddock/SystemSettingsPage.kt");
        String nav = read("kotlin/com/hellovoid/liquiddock/SettingsNavigationModel.kt");

        assertTrue(hook.contains("miuix.slidingwidget.widget.SlidingButton"));
        assertTrue(hook.contains("miuix.slidingwidget.widget.SlidingSwitch"));
        assertTrue(hook.contains("miuix.slidingwidget.widget.SlidingSwitchCompat"));
        assertTrue(hook.contains("declared(target, \"onDraw\", Canvas.class)"));
        assertTrue(hook.contains("SystemSettingsNativeSwitchPainter.paint("));
        assertTrue(hook.contains("return chain.proceed(args)"));
        assertFalse(hook.contains("CompoundButton.class.getDeclaredMethod"));
        assertFalse(hook.contains("SystemSettingsPrismalSwitchOverlay"));
        assertFalse(hook.contains("setChecked("));
        assertFalse(hook.contains("onTouchEvent("));

        assertTrue(painter.contains("android.graphics.RuntimeShader"));
        assertTrue(painter.contains("canvas.drawRect("));
        assertTrue(painter.contains("canvas.drawRoundRect("));
        assertTrue(painter.contains("drawCanvasFallback("));
        // Settings chrome must reproduce the source Prismal proportions,
        // not the previous opaque circular slider approximation.
        assertTrue(reference.contains(".size(64.dp, 28.dp)"));
        assertTrue(reference.contains(".size(40.dp, 24.dp)"));
        assertTrue(reference.contains("pressedScale = 1.5f"));
        assertTrue(reference.contains("20.dp.toPx()"));
        assertTrue(reference.contains("0xFF34C759"));
        assertTrue(reference.contains("0xFF30D158"));
        assertTrue(painter.contains("track=64x28dp thumb=40x24dp"));
        assertTrue(painter.contains("20.0, 12.0"));
        assertTrue(painter.contains("1.0 + 0.5 * pressed"));
        assertTrue(painter.contains("uVelocity"));
        assertTrue(painter.contains("0.20, 0.36"));
        assertTrue(motion.contains("Choreographer.FrameCallback"));
        assertTrue(motion.contains("pressTarget = 1f"));
        assertTrue(motion.contains("986.96f"));
        assertTrue(motion.contains("density * 20f"));
        assertTrue(motion.contains("targetChanged(button.isChecked())"));
        assertFalse(motion.contains("button.setChecked("));
        assertFalse(motion.contains("button.performClick("));
        assertTrue(hook.contains("SystemSettingsPrismalSwitchMotion.observeNativeTouch("));
        assertTrue(hook.contains("SystemSettingsPrismalSwitchMotion.observeNativeChecked("));
        assertTrue(hook.contains("Object originalResult = chain.proceed(touchArgs)"));
        assertTrue(hook.contains("Object originalResult = chain.proceed(checkedArgs)"));

        assertTrue(painter.contains("return false"));
        assertFalse(painter.contains("import androidx.compose."));
        assertFalse(painter.contains("new ComposeView("));
        assertFalse(painter.contains("import android.view.PixelCopy"));
        assertFalse(painter.contains("PixelCopy.request("));
        assertFalse(painter.contains("ScreenCapture.capture("));
        assertFalse(painter.contains("Bitmap.createBitmap"));
        assertFalse(painter.contains("setOnCheckedChangeListener"));
        assertTrue(ui.contains("ConfigSchema.SystemSettings.PRISMAL_SWITCH"));
        assertTrue(nav.contains("Page.SystemSettings"));
        assertFalse(Files.exists(MAIN.resolve(
                "kotlin/com/hellovoid/liquiddock/SystemSettingsComposeLifecycleBridge.kt")));
        assertFalse(Files.exists(MAIN.resolve(
                "kotlin/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchOverlay.kt")));
    }

    private static String read(String file) throws Exception {
        return Files.readString(MAIN.resolve(file));
    }
}
