package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * System Settings: native Preference owns state, genuine Prismal owns pixels
 * and gestures, and the native scene is supplied by GPU RenderNode recording.
 */
public class SystemSettingsScopeContractTest {
    private static final Path MAIN = Path.of("src/main");

    @Test
    public void settingsScopeIsOptInAndTargeted() throws Exception {
        String scope = read("resources/META-INF/xposed/scope.list");
        String module = read("java/com/hellovoid/liquiddock/ModuleMain.java");
        String schema = read("java/com/hellovoid/liquiddock/config/ConfigSchema.java");

        assertTrue(scope.lines().anyMatch("com.android.settings"::equals));
        assertTrue(module.contains("SETTINGS_PACKAGE.equals(loadedProcessName)"));
        assertTrue(module.contains("\"OS3.0.310.0.WAOCNXM\".equals(incremental)"));
        assertTrue(module.contains("ConfigSchema.SystemSettings.PRISMAL_SWITCH"));
        assertTrue(module.contains("SystemSettingsPrismalSwitchHook.install(loader)"));
        assertTrue(schema.contains("\"system_settings_prismal_switch\", false, false, false"));
        assertTrue(schema.contains("add(keys, SystemSettings.PRISMAL_SWITCH)"));
    }

    @Test
    public void realPrismalWidgetWithNativeSceneSamplingAndSafeFallback() throws Exception {
        String hook = read("java/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchHook.java");
        String host = read("kotlin/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchHost.kt");
        String backdrop = read("kotlin/com/hellovoid/liquiddock/SystemSettingsNativeBackdrop.kt");
        String boot = read("java/com/hellovoid/liquiddock/SystemSettingsPrismalBootstrap.java");
        String bridge = read("kotlin/com/hellovoid/liquiddock/SystemSettingsComposeLifecycleBridge.kt");
        String rules = Files.readString(Path.of("proguard-rules.pro"));
        String gradle = Files.readString(Path.of("build.gradle.kts"));

        assertTrue(hook.contains("miuix.slidingwidget.widget.SlidingButton"));
        assertTrue(hook.contains("miuix.slidingwidget.widget.SlidingSwitch"));
        assertTrue(hook.contains("miuix.slidingwidget.widget.SlidingSwitchCompat"));
        assertTrue(hook.contains("declared(cls, \"onDraw\", Canvas.class)"));
        assertTrue(hook.contains("return result"));
        assertTrue(hook.contains("SystemSettingsPrismalSwitchHost.attach(view)"));
        assertFalse(hook.contains("SystemSettingsNativeSwitchPainter"));

        assertTrue(host.contains("GuiOnTouchPrismalToggle("));
        assertTrue(host.contains("backdrop = scene"));
        assertTrue(host.contains("SystemSettingsPrismalBootstrap.ensure(stock.context)"));
        assertTrue(host.contains("SystemSettingsNativeBackdrop(content)"));
        assertTrue(host.contains("stock.alpha = 0f"));
        assertTrue(host.contains("stock.alpha = stockAlpha"));
        assertTrue(host.contains("owner.performClick()"));
        assertTrue(host.contains("stock.performClick()"));
        assertTrue(host.contains("importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS"));
        assertTrue(host.contains("if (!scene.capturing) super.dispatchDraw(canvas)"));
        assertTrue(host.contains("content.removeView(view)"));

        assertTrue(backdrop.contains("RenderNode("));
        assertTrue(backdrop.contains("node.beginRecording(width, height)"));
        assertTrue(backdrop.contains("root.draw(canvas)"));
        assertTrue(backdrop.contains("canvas.clipRect(clip)"));
        assertTrue(backdrop.contains("drawRenderNode(node)"));
        assertTrue(backdrop.contains("coords.positionInWindow()"));
        assertTrue(backdrop.contains("override fun readSamplingState()"));
        assertFalse(backdrop.contains("Bitmap.createBitmap"));
        assertFalse(backdrop.contains("PixelCopy.request"));
        assertFalse(backdrop.contains("ScreenCapture.capture"));
        assertFalse(backdrop.contains("readPixels("));

        assertTrue(boot.contains("createPackageContext("));
        assertTrue(boot.contains("assets.open(\"agsl/rect_refraction.agsl\")"));
        assertTrue(boot.contains("PrismalAgslAssets"));
        assertTrue(boot.contains("bind.invoke("));
        assertTrue(rules.contains("com.styropyr0.prismal.internal.PrismalAgslAssets"));
        assertTrue(gradle.contains("proguardFiles(\"proguard-rules.pro\")"));
        assertTrue(bridge.contains("Application.ActivityLifecycleCallbacks"));

        assertFalse(Files.exists(MAIN.resolve(
                "java/com/hellovoid/liquiddock/SystemSettingsNativeSwitchPainter.java")));
        assertFalse(Files.exists(MAIN.resolve(
                "java/com/hellovoid/liquiddock/SystemSettingsPrismalSwitchMotion.java")));
    }

    private static String read(String file) throws Exception {
        return Files.readString(MAIN.resolve(file));
    }
}
