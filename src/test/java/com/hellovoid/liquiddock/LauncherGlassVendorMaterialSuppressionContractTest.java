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
        assertTrue(hook.contains("updateAppWidget"));
        assertTrue(hook.contains("LauncherGlassVendorMaterialSuppressor.claimWidget"));

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
    public void mamlAsyncRootAndColorRefreshReassertBackgroundOwnershipWithoutDrag() throws Exception {
        String hook = Files.readString(MAIN.resolve("MiuixLauncherStaticGlassHook.java"));

        assertTrue(hook.contains("installMamlBackgroundOwnershipHooks"));
        assertTrue(hook.contains("\"onResume\""));
        assertTrue(hook.contains("\"updateColor\""));
        assertTrue(hook.contains("scheduleBind((View) owner, LauncherGlassDragState.Kind.WIDGET"));
    }

    @Test
    public void folderHookRecognizesActualLauncher450SubclassesWithoutNativeBlur() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon1x1"));
        assertTrue(folder.contains("com.miui.home.launcher.folder.FolderIcon2x2"));
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
        int enabledGuard = attach.indexOf("if (!isFolderLiveEnabled(material))");
        int release = attach.indexOf("releaseMaterialOwnership(material)");
        int suppress = attach.indexOf("makeMaterialTransparent(material)");

        assertTrue(styleResolution >= 0);
        assertTrue("live small/large state must be checked before suppressing native plate",
                enabledGuard > styleResolution);
        assertTrue("disabled live style must release any prior native plate claim",
                release > enabledGuard);
        assertTrue("native background can only be hidden after the live per-style enabled guard",
                suppress > release);

        assertFalse(folder.contains("mPreviewContainer.setAlpha(0"));
        assertFalse(folder.contains("mPreviewContainer.setVisibility"));
    }

    @Test
    public void largeFolderGlassAlsoSuppressesDedicatedCoverAbovePreview() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        assertTrue(folder.contains("CLAIMED_FOLDER_COVERS"));
        assertTrue(folder.contains("syncLargeFolderCover"));
        assertTrue(folder.contains("getCover"));
        assertTrue(folder.contains("makeMaterialTransparent(cover)"));
        assertTrue(folder.contains("restoreMaterial(cover)"));
    }

    @Test
    public void largeFolderCoverIsHiddenOnlyAfterGlassSinkExists() throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));
        String attach = methodSlice(folder,
                "private static void attachFromFolderIcon(",
                "private static void scheduleFolderRecovery(");
        int sink = attach.indexOf("LauncherGlassStaticNode sink = attachMaterial(value, glassConfig)");
        int sync = attach.indexOf("syncLargeFolderCover(icon, glassConfig)");
        assertTrue("cover must not disappear before the static glass node exists", sink >= 0 && sync > sink);
        assertTrue("failed/disabled glass ownership must restore the cover",
                attach.indexOf("releaseFolderCover(icon)", sink) > sink);

        String recovery = methodSlice(folder,
                "private static void scheduleFolderRecovery(",
                "private static void observeFolderIconAttach(");
        int recoverySink = recovery.indexOf("sink = attachMaterial(material, glassConfig)");
        int recoverySync = recovery.indexOf("syncLargeFolderCover(current, glassConfig)");
        assertTrue("startup recovery may hide cover only after it acquires a sink",
                recoverySink >= 0 && recoverySync > recoverySink);
    }

    @Test
    public void largeFolderBackgroundUsesInternalPaintAlphaBecauseDrawableSetAlphaIsNoOp()
            throws Exception {
        String folder = Files.readString(MAIN.resolve("MiuixFolderGlassHook.java"));

        // Launcher 4.50 FolderIcon4x4NormalBackgroundDrawable overrides Drawable.setAlpha(int)
        // with an empty body. ImageView.setImageAlpha(0) therefore cannot suppress this plate.
        // Launcher itself reaches into getPaint().setAlpha(0) when native folder blur is active.
        assertTrue(folder.contains("suppressLargeFolderDrawablePaint"));
        assertTrue(folder.contains("FolderIcon4x4NormalBackgroundDrawable"));
        assertTrue(folder.contains("FolderIcon4x4DefaultBackgroundDrawable"));
        assertTrue(folder.contains("HookUtil.invoke(drawable, \"getPaint\")"));
        assertTrue(folder.contains("paint.setAlpha(0)"));
        assertTrue(folder.contains("ORIGINAL_LARGE_FOLDER_PAINT_ALPHA"));
        assertTrue(folder.contains("restoreLargeFolderDrawablePaint"));
    }

    private static String methodSlice(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        if (start < 0 || end <= start) return "";
        return source.substring(start, end);
    }
}
