package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * Contracts derived from HyperOS Launcher 4.50 native material ownership.
 *
 * <p>Launcher itself removes FolderIcon's icon_icon drawable alpha and AppWidget's
 * android.R.id.widget_frame background when its native blur material is active. LiquidDock must
 * take over those same background owners without hiding preview/content views.</p>
 */
public class LauncherGlassVendorMaterialSuppressionContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/hellovoid/liquiddock");

    @Test
    public void standardWidgetGlassOwnsOnlyAndroidWidgetFrameBackground() throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(helper.contains("android.R.id.widget_frame"));
        assertTrue(helper.contains("findViewById"));
        assertTrue(helper.contains("setBackground(null)"));
        assertTrue(helper.contains("ORIGINAL_WIDGET_BACKGROUNDS"));
        assertTrue(helper.contains("releaseWidget"));
        // RemoteViews may recreate widget_frame, so reclaim after every provider update.
        assertTrue(hook.contains("updateAppWidget"));
        assertTrue(hook.contains("LauncherGlassVendorMaterialSuppressor.claimWidget"));

        // Never hide the whole host or provider content just to remove its fallback plate.
        assertFalse(helper.contains("setAlpha(0"));
        assertFalse(helper.contains("removeAllViews"));
        assertFalse(helper.contains("setVisibility(View.INVISIBLE"));
        assertFalse(helper.contains("setVisibility(View.GONE"));
    }

    @Test
    public void runtimeGlassDisableReleasesClaimedWidgetBackgrounds() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));
        String disabled = methodSlice(hook, "static void onRuntimeGlassDisabled()", "static boolean install(");

        assertTrue(disabled.contains("LauncherGlassVendorMaterialSuppressor.releaseWidget"));
    }

    @Test
    public void mamlContentIsToldLiquidDockProvidesBackgroundMaterial() throws Exception {
        String helper = Files.readString(MAIN.resolve("LauncherGlassVendorMaterialSuppressor.java"));

        assertTrue(helper.contains("putVariableNumber"));
        assertTrue(helper.contains("enable_background_blur"));
        assertTrue(helper.contains("1.0d"));
    }

    @Test
    public void folderHookRecognizesActualLauncher450SubclassesWithoutNativeBlur() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon1x1"));
        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon2x2"));
        // setIconImageView is invoked even when Launcher native folder blur is disabled. Use the
        // abstract FolderIcon type so 1x1, 2x2_4, 2x2_9 and future subclasses all hit this path.
        assertTrue(folder.contains("folderIconType.isInstance(icon)"));
        assertTrue(folder.contains("setIconImageView"));
    }

    @Test
    public void folderBackgroundSuppressionFollowsEachSpecificGlassStyle() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        String attach = methodSlice(folder,
                "private static LauncherGlassStaticNode attachMaterial(",
                "private static LauncherGlassStaticNode claimedSink(");

        int styleResolution = attach.indexOf("smallFolderStyle");
        int enabledGuard = attach.indexOf("if (!style.enabled)");
        int restore = attach.indexOf("restoreMaterial(material)");
        int suppress = attach.indexOf("makeMaterialTransparent(material)");

        assertTrue(styleResolution >= 0);
        assertTrue("disabled small/large style must be checked before suppressing native plate",
                enabledGuard > styleResolution);
        assertTrue("disabled style must restore any prior native plate claim", restore > enabledGuard);
        assertTrue("native background can only be hidden after the per-style enabled guard",
                suppress > restore);

        // The preview/icon contents remain visible; only icon_icon's drawable alpha is suppressed.
        assertFalse(folder.contains("mPreviewContainer.setAlpha(0"));
        assertFalse(folder.contains("mPreviewContainer.setVisibility"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, Math.max(0, start));
        assertTrue("missing start marker: " + startMarker, start >= 0);
        assertTrue("missing end marker: " + endMarker, end > start);
        return source.substring(start, end);
    }
}
